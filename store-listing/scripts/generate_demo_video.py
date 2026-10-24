#!/usr/bin/env python3
"""Renders the Play Console demo video for SMS Toolbox: a full walkthrough of
the app that calls out every permission it asks for, at the moment it asks
and why.

It covers, in the order a new user hits them:

  1. Launch on the Backup tab: no permission prompt at launch
  2. Gmail app password in Settings (INTERNET, no prompt)
  3. Enable backup: SMS rationale, then the system READ_SMS / RECEIVE_SMS /
     SEND_SMS prompt, then POST_NOTIFICATIONS
  4. Existing SMS upload, then a new incoming SMS that ends up in Gmail
  5. Redirect: contact picker (READ_CONTACTS rationale + prompt), save the
     rule, battery-optimization exemption, an SMS forwarded via SEND_SMS
  6. Schedule: a text sent later via SEND_SMS
  7. A summary card listing every permission and what it's for

Like generate_assets.py this is a *concept* render built from the app's real
brand colors and UI strings (app/src/main/res/values/strings.xml) and the
permission flow in MainActivity.kt, not a screen recording. No emulator was
available where it was made. The permission order and dialog wording match
the code. Google's Permissions declaration form asks for a video of the app
on a device, so treat this as the storyboard to record against (see
store-listing/video/README.md) or as a fallback if no device is to hand.

Usage:
    pip install pillow      # plus ffmpeg with libx264 on PATH
    python3 generate_demo_video.py

Writes store-listing/video/sms-toolbox-demo.mp4 (1080x1920, 30 fps, H.264)
and a contact sheet, store-listing/video/storyboard.png.
"""

from __future__ import annotations

import math
import shutil
import subprocess
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Callable

from PIL import Image, ImageDraw, ImageFilter

sys.path.insert(0, str(Path(__file__).resolve().parent))
import generate_assets as ga  # noqa: E402  (reuses the palette, fonts and phone-frame helpers)

ROOT = Path(__file__).resolve().parent.parent
VIDEO_DIR = ROOT / "video"
OUTPUT = VIDEO_DIR / "sms-toolbox-demo.mp4"
STORYBOARD = VIDEO_DIR / "storyboard.png"

W, H = ga.CANVAS_W, ga.CANVAS_H
FPS = 30
FADE_S = 0.35

AMBER = (0xFB, 0xBC, 0x05)
SCRIM = (0, 0, 0)
SYSTEM_DIALOG_BG = (0xEE, 0xF0, 0xF8)
RED = (0xEA, 0x43, 0x35)

Box = tuple[int, int, int, int]
Point = tuple[float, float]


# ---------------------------------------------------------------------------
# Canvas: caption band (badge + two lines) over the phone frame
# ---------------------------------------------------------------------------
def canvas(caption: list[str], badge: str | None = None) -> tuple[Image.Image, ImageDraw.ImageDraw, Box]:
    img, draw, screen_box = ga.new_screenshot_canvas([])
    caption_font = ga.bold(50)
    line_h = 62
    badge_h = 58 if badge else 0
    total = badge_h + (18 if badge else 0) + len(caption) * line_h
    y = (ga.CAPTION_H - total) / 2 + 6
    if badge:
        bf = ga.bold(28)
        bw = ga.text_width(draw, badge, bf) + 56
        bx0 = (W - bw) / 2
        draw.rounded_rectangle((bx0, y, bx0 + bw, y + badge_h), radius=badge_h / 2, fill=AMBER)
        ga.draw_centered_text(draw, W / 2, y + 13, badge, bf, ga.BRAND_ON_SURFACE)
        y += badge_h + 18
    for line in caption:
        ga.draw_centered_text(draw, W / 2, y, line, caption_font, ga.WHITE)
        y += line_h
    return img, draw, screen_box


def wrapped(draw: ImageDraw.ImageDraw, xy: Point, text: str, font, fill, max_w: float, line_h: int) -> float:
    x, y = xy
    for line in ga.wrap_text(draw, text, font, max_w):
        draw.text((x, y), line, font=font, fill=fill)
        y += line_h
    return y


def button(draw: ImageDraw.ImageDraw, box: Box, label: str, filled: bool = True, color=ga.BRAND_PRIMARY) -> Point:
    if filled:
        draw.rounded_rectangle(box, radius=(box[3] - box[1]) / 2, fill=color)
    else:
        draw.rounded_rectangle(box, radius=(box[3] - box[1]) / 2, outline=color, width=3)
    f = ga.bold(30)
    ga.draw_centered_text(draw, (box[0] + box[2]) / 2, (box[1] + box[3]) / 2 - 18, label, f, ga.WHITE if filled else color)
    return ((box[0] + box[2]) / 2, (box[1] + box[3]) / 2)


def contact_icon(draw: ImageDraw.ImageDraw, cx: float, cy: float) -> None:
    draw.ellipse((cx - 9, cy - 16, cx + 9, cy + 2), outline=ga.BRAND_PRIMARY, width=3)
    draw.arc((cx - 17, cy + 2, cx + 17, cy + 30), 180, 360, fill=ga.BRAND_PRIMARY, width=3)


def field_box(draw, x0, x1, y, label, value, icon=False, placeholder=False) -> tuple[int, Point | None]:
    """Like ga.draw_form_field but also returns where the contact icon is so
    the tap indicator can land on it."""
    lf = ga.regular(24)
    draw.text((x0 + 36, y), label, font=lf, fill=ga.BRAND_ON_SURFACE_VARIANT)
    box = (x0 + 36, y + 34, x1 - 36, y + 100)
    draw.rounded_rectangle(box, radius=14, outline=ga.BRAND_PRIMARY if not placeholder else ga.BRAND_OUTLINE, width=3)
    vf = ga.regular(30)
    draw.text(
        (box[0] + 20, box[1] + 16),
        value,
        font=vf,
        fill=ga.BRAND_ON_SURFACE_VARIANT if placeholder else ga.BRAND_ON_SURFACE,
    )
    icon_pt = None
    if icon:
        cx, cy = box[2] - 40, (box[1] + box[3]) / 2 - 6
        contact_icon(draw, cx, cy)
        icon_pt = (cx, cy + 6)
    return y + 130, icon_pt


def info_card(draw, x0, x1, y, lines: list[tuple[str, bool]], height: int | None = None) -> int:
    h = height or (40 + 40 * len(lines))
    box = (x0 + 24, y, x1 - 24, y + h)
    draw.rounded_rectangle(box, radius=22, fill=ga.WHITE, outline=ga.BRAND_OUTLINE, width=2)
    ty = box[1] + 22
    for text, is_bold in lines:
        f = ga.bold(28) if is_bold else ga.regular(24)
        ty = wrapped(draw, (box[0] + 30, ty), text, f, ga.BRAND_ON_SURFACE if is_bold else ga.BRAND_ON_SURFACE_VARIANT, box[2] - box[0] - 60, 36)
        ty += 4
    return box[3] + 24


def disabled_banner(draw, x0, x1, y, label) -> tuple[int, Point]:
    box = (x0 + 36, y, x1 - 36, y + 110)
    draw.rounded_rectangle(box, radius=20, fill=ga.BRAND_OUTLINE)
    f = ga.bold(34)
    ga.draw_centered_text(draw, (box[0] + box[2]) / 2, box[1] + 36, label, f, ga.BRAND_ON_SURFACE_VARIANT)
    return box[3] + 30, ((box[0] + box[2]) / 2, (box[1] + box[3]) / 2)


def progress_bar(draw, x0, x1, y, fraction: float, label: str) -> int:
    f = ga.regular(26)
    draw.text((x0 + 36, y), label, font=f, fill=ga.BRAND_ON_SURFACE)
    track = (x0 + 36, y + 44, x1 - 36, y + 60)
    draw.rounded_rectangle(track, radius=8, fill=ga.BRAND_OUTLINE)
    fill_x = track[0] + (track[2] - track[0]) * max(0.02, min(1.0, fraction))
    draw.rounded_rectangle((track[0], track[1], fill_x, track[3]), radius=8, fill=ga.BRAND_PRIMARY)
    return y + 90


# ---------------------------------------------------------------------------
# Overlays: in-app AlertDialog, Android system permission prompt, heads-up
# notification. All draw onto an existing screen image.
# ---------------------------------------------------------------------------
def scrim(img: Image.Image, screen_box: Box, alpha: int = 140) -> ImageDraw.ImageDraw:
    overlay = Image.new("RGBA", img.size, (0, 0, 0, 0))
    od = ImageDraw.Draw(overlay)
    od.rounded_rectangle(screen_box, radius=48, fill=(*SCRIM, alpha))
    img.paste(Image.alpha_composite(img.convert("RGBA"), overlay).convert("RGB"))
    return ImageDraw.Draw(img)


def app_dialog(img: Image.Image, screen_box: Box, title: str, message: str, confirm: str, dismiss: str = "Cancel") -> Point:
    """The Material 3 AlertDialog MainActivity shows *before* each system
    prompt (sms/contacts/notifications rationale, battery)."""
    draw = scrim(img, screen_box)
    x0, y0, x1, y1 = screen_box
    dx0, dx1 = x0 + 60, x1 - 60
    tf, mf = ga.bold(36), ga.regular(28)
    lines = ga.wrap_text(draw, message, mf, dx1 - dx0 - 80)
    h = 60 + 50 + 30 + len(lines) * 40 + 40 + 70 + 40
    cy = (y0 + y1) / 2
    box = (dx0, cy - h / 2, dx1, cy + h / 2)
    draw.rounded_rectangle(box, radius=36, fill=ga.BRAND_SURFACE)
    ty = wrapped(draw, (box[0] + 40, box[1] + 50), title, tf, ga.BRAND_ON_SURFACE, dx1 - dx0 - 80, 46)
    ty += 20
    for line in lines:
        draw.text((box[0] + 40, ty), line, font=mf, fill=ga.BRAND_ON_SURFACE_VARIANT)
        ty += 40
    bf = ga.bold(30)
    by = box[3] - 80
    cw = ga.text_width(draw, confirm, bf)
    confirm_x = box[2] - 40 - cw
    draw.text((confirm_x, by), confirm, font=bf, fill=ga.BRAND_PRIMARY)
    dw = ga.text_width(draw, dismiss, bf)
    draw.text((confirm_x - 50 - dw, by), dismiss, font=bf, fill=ga.BRAND_PRIMARY)
    return (confirm_x + cw / 2, by + 18)


def system_prompt(img: Image.Image, screen_box: Box, glyph: str, question: str, allow: str = "Allow", deny: str = "Don't allow") -> Point:
    """Android's own runtime-permission dialog (the app cannot style this)."""
    draw = scrim(img, screen_box, 170)
    x0, y0, x1, y1 = screen_box
    dx0, dx1 = x0 + 50, x1 - 50
    qf = ga.regular(34)
    lines = ga.wrap_text(draw, question, qf, dx1 - dx0 - 90)
    h = 50 + 70 + 40 + len(lines) * 46 + 50 + 2 * 96 + 30
    cy = (y0 + y1) / 2 + 60
    box = (dx0, cy - h / 2, dx1, cy + h / 2)
    draw.rounded_rectangle(box, radius=40, fill=SYSTEM_DIALOG_BG)
    gcx, gcy = (dx0 + dx1) / 2, box[1] + 50 + 35
    draw.ellipse((gcx - 35, gcy - 35, gcx + 35, gcy + 35), fill=ga.BRAND_PRIMARY_CONTAINER)
    ga.draw_centered_text(draw, gcx, gcy - 22, glyph, ga.bold(36), ga.BRAND_PRIMARY)
    ty = gcy + 35 + 40
    for line in lines:
        ga.draw_centered_text(draw, (dx0 + dx1) / 2, ty, line, qf, ga.BRAND_ON_SURFACE)
        ty += 46
    ty += 30
    allow_pt = button(draw, (dx0 + 40, ty, dx1 - 40, ty + 80), allow)
    ty += 96
    button(draw, (dx0 + 40, ty, dx1 - 40, ty + 80), deny, filled=False)
    return allow_pt


def heads_up(img: Image.Image, screen_box: Box, title: str, body: str, offset: float = 0.0) -> None:
    """A heads-up notification sliding down from the status bar; offset in
    [0, 1] where 1 = fully shown."""
    draw = ImageDraw.Draw(img)
    x0, y0, x1, _ = screen_box
    h = 170
    top = y0 + 20 - (1 - offset) * (h + 40)
    box = (x0 + 24, top, x1 - 24, top + h)
    shadow = Image.new("RGBA", img.size, (0, 0, 0, 0))
    ImageDraw.Draw(shadow).rounded_rectangle((box[0], box[1] + 8, box[2], box[3] + 8), radius=32, fill=(0, 0, 0, 70))
    shadow = shadow.filter(ImageFilter.GaussianBlur(10))
    img.paste(Image.alpha_composite(img.convert("RGBA"), shadow).convert("RGB"))
    draw = ImageDraw.Draw(img)
    draw.rounded_rectangle(box, radius=32, fill=ga.WHITE, outline=ga.BRAND_OUTLINE, width=2)
    draw.ellipse((box[0] + 28, box[1] + 30, box[0] + 88, box[1] + 90), fill=ga.BRAND_SECONDARY)
    ga.draw_centered_text(draw, box[0] + 58, box[1] + 42, "M", ga.bold(30), ga.WHITE)
    draw.text((box[0] + 112, box[1] + 26), "Messages · now", font=ga.regular(22), fill=ga.BRAND_ON_SURFACE_VARIANT)
    draw.text((box[0] + 112, box[1] + 60), title, font=ga.bold(30), fill=ga.BRAND_ON_SURFACE)
    draw.text((box[0] + 112, box[1] + 104), body, font=ga.regular(28), fill=ga.BRAND_ON_SURFACE_VARIANT)


# ---------------------------------------------------------------------------
# App screens. Each returns (image, tap targets by name).
# ---------------------------------------------------------------------------
def app_chrome(caption, badge, title, tab: int | None, icons=1, fab=False):
    img, draw, sb = canvas(caption, badge)
    ga.draw_status_bar(draw, sb)
    top = ga.draw_app_bar(draw, sb, title, icons=icons)
    targets: dict[str, Point] = {}
    # The right-most app-bar icon (the gear on main tabs).
    targets["appbar_action"] = (sb[2] - 50, sb[1] + ga.STATUS_BAR_H + ga.APP_BAR_H / 2)
    nav_top = sb[3]
    if tab is not None:
        nav_top = ga.draw_bottom_nav(draw, sb, active=tab)
        seg = (sb[2] - sb[0]) / 3
        for i, name in enumerate(("tab_backup", "tab_redirect", "tab_schedule")):
            targets[name] = (sb[0] + seg * i + seg / 2, nav_top + 40)
    if fab:
        ga.draw_fab(draw, sb, nav_top)
        targets["fab"] = (sb[2] - 90, nav_top - 90)
    return img, draw, sb, top + 30, targets


def title_card() -> Image.Image:
    img = Image.new("RGB", (W, H), ga.BRAND_PRIMARY)
    draw = ImageDraw.Draw(img)
    for y in range(H):
        t = y / H
        c = tuple(int(a * (1 - t) + b * t) for a, b in zip(ga.BRAND_PRIMARY, ga.BRAND_PRIMARY_DARK))
        draw.line([(0, y), (W, y)], fill=c)
    icon = ga.draw_app_icon(300)
    mask = Image.new("L", (300, 300), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, 300, 300), radius=66, fill=255)
    img.paste(icon, ((W - 300) // 2, 360), mask)
    ga.draw_centered_text(draw, W / 2, 720, "SMS Toolbox", ga.bold(88), ga.WHITE)
    ga.draw_centered_text(draw, W / 2, 830, "App walkthrough", ga.regular(46), ga.BRAND_PRIMARY_CONTAINER)
    y = 1010
    draw.text((140, y), "Permissions shown in this video:", font=ga.bold(38), fill=ga.WHITE)
    y += 80
    for line in (
        "SMS: read, receive, send",
        "Contacts",
        "Notifications",
        "Run in background (battery)",
        "Internet: your Gmail only",
    ):
        draw.ellipse((150, y + 14, 170, y + 34), fill=AMBER)
        draw.text((196, y), line, font=ga.regular(38), fill=ga.WHITE)
        y += 70
    ga.draw_centered_text(draw, W / 2, 1700, "Each one is asked for only when a feature needs it.", ga.regular(32), ga.BRAND_PRIMARY_CONTAINER)
    return img


def backup_no_credentials():
    img, draw, sb, y, t = app_chrome(["Opens on Backup.", "No permission asked at launch."], None, "Backup to Gmail", 0)
    x0, _, x1, _ = sb
    y = info_card(
        draw,
        x0,
        x1,
        y,
        [
            ("No Gmail app password set", True),
            ("To back up your messages, add your Gmail address and an app password in Settings.", False),
            ("We back up your SMS to a specific Gmail label, so they stay archived and searchable right in Gmail.", False),
        ],
    )
    t["open_settings"] = button(draw, (x0 + 60, y, x1 - 60, y + 86), "Open Settings")
    return img, t


def settings_gmail(stage: int):
    """stage 0 = empty, 1 = typed, 2 = checking, 3 = connected."""
    img, draw, sb, y, t = app_chrome(
        ["Step 1: connect your own", "Gmail with an app password"],
        "INTERNET · no prompt · imap.gmail.com only",
        "Settings",
        None,
    )
    x0, _, x1, _ = sb
    draw.text((x0 + 36, y), "Gmail backup", font=ga.bold(32), fill=ga.BRAND_PRIMARY)
    y += 60
    y, _ = field_box(draw, x0, x1, y, "Gmail address", "sms.toolbox.demo@gmail.com" if stage else "you@gmail.com", placeholder=not stage)
    y, _ = field_box(draw, x0, x1, y, "App password", "•••• •••• •••• ••••" if stage else "16-character app password", placeholder=not stage)
    label = {0: "Save and verify", 1: "Save and verify", 2: "Checking…", 3: "Save and verify"}[stage]
    t["save"] = button(draw, (x0 + 36, y, x1 - 36, y + 86), label, color=ga.BRAND_PRIMARY if stage != 2 else ga.BRAND_ON_SURFACE_VARIANT)
    y += 116
    if stage == 3:
        r, cx, cy = 18, x0 + 56, y + 18
        draw.ellipse((cx - r, cy - r, cx + r, cy + r), fill=ga.BRAND_SECONDARY)
        draw.text((cx + 34, y), "Connected to Gmail", font=ga.bold(30), fill=ga.BRAND_SECONDARY)
        y += 60
    wrapped(
        draw,
        (x0 + 36, y),
        "SMS Toolbox connects straight to Gmail with an app password. It is stored encrypted on this device and is never sent anywhere else.",
        ga.regular(24),
        ga.BRAND_ON_SURFACE_VARIANT,
        x1 - x0 - 72,
        34,
    )
    t["field"] = ((x0 + x1) / 2, sb[1] + 400)
    return img, t


def backup_ready(previous_on: bool, caption=None, badge=None):
    img, draw, sb, y, t = app_chrome(caption or ["Step 2: turn on", "Backup to Gmail"], badge, "Backup to Gmail", 0)
    x0, _, x1, _ = sb
    y, t["enable"] = disabled_banner(draw, x0, x1, y, "Enable Backup to Gmail")
    y = ga.draw_form_field(draw, x0, x1, y, "Gmail label", "SMS")
    y = ga.draw_form_field(draw, x0, x1, y, "Email subject", "SMS with $CONTACT_NAME ($PHONE_NUMBER)")
    y += 6
    t["previous"] = (x1 - 90, y + 16)
    y = ga.draw_toggle_row(draw, x0, x1, y, "Backup all previous SMS", previous_on)
    wrapped(
        draw,
        (x0 + 36, y),
        "Messages are backed up as they arrive. With “Backup all previous SMS” on, your existing messages are backed up immediately first.",
        ga.regular(24),
        ga.BRAND_ON_SURFACE_VARIANT,
        x1 - x0 - 72,
        34,
    )
    return img, t


def backup_running(done: int, total: int, caption, badge):
    img, draw, sb, y, t = app_chrome(caption, badge, "Backup to Gmail", 0)
    x0, _, x1, _ = sb
    y = ga.draw_enabled_banner(draw, x0, x1, y, "Backup to Gmail enabled")
    y = ga.draw_form_field(draw, x0, x1, y, "Gmail label", "SMS")
    y = ga.draw_form_field(draw, x0, x1, y, "Email subject", "SMS with $CONTACT_NAME ($PHONE_NUMBER)")
    y = ga.draw_toggle_row(draw, x0, x1, y, "Backup all previous SMS", True)
    if done < total:
        y = progress_bar(draw, x0, x1, y, done / total, f"Backing up {done:,} of {total:,}")
    else:
        y = info_card(
            draw,
            x0,
            x1,
            y,
            [(f"{total:,} messages backed up", True), ("Continues automatically as new messages arrive", False)],
        )
    return img, sb, t


def gmail_inbox(highlight: bool):
    img, draw, sb = canvas(["Every SMS becomes an email", "under your “SMS” label"], "Your Gmail · your account")
    ga.draw_status_bar(draw, sb)
    x0, y0, x1, _ = sb
    top = y0 + ga.STATUS_BAR_H
    draw.rectangle((x0, top, x1, top + 120), fill=ga.WHITE)
    draw.rounded_rectangle((x0 + 30, top + 20, x1 - 30, top + 100), radius=40, fill=ga.BRAND_OUTLINE)
    draw.text((x0 + 70, top + 42), "Search in mail", font=ga.regular(30), fill=ga.BRAND_ON_SURFACE_VARIANT)
    y = top + 150
    draw.text((x0 + 36, y), "Label: SMS", font=ga.bold(30), fill=ga.BRAND_ON_SURFACE_VARIANT)
    y += 60
    rows = [
        ("Mom", "SMS with Mom (+351 933 111 222)", "Dinner at 8? Bring the dessert", "now"),
        ("WorkBank", "SMS with WorkBank (WorkBank)", "Your code is 482 913", "09:47"),
        ("Ana Silva", "SMS with Ana Silva (+351 961 222 333)", "See you tomorrow!", "Yesterday"),
        ("DHL", "SMS with DHL (DHL)", "Your parcel is out for delivery", "Yesterday"),
    ]
    for i, (who, subject, snippet, when) in enumerate(rows):
        box = (x0 + 16, y, x1 - 16, y + 150)
        if i == 0 and highlight:
            draw.rounded_rectangle(box, radius=20, fill=ga.BRAND_PRIMARY_CONTAINER)
        r, cx, cy = 34, box[0] + 56, box[1] + 60
        draw.ellipse((cx - r, cy - r, cx + r, cy + r), fill=ga.CATEGORY_ACCENTS[i % len(ga.CATEGORY_ACCENTS)])
        ga.draw_centered_text(draw, cx, cy - 20, who[0], ga.bold(32), ga.WHITE)
        draw.text((box[0] + 116, box[1] + 18), "SMS Toolbox", font=ga.bold(28), fill=ga.BRAND_ON_SURFACE)
        tw = ga.text_width(draw, when, ga.regular(22))
        draw.text((box[2] - 24 - tw, box[1] + 22), when, font=ga.regular(22), fill=ga.BRAND_ON_SURFACE_VARIANT)
        draw.text((box[0] + 116, box[1] + 58), subject, font=ga.bold(24) if i == 0 else ga.regular(24), fill=ga.BRAND_ON_SURFACE)
        draw.text((box[0] + 116, box[1] + 96), snippet, font=ga.regular(24), fill=ga.BRAND_ON_SURFACE_VARIANT)
        y += 166
    wrapped(
        draw,
        (x0 + 36, y + 20),
        "Names in the subject come from your contacts (if Contacts is allowed). Otherwise the number is used.",
        ga.regular(24),
        ga.BRAND_ON_SURFACE_VARIANT,
        x1 - x0 - 72,
        34,
    )
    return img


def redirect_list(with_rule: bool, caption, badge=None):
    img, draw, sb, y, t = app_chrome(caption, badge, "Redirect Rules", 1, icons=2, fab=True)
    x0, _, x1, _ = sb
    if with_rule:
        y = ga.draw_category_header(draw, x0, x1, y, "Family", "1 rule")
        ga.draw_card(draw, x0, x1, y, 150, ga.CATEGORY_ACCENTS[1], "Mom's phone", "Sender: Mom", "Target: +351 912 345 678")
    else:
        ga.draw_centered_text(draw, (x0 + x1) / 2, y + 380, "No rules yet", ga.bold(34), ga.BRAND_ON_SURFACE_VARIANT)
        ga.draw_centered_text(draw, (x0 + x1) / 2, y + 430, "Tap + to forward SMS from a sender", ga.regular(26), ga.BRAND_ON_SURFACE_VARIANT)
    return img, sb, t


def rule_form(stage: int, caption, badge=None):
    """stage 0 = empty, 1 = sender picked, 2 = filled."""
    img, draw, sb, y, t = app_chrome(caption, badge, "Create Rule", None)
    x0, _, x1, _ = sb
    y, _ = field_box(draw, x0, x1, y, "Rule Name", "Mom's phone" if stage >= 2 else "Rule name", placeholder=stage < 2)
    y, t["sender_icon"] = field_box(draw, x0, x1, y, "Sender", "Mom" if stage >= 1 else "Name or number", icon=True, placeholder=stage < 1)
    y, t["target_icon"] = field_box(draw, x0, x1, y, "Target Number", "+351 912 345 678" if stage >= 2 else "Forward to", icon=True, placeholder=stage < 2)
    y, _ = field_box(draw, x0, x1, y, "Category", "Family" if stage >= 2 else "Category", placeholder=stage < 2)
    t["save"] = button(draw, (x0 + 36, y + 20, x1 - 36, y + 106), "Save Rule")
    return img, sb, t


def contact_picker():
    img, draw, sb = canvas(["Android's contact picker:", "choose who to forward from"], "READ_CONTACTS")
    ga.draw_status_bar(draw, sb)
    x0, y0, x1, _ = sb
    top = y0 + ga.STATUS_BAR_H
    draw.text((x0 + 36, top + 40), "Choose a contact", font=ga.bold(38), fill=ga.BRAND_ON_SURFACE)
    y = top + 130
    targets = {}
    for i, name in enumerate(["Ana Silva", "DHL", "Mom", "Rui Costa", "WorkBank"]):
        r, cx, cy = 34, x0 + 80, y + 50
        draw.ellipse((cx - r, cy - r, cx + r, cy + r), fill=ga.CATEGORY_ACCENTS[i])
        ga.draw_centered_text(draw, cx, cy - 20, name[0], ga.bold(30), ga.WHITE)
        draw.text((x0 + 140, y + 30), name, font=ga.regular(34), fill=ga.BRAND_ON_SURFACE)
        targets[name] = (x0 + 300, y + 50)
        draw.line((x0 + 140, y + 100, x1 - 30, y + 100), fill=ga.BRAND_OUTLINE, width=2)
        y += 110
    return img, targets


def history_screen():
    img, draw, sb, y, t = app_chrome(["Every forward is logged", "in Redirect History"], "SEND_SMS", "Redirect History", None)
    x0, _, x1, _ = sb
    box = (x0 + 24, y, x1 - 24, y + 160)
    draw.rounded_rectangle(box, radius=22, fill=ga.BRAND_PRIMARY_CONTAINER, outline=ga.BRAND_OUTLINE, width=2)
    draw.text((box[0] + 30, box[1] + 18), "Mom → +351 912 345 678", font=ga.bold(28), fill=ga.BRAND_ON_SURFACE)
    draw.text((box[0] + 30, box[1] + 60), "Rule: Mom's phone", font=ga.regular(24), fill=ga.BRAND_ON_SURFACE_VARIANT)
    draw.text((box[0] + 30, box[1] + 100), "Today, 19:02", font=ga.regular(24), fill=ga.BRAND_ON_SURFACE_VARIANT)
    return img


def schedule_list(with_item: bool, caption, badge=None):
    img, draw, sb, y, t = app_chrome(caption, badge, "Scheduled SMS", 2, icons=2, fab=True)
    x0, _, x1, _ = sb
    if with_item:
        y = ga.draw_category_header(draw, x0, x1, y, "Birthdays", "1 item")
        ga.draw_card(draw, x0, x1, y, 150, ga.CATEGORY_ACCENTS[4], "Happy birthday, Ana!", "Next send: 3 Sep, 08:00", "Repeat: Yearly")
    else:
        ga.draw_centered_text(draw, (x0 + x1) / 2, y + 380, "Nothing scheduled", ga.bold(34), ga.BRAND_ON_SURFACE_VARIANT)
    return img, sb, t


def schedule_form(filled: bool):
    img, draw, sb, y, t = app_chrome(
        ["Pick a contact, a message", "and when to send it"],
        "READ_CONTACTS · already allowed",
        "Create Schedule SMS",
        None,
    )
    x0, _, x1, _ = sb
    y, t["contact_icon"] = field_box(draw, x0, x1, y, "Contact", "Ana Silva" if filled else "Name or number", icon=True, placeholder=not filled)
    y, _ = field_box(draw, x0, x1, y, "Message", "Happy birthday, Ana!" if filled else "Message", placeholder=not filled)
    y, _ = field_box(draw, x0, x1, y, "Date", "3 September 2026, 08:00" if filled else "Date", placeholder=not filled)
    y, _ = field_box(draw, x0, x1, y, "Repeat", "Yearly" if filled else "Never", placeholder=not filled)
    t["save"] = button(draw, (x0 + 36, y + 20, x1 - 36, y + 106), "Save Scheduled SMS")
    return img, sb, t


def summary_card() -> Image.Image:
    img = Image.new("RGB", (W, H), ga.BRAND_SURFACE)
    draw = ImageDraw.Draw(img)
    draw.rectangle((0, 0, W, 260), fill=ga.BRAND_PRIMARY)
    ga.draw_centered_text(draw, W / 2, 80, "Why each permission", ga.bold(60), ga.WHITE)
    ga.draw_centered_text(draw, W / 2, 160, "is needed", ga.bold(60), ga.WHITE)
    rows = [
        ("READ_SMS", "Back up your existing messages to your Gmail (core feature: Backup and restore)."),
        ("RECEIVE_SMS", "Back up each new SMS as it arrives, and forward it if a rule matches."),
        ("SEND_SMS", "Forward matching SMS and send your scheduled messages, from your own SIM."),
        ("READ_CONTACTS", "Pick senders and recipients from your contacts; show names in backups and history."),
        ("POST_NOTIFICATIONS", "Show backup progress and confirm forwards and scheduled sends."),
        ("Battery exemption", "Keep forwarding and scheduled sends on time while the phone sleeps."),
        ("INTERNET", "Connect only to imap.gmail.com, using your own app password."),
    ]
    y = 320
    for name, why in rows:
        draw.rounded_rectangle((60, y, W - 60, y + 190), radius=24, fill=ga.WHITE, outline=ga.BRAND_OUTLINE, width=2)
        draw.rounded_rectangle((60, y, 76, y + 190), radius=8, fill=AMBER)
        draw.text((106, y + 24), name, font=ga.bold(36), fill=ga.BRAND_PRIMARY)
        wrapped(draw, (106, y + 78), why, ga.regular(28), ga.BRAND_ON_SURFACE_VARIANT, W - 220, 38)
        y += 210
    for i, line in enumerate(("Your messages never pass through our servers.", "Analytics are anonymous and exclude SMS content.")):
        ga.draw_centered_text(draw, W / 2, y + 10 + i * 38, line, ga.regular(28), ga.BRAND_ON_SURFACE_VARIANT)
    return img


# ---------------------------------------------------------------------------
# Timeline
# ---------------------------------------------------------------------------
@dataclass
class Shot:
    duration: float
    frame: Image.Image | None = None
    # For animated shots: progress in [0, 1] -> image.
    render: Callable[[float], Image.Image] | None = None
    tap: Point | None = None
    tap_at: float = 0.7  # fraction of the shot at which the finger lands
    fade_in: bool = True
    label: str = ""

    def image_at(self, t: float) -> Image.Image:
        base = self.render(t) if self.render else self.frame
        assert base is not None
        if self.tap is None:
            return base
        land = self.tap_at * self.duration
        now = t * self.duration
        dt = now - land
        if dt < -0.45 or dt > 0.55:
            return base
        img = base.copy()
        overlay = Image.new("RGBA", img.size, (0, 0, 0, 0))
        od = ImageDraw.Draw(overlay)
        x, y = self.tap
        if dt < 0:
            # Finger approaching: a solid dot fading in.
            a = int(200 * (1 + dt / 0.45))
            od.ellipse((x - 34, y - 34, x + 34, y + 34), fill=(255, 255, 255, a // 2), outline=(26, 115, 232, a), width=6)
        else:
            # Ripple expanding outwards.
            p = dt / 0.55
            r = 34 + 70 * p
            a = int(220 * (1 - p))
            od.ellipse((x - r, y - r, x + r, y + r), outline=(26, 115, 232, a), width=8)
            od.ellipse((x - 30, y - 30, x + 30, y + 30), fill=(26, 115, 232, int(a * 0.6)))
        return Image.alpha_composite(img.convert("RGBA"), overlay).convert("RGB")


def with_dialog(base: Image.Image, sb: Box, fn: Callable[[Image.Image, Box], Point]) -> tuple[Image.Image, Point]:
    img = base.copy()
    pt = fn(img, sb)
    return img, pt


def build_timeline() -> list[Shot]:
    shots: list[Shot] = [Shot(4.0, title_card(), label="Title")]

    # 1. Landing on Backup.
    img, t = backup_no_credentials()
    shots.append(Shot(4.0, img, tap=t["open_settings"], tap_at=0.8, label="Launch: Backup tab"))

    # 2. Gmail credentials (INTERNET).
    img0, t = settings_gmail(0)
    shots.append(Shot(2.5, img0, tap=t["field"], tap_at=0.6, label="Settings: Gmail"))
    img1, t = settings_gmail(1)
    shots.append(Shot(2.5, img1, tap=t["save"], tap_at=0.7, fade_in=False))
    shots.append(Shot(1.2, settings_gmail(2)[0], fade_in=False))
    shots.append(Shot(3.0, settings_gmail(3)[0], fade_in=False, label="Connected to Gmail"))

    # 3. Enable backup → SMS rationale → system SMS prompt → notifications.
    img, t = backup_ready(False)
    shots.append(Shot(2.5, img, tap=t["previous"], tap_at=0.7, label="Backup: ready"))
    img, t = backup_ready(True)
    shots.append(Shot(2.5, img, tap=t["enable"], tap_at=0.7, fade_in=False))

    base, _ = backup_ready(True, ["Why SMS access?", "The app explains first"], "READ_SMS · RECEIVE_SMS · SEND_SMS")
    sb = (ga.PHONE_MARGIN_X + ga.BEZEL, ga.PHONE_TOP + ga.BEZEL, W - ga.PHONE_MARGIN_X - ga.BEZEL, ga.PHONE_BOTTOM - ga.BEZEL)
    img, pt = with_dialog(
        base,
        sb,
        lambda i, s: app_dialog(
            i,
            s,
            "SMS permission needed",
            "SMS Toolbox needs SMS permissions to read incoming text messages, back them up and forward or send them for you. "
            "MMS and RCS messages are out of scope and will not be forwarded or scheduled.",
            "Proceed",
        ),
    )
    shots.append(Shot(6.0, img, tap=pt, tap_at=0.85, label="SMS rationale"))

    base, _ = backup_ready(True, ["Read SMS: copy existing texts", "Receive SMS: back up new ones"], "READ_SMS · RECEIVE_SMS · SEND_SMS")
    img, pt = with_dialog(base, sb, lambda i, s: system_prompt(i, s, "✉", "Allow SMS Toolbox to send and view SMS messages?"))
    shots.append(Shot(5.0, img, tap=pt, tap_at=0.8, label="SMS prompt"))

    base, _ = backup_ready(True, ["Notifications show", "backup progress"], "POST_NOTIFICATIONS")
    img, pt = with_dialog(base, sb, lambda i, s: system_prompt(i, s, "!", "Allow SMS Toolbox to send you notifications?"))
    shots.append(Shot(4.0, img, tap=pt, tap_at=0.75, label="Notif. prompt"))

    # 4. Backup runs, then a new SMS arrives and lands in Gmail.
    total = 3214
    cap = ["Existing SMS are backed up", "to your Gmail (READ_SMS)"]
    shots.append(
        Shot(
            4.0,
            render=lambda p: backup_running(int(total * min(1.0, p * 1.15)) if p < 0.87 else total, total, cap, "READ_SMS")[0],
            label="Backup running",
        )
    )

    done_img, sb_run, _ = backup_running(total, total, ["A new SMS arrives…", "and is backed up at once"], "RECEIVE_SMS")

    def incoming(p: float) -> Image.Image:
        img = done_img.copy()
        heads_up(img, sb_run, "Mom", "Dinner at 8? Bring the dessert", offset=min(1.0, p * 3))
        return img

    shots.append(Shot(3.5, render=incoming, label="Incoming SMS"))
    shots.append(Shot(4.5, gmail_inbox(True), label="Gmail: SMS label"))

    # 5. Redirect: contacts, rule, battery exemption, forward.
    img, sb_r, t = redirect_list(False, ["Redirect: forward SMS", "from a sender to you"])
    shots.append(Shot(3.0, img, tap=t["fab"], tap_at=0.75, label="Redirect tab"))
    img, sb_r, t = rule_form(0, ["Tap the contact icon", "to pick the sender"])
    shots.append(Shot(3.0, img, tap=t["sender_icon"], tap_at=0.75, label="Create rule"))

    base, sb_r, _ = rule_form(0, ["Why Contacts?", "The app explains first"], "READ_CONTACTS")
    img, pt = with_dialog(
        base,
        sb_r,
        lambda i, s: app_dialog(
            i,
            s,
            "Contacts permission needed",
            "SMS Toolbox needs access to your contacts so you can pick a sender or target directly from your contact list instead of typing a number.",
            "Proceed",
        ),
    )
    shots.append(Shot(5.0, img, tap=pt, tap_at=0.85, label="Contacts rationale"))

    base, sb_r, _ = rule_form(0, ["Contacts: pick people", "instead of typing numbers"], "READ_CONTACTS")
    img, pt = with_dialog(base, sb_r, lambda i, s: system_prompt(i, s, "☺", "Allow SMS Toolbox to access your contacts?"))
    shots.append(Shot(4.0, img, tap=pt, tap_at=0.8, label="Contacts prompt"))

    img, picks = contact_picker()
    shots.append(Shot(3.0, img, tap=picks["Mom"], tap_at=0.7, label="Contact picker"))
    img, sb_r, t = rule_form(2, ["Forward Mom's texts", "to your other phone"])
    shots.append(Shot(3.0, img, tap=t["save"], tap_at=0.75, label="Rule filled"))

    base, sb_r, _ = redirect_list(True, ["Keep running in background", "so forwards are on time"], "IGNORE_BATTERY_OPTIMIZATIONS")
    img, pt = with_dialog(
        base,
        sb_r,
        lambda i, s: app_dialog(
            i,
            s,
            "Keep running in background",
            "SMS Toolbox needs to keep running in the background to forward messages and send scheduled SMS reliably. "
            "Disable battery optimization for this app so Android does not stop it.",
            "Proceed",
        ),
    )
    shots.append(Shot(5.0, img, tap=pt, tap_at=0.85, label="Battery rationale"))
    img, pt = with_dialog(base, sb_r, lambda i, s: system_prompt(i, s, "⚡", "Let SMS Toolbox always run in background?"))
    shots.append(Shot(3.5, img, tap=pt, tap_at=0.75, label="Battery prompt"))

    rule_img, sb_r, _ = redirect_list(True, ["A matching SMS arrives…", "and is forwarded (SEND_SMS)"], "RECEIVE_SMS · SEND_SMS")

    def forward(p: float) -> Image.Image:
        img = rule_img.copy()
        heads_up(img, sb_r, "Mom", "Can you call me later?", offset=min(1.0, p * 3))
        return img

    shots.append(Shot(3.5, render=forward, label="Forward"))
    shots.append(Shot(3.5, history_screen(), label="History"))

    # 6. Schedule.
    img, _, t = schedule_list(False, ["Schedule: send a text", "later, even on repeat"])
    shots.append(Shot(3.0, img, tap=t["fab"], tap_at=0.75, label="Schedule tab"))
    img, _, t = schedule_form(False)
    shots.append(Shot(2.5, img, tap=t["contact_icon"], tap_at=0.7, label="Schedule form"))
    img, _, t = schedule_form(True)
    shots.append(Shot(3.0, img, tap=t["save"], tap_at=0.8, fade_in=False))
    img, _, _ = schedule_list(True, ["Sent at 08:00 on 3 Sep", "from your SIM (SEND_SMS)"], "SEND_SMS")
    shots.append(Shot(3.5, img, label="Scheduled"))

    # 7. Summary.
    shots.append(Shot(9.0, summary_card(), label="Summary"))
    shots.append(Shot(3.0, title_card(), label="End"))
    return shots


def render(shots: list[Shot]) -> None:
    if shutil.which("ffmpeg") is None:
        sys.exit("ffmpeg not found on PATH")
    VIDEO_DIR.mkdir(parents=True, exist_ok=True)
    cmd = [
        "ffmpeg", "-y", "-loglevel", "error",
        "-f", "rawvideo", "-pix_fmt", "rgb24", "-s", f"{W}x{H}", "-r", str(FPS), "-i", "-",
        # Silent stereo track: some upload paths (YouTube included) treat a
        # video with no audio stream oddly.
        "-f", "lavfi", "-i", "anullsrc=channel_layout=stereo:sample_rate=44100",
        "-shortest",
        "-c:v", "libx264", "-preset", "medium", "-crf", "20", "-pix_fmt", "yuv420p",
        "-c:a", "aac", "-b:a", "64k",
        "-movflags", "+faststart",
        str(OUTPUT),
    ]  # fmt: skip
    proc = subprocess.Popen(cmd, stdin=subprocess.PIPE)
    assert proc.stdin is not None
    previous_last: Image.Image | None = None
    for shot in shots:
        n = max(1, round(shot.duration * FPS))
        fade_n = round(FADE_S * FPS) if (shot.fade_in and previous_last is not None) else 0
        last = None
        for i in range(n):
            frame = shot.image_at(i / max(1, n - 1))
            if i < fade_n and previous_last is not None:
                a = (i + 1) / (fade_n + 1)
                a = 0.5 - 0.5 * math.cos(math.pi * a)
                frame = Image.blend(previous_last, frame, a)
            proc.stdin.write(frame.tobytes())
            last = frame
        previous_last = shot.image_at(1.0) if shot.tap is None else last
    proc.stdin.close()
    if proc.wait() != 0:
        sys.exit("ffmpeg failed")
    total = sum(s.duration for s in shots)
    print(f"Wrote {OUTPUT.relative_to(ROOT)} ({total:.0f}s)")


def storyboard(shots: list[Shot]) -> None:
    labelled = [s for s in shots if s.label]
    cols = 6
    tw, th = 270, 480
    rows = math.ceil(len(labelled) / cols)
    sheet = Image.new("RGB", (cols * tw, rows * (th + 50)), ga.WHITE)
    draw = ImageDraw.Draw(sheet)
    elapsed = 0.0
    starts = {}
    for s in shots:
        starts[id(s)] = elapsed
        elapsed += s.duration
    for i, s in enumerate(labelled):
        thumb = s.image_at(0.95).resize((tw, th), Image.LANCZOS)
        x, y = (i % cols) * tw, (i // cols) * (th + 50)
        sheet.paste(thumb, (x, y))
        t0 = starts[id(s)]
        draw.text((x + 8, y + th + 10), f"{int(t0 // 60)}:{int(t0 % 60):02d}  {s.label}", font=ga.regular(20), fill=ga.BRAND_ON_SURFACE)
    sheet.save(STORYBOARD, "PNG")
    print(f"Wrote {STORYBOARD.relative_to(ROOT)}")


def main() -> None:
    shots = build_timeline()
    storyboard(shots)
    render(shots)


if __name__ == "__main__":
    main()
