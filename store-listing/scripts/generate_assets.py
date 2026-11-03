#!/usr/bin/env python3
"""Generates the Play Store icon, feature graphic, and screenshot mockups
for SMS Toolbox — phone, plus 7-inch and 10-inch tablet ("large screens")
variants.

These are concept/marketing graphics built from the app's real brand colors
(app/src/main/java/.../presentation/theme/Color.kt) and real UI strings
(app/src/main/res/values/strings.xml) — not raw device screenshots. They are
a strong starting point for the Play Store listing; swap the screenshots for
real device captures (Android Studio "Take Screenshot" or `adb exec-out
screencap`) whenever convenient for pixel-perfect fidelity. The tablet
screenshots reuse the exact same rendered phone screens (the app has no
distinct tablet layout), recomposed into a landscape 16:9 promotional frame
instead of stretching the phone UI to fill a much larger canvas.

Usage:
    pip install pillow
    python3 generate_assets.py

Requires a TrueType font. Tries a short list of common system fonts and
falls back to Pillow's default bitmap font (lower quality) if none are found.
"""

from __future__ import annotations

import os
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent.parent
GRAPHICS_DIR = ROOT / "graphics"
SCREENSHOTS_DIR = ROOT / "screenshots" / "phone"
SEVEN_INCH_DIR = ROOT / "screenshots" / "sevenInchTablet"
TEN_INCH_DIR = ROOT / "screenshots" / "tenInchTablet"

# ---------------------------------------------------------------------------
# Brand palette — copied from presentation/theme/Color.kt so assets stay
# visually consistent with the real app. Update both places together.
# ---------------------------------------------------------------------------
BRAND_PRIMARY = (0x1A, 0x73, 0xE8)  # BrandPrimary
BRAND_PRIMARY_DARK = (0x18, 0x5A, 0xBC)  # gradient shade used in-app for the envelope flap
BRAND_PRIMARY_CONTAINER = (0xD3, 0xE3, 0xFD)  # BrandPrimaryContainer
BRAND_SECONDARY = (0x18, 0x80, 0x38)  # BrandSecondary (green)
WHITE = (0xFF, 0xFF, 0xFF)
BRAND_SURFACE = (0xFA, 0xFB, 0xFF)  # BrandSurfaceLight
BRAND_ON_SURFACE = (0x1A, 0x1C, 0x1E)  # BrandOnSurfaceLight
BRAND_OUTLINE = (0xDF, 0xE2, 0xEB)  # BrandSurfaceVariantLight
BRAND_ON_SURFACE_VARIANT = (0x43, 0x47, 0x4E)
CATEGORY_ACCENTS = [
    (0x1A, 0x73, 0xE8),
    (0x34, 0xA8, 0x53),
    (0xFB, 0xBC, 0x05),
    (0xEA, 0x43, 0x35),
    (0x12, 0xB5, 0xCB),
    (0x93, 0x34, 0xE6),
]
PHONE_BEZEL = (0x20, 0x22, 0x26)

FONT_CANDIDATES_BOLD = [
    "/System/Library/Fonts/Supplemental/Arial Bold.ttf",
    "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
    "/usr/share/fonts/truetype/liberation/LiberationSans-Bold.ttf",
]
FONT_CANDIDATES_REGULAR = [
    "/System/Library/Fonts/Supplemental/Arial.ttf",
    "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
    "/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf",
]


def _load_font(candidates: list[str], size: int) -> ImageFont.FreeTypeFont:
    for path in candidates:
        if os.path.exists(path):
            return ImageFont.truetype(path, size)
    return ImageFont.load_default(size=size)


def bold(size: int) -> ImageFont.FreeTypeFont:
    return _load_font(FONT_CANDIDATES_BOLD, size)


def regular(size: int) -> ImageFont.FreeTypeFont:
    return _load_font(FONT_CANDIDATES_REGULAR, size)


def text_width(draw: ImageDraw.ImageDraw, text: str, font: ImageFont.FreeTypeFont) -> float:
    return draw.textlength(text, font=font)


def draw_centered_text(
    draw: ImageDraw.ImageDraw,
    center_x: float,
    y: float,
    text: str,
    font: ImageFont.FreeTypeFont,
    fill,
) -> None:
    w = text_width(draw, text, font)
    draw.text((center_x - w / 2, y), text, font=font, fill=fill)


def wrap_text(draw: ImageDraw.ImageDraw, text: str, font: ImageFont.FreeTypeFont, max_width: float) -> list[str]:
    words = text.split()
    lines: list[str] = []
    current = ""
    for word in words:
        candidate = f"{current} {word}".strip()
        if text_width(draw, candidate, font) <= max_width or not current:
            current = candidate
        else:
            lines.append(current)
            current = word
    if current:
        lines.append(current)
    return lines


# ---------------------------------------------------------------------------
# Icon (shared by the standalone 512x512 icon and the feature graphic)
# ---------------------------------------------------------------------------
def draw_app_icon(size: int) -> Image.Image:
    """Recreates the real launcher icon (blue bg, white chat bubble, two
    forward chevrons) at high resolution — see drawable/ic_launcher_*.xml."""
    scale = 4  # supersample then downsize for clean anti-aliasing
    s = size * scale
    img = Image.new("RGB", (s, s), BRAND_PRIMARY)
    draw = ImageDraw.Draw(img)

    # Speech bubble
    left, top, right, bottom = 0.22 * s, 0.30 * s, 0.78 * s, 0.62 * s
    radius = 0.05 * s
    draw.rounded_rectangle((left, top, right, bottom), radius=radius, fill=WHITE)
    # Tail (bottom-left of the bubble, matching ic_launcher_foreground.xml)
    bw = right - left
    tail = [
        (left + 0.18 * bw, bottom - 1),
        (left + 0.40 * bw, bottom - 1),
        (left + 0.18 * bw, bottom + 0.16 * bw),
    ]
    draw.polygon(tail, fill=WHITE)

    # Two "forward" chevrons inside the bubble
    bh = bottom - top
    cy = top + bh / 2
    chevron_w = 0.13 * bw
    chevron_h = 0.22 * bh
    for i, cx in enumerate((left + 0.44 * bw, left + 0.62 * bw)):
        draw.polygon(
            [
                (cx - chevron_w / 2, cy - chevron_h / 2),
                (cx + chevron_w / 2, cy),
                (cx - chevron_w / 2, cy + chevron_h / 2),
            ],
            fill=BRAND_PRIMARY,
        )

    return img.resize((size, size), Image.LANCZOS)


def generate_icon() -> None:
    icon = draw_app_icon(512)
    GRAPHICS_DIR.mkdir(parents=True, exist_ok=True)
    icon.save(GRAPHICS_DIR / "icon-512.png", "PNG")
    print("Wrote graphics/icon-512.png")


# ---------------------------------------------------------------------------
# Feature graphic (1024x500)
# ---------------------------------------------------------------------------
def generate_feature_graphic() -> None:
    w, h = 1024, 500
    img = Image.new("RGB", (w, h), BRAND_PRIMARY)
    draw = ImageDraw.Draw(img)

    # Subtle vertical gradient for depth (BrandPrimary -> darker shade)
    for y in range(h):
        t = y / h
        r = int(BRAND_PRIMARY[0] * (1 - t) + BRAND_PRIMARY_DARK[0] * t)
        g = int(BRAND_PRIMARY[1] * (1 - t) + BRAND_PRIMARY_DARK[1] * t)
        b = int(BRAND_PRIMARY[2] * (1 - t) + BRAND_PRIMARY_DARK[2] * t)
        draw.line([(0, y), (w, y)], fill=(r, g, b))

    # Decorative category-accent dots, top-right corner (echoes the app's
    # color-coded rule categories) — kept away from the center-left where
    # Play overlays a Play-button if a promo video is attached.
    for i, color in enumerate(CATEGORY_ACCENTS):
        cx = w - 40 - (len(CATEGORY_ACCENTS) - i - 1) * 34
        cy = 40
        r = 10
        draw.ellipse((cx - r, cy - r, cx + r, cy + r), fill=color)

    # Icon, on a white rounded "badge" so it reads clearly against the blue
    # background instead of blending into it.
    icon_size = 260
    pad = 16
    badge_radius = icon_size * 0.22 + pad
    badge_box = (
        70 - pad,
        (h - icon_size) // 2 - pad,
        70 + icon_size + pad,
        (h - icon_size) // 2 + icon_size + pad,
    )
    draw.rounded_rectangle(badge_box, radius=badge_radius, fill=WHITE)

    icon = draw_app_icon(icon_size)
    icon_x, icon_y = 70, (h - icon_size) // 2
    icon_mask = Image.new("L", (icon_size, icon_size), 0)
    ImageDraw.Draw(icon_mask).rounded_rectangle(
        (0, 0, icon_size, icon_size), radius=icon_size * 0.22, fill=255
    )
    img.paste(icon, (icon_x, icon_y), mask=icon_mask)

    # Title + tagline
    text_x = icon_x + icon_size + 48
    title_font = bold(64)
    tagline_font = regular(30)
    title_lines = ["SMS Toolbox"]
    title_y = h / 2 - 66
    for line in title_lines:
        draw.text((text_x, title_y), line, font=title_font, fill=WHITE)
        title_y += 74

    # Manually split (rather than wrap_text's greedy fill) so the two lines
    # come out visually balanced instead of leaving a lone trailing word.
    tagline_lines = ["Back up & restore SMS,", "forward or schedule them."]
    tagline_y = title_y + 14
    for line in tagline_lines:
        draw.text((text_x, tagline_y), line, font=tagline_font, fill=BRAND_PRIMARY_CONTAINER)
        tagline_y += 40

    GRAPHICS_DIR.mkdir(parents=True, exist_ok=True)
    img.save(GRAPHICS_DIR / "feature-graphic-1024x500.png", "PNG")
    print("Wrote graphics/feature-graphic-1024x500.png")


# ---------------------------------------------------------------------------
# Screenshot mockups (1080x1920 — 9:16 phone screenshots)
# ---------------------------------------------------------------------------
CANVAS_W, CANVAS_H = 1080, 1920
CAPTION_H = 300
PHONE_MARGIN_X = 90
PHONE_TOP = CAPTION_H + 60
PHONE_BOTTOM = CANVAS_H - 60
BEZEL = 22
STATUS_BAR_H = 56
APP_BAR_H = 140
BOTTOM_NAV_H = 130


def new_screenshot_canvas(caption_lines: list[str]) -> tuple[Image.Image, ImageDraw.ImageDraw, tuple[int, int, int, int]]:
    img = Image.new("RGB", (CANVAS_W, CANVAS_H), BRAND_PRIMARY)
    draw = ImageDraw.Draw(img)

    # Caption band
    for y in range(CAPTION_H):
        t = y / CAPTION_H
        r = int(BRAND_PRIMARY[0] * (1 - t) + BRAND_PRIMARY_DARK[0] * t)
        g = int(BRAND_PRIMARY[1] * (1 - t) + BRAND_PRIMARY_DARK[1] * t)
        b = int(BRAND_PRIMARY[2] * (1 - t) + BRAND_PRIMARY_DARK[2] * t)
        draw.line([(0, y), (CANVAS_W, y)], fill=(r, g, b))

    caption_font = bold(58)
    total_h = len(caption_lines) * 70
    y = (CAPTION_H - total_h) / 2 + 10
    for line in caption_lines:
        draw_centered_text(draw, CANVAS_W / 2, y, line, caption_font, WHITE)
        y += 70

    # Background below the caption
    draw.rectangle((0, CAPTION_H, CANVAS_W, CANVAS_H), fill=BRAND_PRIMARY_CONTAINER)

    # Phone frame
    frame_box = (PHONE_MARGIN_X, PHONE_TOP, CANVAS_W - PHONE_MARGIN_X, PHONE_BOTTOM)
    draw.rounded_rectangle(frame_box, radius=64, fill=PHONE_BEZEL)
    screen_box = (
        frame_box[0] + BEZEL,
        frame_box[1] + BEZEL,
        frame_box[2] - BEZEL,
        frame_box[3] - BEZEL,
    )
    draw.rounded_rectangle(screen_box, radius=48, fill=BRAND_SURFACE)

    return img, draw, screen_box


def draw_status_bar(draw: ImageDraw.ImageDraw, screen_box: tuple[int, int, int, int]) -> None:
    x0, y0, x1, _ = screen_box
    f = bold(26)
    draw.text((x0 + 34, y0 + 14), "9:41", font=f, fill=BRAND_ON_SURFACE)
    draw_centered_text(draw, (x0 + x1) / 2, y0 + 14, "", f, BRAND_ON_SURFACE)
    # signal / wifi / battery glyphs (simplified)
    for i in range(4):
        bar_h = 8 + i * 5
        draw.rectangle(
            (x1 - 150 + i * 14, y0 + 34 - bar_h, x1 - 150 + i * 14 + 8, y0 + 34),
            fill=BRAND_ON_SURFACE,
        )
    draw.rounded_rectangle((x1 - 80, y0 + 16, x1 - 34, y0 + 34), radius=4, outline=BRAND_ON_SURFACE, width=3)
    draw.rectangle((x1 - 76, y0 + 19, x1 - 60, y0 + 31), fill=BRAND_ON_SURFACE)


def draw_app_bar(
    draw: ImageDraw.ImageDraw,
    screen_box: tuple[int, int, int, int],
    title: str,
    icons: int = 2,
) -> int:
    x0, y0, x1, _ = screen_box
    top = y0 + STATUS_BAR_H
    draw.rectangle((x0, top, x1, top + APP_BAR_H), fill=BRAND_SURFACE)
    f = bold(38)
    draw.text((x0 + 36, top + APP_BAR_H / 2 - 22), title, font=f, fill=BRAND_ON_SURFACE)
    # icon buttons (simple circles) top-right
    r = 26
    cy = top + APP_BAR_H / 2
    for i in range(icons):
        cx = x1 - 50 - i * 80
        draw.ellipse((cx - r, cy - r, cx + r, cy + r), outline=BRAND_ON_SURFACE_VARIANT, width=4)
    draw.line((x0, top + APP_BAR_H, x1, top + APP_BAR_H), fill=BRAND_OUTLINE, width=3)
    return top + APP_BAR_H


def draw_bottom_nav(draw: ImageDraw.ImageDraw, screen_box: tuple[int, int, int, int], active: int) -> int:
    x0, y0, x1, y1 = screen_box
    top = y1 - BOTTOM_NAV_H
    draw.rectangle((x0, top, x1, y1), fill=WHITE)
    draw.line((x0, top, x1, top), fill=BRAND_OUTLINE, width=3)
    # Matches MainTab's real order: Backup is the first tab and the app's
    # landing screen (the declared "Backup and restore" Play use case).
    labels = ["Backup", "Redirect", "Schedule"]
    seg_w = (x1 - x0) / len(labels)
    f = bold(28)
    for i, label in enumerate(labels):
        cx = x0 + seg_w * i + seg_w / 2
        color = BRAND_PRIMARY if i == active else BRAND_ON_SURFACE_VARIANT
        r = 18
        draw.ellipse((cx - r, top + 26 - r, cx + r, top + 26 + r), fill=color)
        draw_centered_text(draw, cx, top + 56, label, f, color)
    return top


def draw_fab(draw: ImageDraw.ImageDraw, screen_box: tuple[int, int, int, int], above_y: int) -> None:
    _, _, x1, _ = screen_box
    r = 54
    cx, cy = x1 - 90, above_y - 90
    draw.ellipse((cx - r, cy - r, cx + r, cy + r), fill=BRAND_PRIMARY)
    f = bold(52)
    draw_centered_text(draw, cx, cy - 30, "+", f, WHITE)


def draw_category_header(draw: ImageDraw.ImageDraw, x0: int, x1: int, y: int, label: str, count: str) -> int:
    f = bold(30)
    fc = regular(26)
    draw.text((x0 + 36, y), label, font=f, fill=BRAND_ON_SURFACE)
    count_w = text_width(draw, count, fc)
    draw.text((x1 - 36 - count_w, y + 4), count, font=fc, fill=BRAND_ON_SURFACE_VARIANT)
    return y + 56


def draw_card(
    draw: ImageDraw.ImageDraw,
    x0: int,
    x1: int,
    y: int,
    height: int,
    accent: tuple[int, int, int],
    title: str,
    line1: str,
    line2: str,
) -> int:
    box = (x0 + 24, y, x1 - 24, y + height)
    draw.rounded_rectangle(box, radius=22, fill=WHITE, outline=BRAND_OUTLINE, width=2)
    draw.rounded_rectangle((box[0] + 18, box[1] + 16, box[0] + 26, box[3] - 16), radius=4, fill=accent)
    tf = bold(32)
    lf = regular(26)
    draw.text((box[0] + 48, box[1] + 18), title, font=tf, fill=BRAND_ON_SURFACE)
    draw.text((box[0] + 48, box[1] + 62), line1, font=lf, fill=BRAND_ON_SURFACE_VARIANT)
    draw.text((box[0] + 48, box[1] + 98), line2, font=lf, fill=BRAND_ON_SURFACE_VARIANT)
    # drag handle dots, right edge
    dx = box[2] - 34
    for i in range(3):
        dy = box[1] + height / 2 - 20 + i * 18
        draw.ellipse((dx - 4, dy - 4, dx + 4, dy + 4), fill=BRAND_ON_SURFACE_VARIANT)
    return y + height + 20


def draw_enabled_banner(draw: ImageDraw.ImageDraw, x0: int, x1: int, y: int, label: str) -> int:
    """A filled green banner with a checkmark — the "grey -> green" enabled
    state of the real Backup screen's "Enable Backup to Gmail" button."""
    box = (x0 + 36, y, x1 - 36, y + 110)
    draw.rounded_rectangle(box, radius=20, fill=BRAND_SECONDARY)
    r = 22
    cx, cy = box[0] + 56, (box[1] + box[3]) / 2
    draw.ellipse((cx - r, cy - r, cx + r, cy + r), fill=WHITE)
    draw.line((cx - 11, cy, cx - 2, cy + 11), fill=BRAND_SECONDARY, width=7)
    draw.line((cx - 2, cy + 11, cx + 15, cy - 13), fill=BRAND_SECONDARY, width=7)
    f = bold(34)
    draw.text((cx + 46, cy - 20), label, font=f, fill=WHITE)
    return box[3] + 30


def screenshot_backup() -> Image.Image:
    img, draw, screen_box = new_screenshot_canvas(["Back up your SMS", "safely to your Gmail"])
    x0, y0, x1, y1 = screen_box
    draw_status_bar(draw, screen_box)
    content_top = draw_app_bar(draw, screen_box, "Backup to Gmail", icons=1)
    # Backup has no "create" action, so the real UI hides the FAB on this
    # tab — the mockup does the same.
    draw_bottom_nav(draw, screen_box, active=0)

    y = content_top + 30
    y = draw_enabled_banner(draw, x0, x1, y, "Backup to Gmail enabled")
    y = draw_form_field(draw, x0, x1, y, "Gmail label", "SMS")
    y = draw_form_field(draw, x0, x1, y, "Email subject", "SMS with $CONTACT_NAME ($PHONE_NUMBER)")
    y += 6
    y = draw_toggle_row(draw, x0, x1, y, "Backup all previous SMS", False)
    y += 14

    box = (x0 + 24, y, x1 - 24, y + 150)
    draw.rounded_rectangle(box, radius=22, fill=WHITE, outline=BRAND_OUTLINE, width=2)
    f1 = bold(30)
    f2 = regular(24)
    draw.text((box[0] + 30, box[1] + 22), "3,214 messages backed up", font=f1, fill=BRAND_ON_SURFACE)
    draw.text((box[0] + 30, box[1] + 68), "Filed under the “SMS” Gmail label", font=f2, fill=BRAND_ON_SURFACE_VARIANT)
    draw.text(
        (box[0] + 30, box[1] + 104),
        "Keeps going as you send and receive texts",
        font=f2,
        fill=BRAND_ON_SURFACE_VARIANT,
    )

    SCREENSHOTS_DIR.mkdir(parents=True, exist_ok=True)
    img.save(SCREENSHOTS_DIR / "01-backup.png", "PNG")
    print("Wrote screenshots/phone/01-backup.png")
    return img


def screenshot_restore() -> Image.Image:
    """The Restore card on the Backup tab, mid-run. Copy from strings.xml
    (restore_title, restore_description, restore_progress, restore_running_hint)."""
    img, draw, screen_box = new_screenshot_canvas(["Restore your texts", "to a new phone"])
    x0, y0, x1, y1 = screen_box
    draw_status_bar(draw, screen_box)
    content_top = draw_app_bar(draw, screen_box, "Backup to Gmail", icons=1)
    draw_bottom_nav(draw, screen_box, active=0)

    y = content_top + 30
    y = draw_enabled_banner(draw, x0, x1, y, "Backup to Gmail enabled")

    pad = 30
    box_x0, box_x1 = x0 + 24, x1 - 24
    inner_w = box_x1 - box_x0 - 2 * pad
    f_title = bold(32)
    f_body = regular(24)
    f_prog = bold(28)
    description = (
        "Copy the messages backed up under the Gmail label “SMS” back to this phone. "
        "Messages already on the phone are skipped, so it is safe to run more than once."
    )
    desc_lines = wrap_text(draw, description, f_body, inner_w)
    hint_lines = wrap_text(draw, "You can leave the app — the restore continues in the background.", f_body, inner_w)
    height = 22 + 50 + len(desc_lines) * 34 + 30 + 44 + 34 + 40 + len(hint_lines) * 34 + 24
    draw.rounded_rectangle((box_x0, y, box_x1, y + height), radius=22, fill=WHITE, outline=BRAND_OUTLINE, width=2)
    ty = y + 22
    draw.text((box_x0 + pad, ty), "Restore from Gmail", font=f_title, fill=BRAND_ON_SURFACE)
    ty += 50
    for line in desc_lines:
        draw.text((box_x0 + pad, ty), line, font=f_body, fill=BRAND_ON_SURFACE_VARIANT)
        ty += 34
    ty += 30
    draw.text((box_x0 + pad, ty), "Checking 2,180 of 3,214", font=f_prog, fill=BRAND_ON_SURFACE)
    ty += 44
    bar = (box_x0 + pad, ty, box_x1 - pad, ty + 12)
    draw.rounded_rectangle(bar, radius=6, fill=BRAND_PRIMARY_CONTAINER)
    draw.rounded_rectangle((bar[0], bar[1], bar[0] + (bar[2] - bar[0]) * 2180 / 3214, bar[3]), radius=6, fill=BRAND_PRIMARY)
    ty += 34
    draw.text((box_x0 + pad, ty), "2,176 messages restored so far", font=f_body, fill=BRAND_ON_SURFACE_VARIANT)
    ty += 40
    for line in hint_lines:
        draw.text((box_x0 + pad, ty), line, font=f_body, fill=BRAND_ON_SURFACE_VARIANT)
        ty += 34

    SCREENSHOTS_DIR.mkdir(parents=True, exist_ok=True)
    img.save(SCREENSHOTS_DIR / "08-restore.png", "PNG")
    print("Wrote screenshots/phone/08-restore.png")
    return img


def screenshot_redirect_home() -> Image.Image:
    img, draw, screen_box = new_screenshot_canvas(["Forward SMS", "by simple rules"])
    x0, y0, x1, y1 = screen_box
    draw_status_bar(draw, screen_box)
    content_top = draw_app_bar(draw, screen_box, "Redirect Rules")
    nav_top = draw_bottom_nav(draw, screen_box, active=1)

    y = content_top + 30
    y = draw_category_header(draw, x0, x1, y, "Work", "2 rules")
    y = draw_card(draw, x0, x1, y, 150, CATEGORY_ACCENTS[0], "Work OTP", "Sender: WorkBank", "Target: +351 912 345 678")
    y = draw_card(draw, x0, x1, y, 150, CATEGORY_ACCENTS[0], "Delivery codes", "Sender: contains \"DHL\"", "Target: +351 912 345 678")
    y += 20
    y = draw_category_header(draw, x0, x1, y, "Family", "1 rule")
    y = draw_card(draw, x0, x1, y, 150, CATEGORY_ACCENTS[1], "Mom's phone", "Sender: Mom", "Target: +351 933 111 222")

    draw_fab(draw, screen_box, nav_top)
    SCREENSHOTS_DIR.mkdir(parents=True, exist_ok=True)
    img.save(SCREENSHOTS_DIR / "02-redirect-rules.png", "PNG")
    print("Wrote screenshots/phone/02-redirect-rules.png")
    return img


def draw_form_field(draw: ImageDraw.ImageDraw, x0: int, x1: int, y: int, label: str, value: str, with_icon: bool = False) -> int:
    lf = regular(24)
    draw.text((x0 + 36, y), label, font=lf, fill=BRAND_ON_SURFACE_VARIANT)
    box = (x0 + 36, y + 34, x1 - 36, y + 100)
    draw.rounded_rectangle(box, radius=14, outline=BRAND_PRIMARY, width=3)
    vf = regular(30)
    draw.text((box[0] + 20, box[1] + 18), value, font=vf, fill=BRAND_ON_SURFACE)
    if with_icon:
        r = 18
        cx, cy = box[2] - 34, (box[1] + box[3]) / 2
        draw.ellipse((cx - r, cy - r, cx + r, cy + r), outline=BRAND_PRIMARY, width=3)
    return y + 130


def screenshot_create_rule() -> Image.Image:
    img, draw, screen_box = new_screenshot_canvas(["Pick a sender,", "pick a target"])
    x0, y0, x1, y1 = screen_box
    draw_status_bar(draw, screen_box)
    content_top = draw_app_bar(draw, screen_box, "Create Rule", icons=1)

    y = content_top + 30
    y = draw_form_field(draw, x0, x1, y, "Rule Name", "Work OTP")
    y = draw_form_field(draw, x0, x1, y, "Sender", "WorkBank", with_icon=True)
    y = draw_form_field(draw, x0, x1, y, "Target Number", "+351 912 345 678", with_icon=True)
    y = draw_form_field(draw, x0, x1, y, "Category", "Work")

    btn_box = (x0 + 36, y + 30, x1 - 36, y + 110)
    draw.rounded_rectangle(btn_box, radius=16, fill=BRAND_PRIMARY)
    bf = bold(32)
    draw_centered_text(draw, (btn_box[0] + btn_box[2]) / 2, btn_box[1] + 24, "Save Rule", bf, WHITE)

    img.save(SCREENSHOTS_DIR / "03-create-rule.png", "PNG")
    print("Wrote screenshots/phone/03-create-rule.png")
    return img


def screenshot_schedule_list() -> Image.Image:
    img, draw, screen_box = new_screenshot_canvas(["Schedule texts", "to send later"])
    x0, y0, x1, y1 = screen_box
    draw_status_bar(draw, screen_box)
    content_top = draw_app_bar(draw, screen_box, "Scheduled SMS")
    nav_top = draw_bottom_nav(draw, screen_box, active=2)

    y = content_top + 30
    y = draw_category_header(draw, x0, x1, y, "Reminders", "2 items")
    y = draw_card(draw, x0, x1, y, 150, CATEGORY_ACCENTS[2], "Pay rent", "Next send: 1 Aug, 09:00", "Repeat: Monthly")
    y = draw_card(draw, x0, x1, y, 150, CATEGORY_ACCENTS[2], "Water the plants", "Next send: 14 Jul, 18:00", "Repeat: Work week days")
    y += 20
    y = draw_category_header(draw, x0, x1, y, "Birthdays", "1 item")
    y = draw_card(draw, x0, x1, y, 150, CATEGORY_ACCENTS[4], "Happy birthday, Ana!", "Next send: 3 Sep, 08:00", "Repeat: Yearly")

    draw_fab(draw, screen_box, nav_top)
    img.save(SCREENSHOTS_DIR / "04-schedule-list.png", "PNG")
    print("Wrote screenshots/phone/04-schedule-list.png")
    return img


def screenshot_schedule_form() -> Image.Image:
    img, draw, screen_box = new_screenshot_canvas(["Repeats daily, weekly,", "monthly or yearly"])
    x0, y0, x1, y1 = screen_box
    draw_status_bar(draw, screen_box)
    content_top = draw_app_bar(draw, screen_box, "Create Schedule SMS", icons=1)

    y = content_top + 26
    y = draw_form_field(draw, x0, x1, y, "Contact", "Ana Silva", with_icon=True)
    y = draw_form_field(draw, x0, x1, y, "Message", "Happy birthday, Ana!")
    y = draw_form_field(draw, x0, x1, y, "Date", "3 September 2026")
    y = draw_form_field(draw, x0, x1, y, "Repeat", "Yearly")

    btn_box = (x0 + 36, y + 20, x1 - 36, y + 100)
    draw.rounded_rectangle(btn_box, radius=16, fill=BRAND_PRIMARY)
    bf = bold(30)
    draw_centered_text(draw, (btn_box[0] + btn_box[2]) / 2, btn_box[1] + 24, "Save Scheduled SMS", bf, WHITE)

    img.save(SCREENSHOTS_DIR / "05-schedule-form.png", "PNG")
    print("Wrote screenshots/phone/05-schedule-form.png")
    return img


def draw_toggle_row(draw: ImageDraw.ImageDraw, x0: int, x1: int, y: int, label: str, on: bool) -> int:
    f = regular(30)
    draw.text((x0 + 36, y), label, font=f, fill=BRAND_ON_SURFACE)
    track = (x1 - 130, y - 8, x1 - 50, y + 40)
    draw.rounded_rectangle(track, radius=20, fill=BRAND_PRIMARY if on else BRAND_OUTLINE)
    knob_x = track[2] - 20 if on else track[0] + 20
    knob_y = (track[1] + track[3]) / 2
    draw.ellipse((knob_x - 16, knob_y - 16, knob_x + 16, knob_y + 16), fill=WHITE)
    draw.line((x0, y + 60, x1, y + 60), fill=BRAND_OUTLINE, width=2)
    return y + 100


def screenshot_settings() -> Image.Image:
    img, draw, screen_box = new_screenshot_canvas(["Your data stays", "on your device"])
    x0, y0, x1, y1 = screen_box
    draw_status_bar(draw, screen_box)
    content_top = draw_app_bar(draw, screen_box, "Settings", icons=1)

    y = content_top + 30
    y = draw_form_field(draw, x0, x1, y, "Landing screen", "SMS Backup")
    y += 10
    y = draw_toggle_row(draw, x0, x1, y, "Send while roaming", False)
    y = draw_toggle_row(draw, x0, x1, y, "Keep history", True)
    y = draw_toggle_row(draw, x0, x1, y, "Show notifications", True)
    y += 10
    y = draw_form_field(draw, x0, x1, y, "Forward message prefix", "SMS Toolbox:")
    y += 10
    btn_box = (x0 + 36, y, (x0 + x1) / 2 - 12, y + 90)
    draw.rounded_rectangle(btn_box, radius=16, outline=BRAND_PRIMARY, width=3)
    bf = bold(26)
    draw_centered_text(draw, (btn_box[0] + btn_box[2]) / 2, btn_box[1] + 28, "Export Config", bf, BRAND_PRIMARY)
    btn_box2 = ((x0 + x1) / 2 + 12, y, x1 - 36, y + 90)
    draw.rounded_rectangle(btn_box2, radius=16, outline=BRAND_PRIMARY, width=3)
    draw_centered_text(draw, (btn_box2[0] + btn_box2[2]) / 2, btn_box2[1] + 28, "Import Config", bf, BRAND_PRIMARY)

    img.save(SCREENSHOTS_DIR / "06-settings.png", "PNG")
    print("Wrote screenshots/phone/06-settings.png")
    return img


def screenshot_history() -> Image.Image:
    img, draw, screen_box = new_screenshot_canvas(["Full history of every", "forwarded message"])
    x0, y0, x1, y1 = screen_box
    draw_status_bar(draw, screen_box)
    content_top = draw_app_bar(draw, screen_box, "Redirect History", icons=1)

    rows = [
        ("WorkBank \u2192 +351 912 345 678", "Rule: Work OTP", "Today, 14:32"),
        ("DHL \u2192 +351 912 345 678", "Rule: Delivery codes", "Today, 11:05"),
        ("Mom \u2192 +351 933 111 222", "Rule: Mom's phone", "Yesterday, 20:11"),
        ("WorkBank \u2192 +351 912 345 678", "Rule: Work OTP", "Yesterday, 09:47"),
    ]
    y = content_top + 30
    for sender_line, rule_line, time_line in rows:
        box = (x0 + 24, y, x1 - 24, y + 160)
        draw.rounded_rectangle(box, radius=22, fill=WHITE, outline=BRAND_OUTLINE, width=2)
        f1 = bold(28)
        f2 = regular(24)
        draw.text((box[0] + 30, box[1] + 18), sender_line, font=f1, fill=BRAND_ON_SURFACE)
        draw.text((box[0] + 30, box[1] + 60), rule_line, font=f2, fill=BRAND_ON_SURFACE_VARIANT)
        draw.text((box[0] + 30, box[1] + 100), time_line, font=f2, fill=BRAND_ON_SURFACE_VARIANT)
        y += 180

    img.save(SCREENSHOTS_DIR / "07-history.png", "PNG")
    print("Wrote screenshots/phone/07-history.png")
    return img


# ---------------------------------------------------------------------------
# Tablet screenshots (7-inch & 10-inch "large screens" listings).
#
# The app has no dedicated tablet layout, so rather than stretching the phone
# UI to fill a huge canvas (which would misrepresent the app), each tablet
# image reuses the *real* rendered phone screen — cropped out of the already
# generated phone mockup — and recomposes it into a landscape 16:9 graphic
# with a large headline, echoing the feature graphic's layout. This stays
# honest about what the UI looks like while meeting Play's current "Large
# screens" guidance (16:9 landscape / 9:16 portrait, minimum 4 screenshots,
# each dimension between 1080 and 7680px).
# ---------------------------------------------------------------------------
PHONE_DEVICE_CROP = (PHONE_MARGIN_X, PHONE_TOP, CANVAS_W - PHONE_MARGIN_X, PHONE_BOTTOM)

TABLET_PROFILES = [
    (SEVEN_INCH_DIR, "sevenInchTablet", 1920, 1080),
    (TEN_INCH_DIR, "tenInchTablet", 2560, 1440),
]


def generate_tablet_screenshot(
    out_dir: Path,
    out_w: int,
    out_h: int,
    filename: str,
    phone_img: Image.Image,
    caption_lines: list[str],
) -> None:
    img = Image.new("RGB", (out_w, out_h), BRAND_PRIMARY)
    draw = ImageDraw.Draw(img)

    for y in range(out_h):
        t = y / out_h
        r = int(BRAND_PRIMARY[0] * (1 - t) + BRAND_PRIMARY_DARK[0] * t)
        g = int(BRAND_PRIMARY[1] * (1 - t) + BRAND_PRIMARY_DARK[1] * t)
        b = int(BRAND_PRIMARY[2] * (1 - t) + BRAND_PRIMARY_DARK[2] * t)
        draw.line([(0, y), (out_w, y)], fill=(r, g, b))

    # Decorative category-accent dots, top-right — echoes the feature graphic.
    dot_r = max(6, int(out_h * 0.011))
    dot_gap = dot_r * 3.4
    for i, color in enumerate(CATEGORY_ACCENTS):
        cx = out_w - out_h * 0.05 - (len(CATEGORY_ACCENTS) - i - 1) * dot_gap
        cy = out_h * 0.06
        draw.ellipse((cx - dot_r, cy - dot_r, cx + dot_r, cy + dot_r), fill=color)

    # Real phone screen, cropped out of the phone mockup (drops the caption
    # band, keeps the bezel + a light "card" margin from its background).
    device = phone_img.crop(PHONE_DEVICE_CROP)
    device_h = int(out_h * 0.88)
    device_w = int(device.width * device_h / device.height)
    device = device.resize((device_w, device_h), Image.LANCZOS)
    device_x = out_w - device_w - int(out_w * 0.06)
    device_y = (out_h - device_h) // 2
    img.paste(device, (device_x, device_y))

    # Headline, left-aligned, sized to the available width before the device.
    text_x = int(out_w * 0.06)
    max_text_w = device_x - text_x - int(out_w * 0.03)
    title_font = bold(int(out_h * 0.09))
    line_h = int(out_h * 0.11)
    total_h = len(caption_lines) * line_h
    title_y = out_h / 2 - total_h / 2
    for line in caption_lines:
        for wrapped in wrap_text(draw, line, title_font, max_text_w):
            draw.text((text_x, title_y), wrapped, font=title_font, fill=WHITE)
            title_y += line_h

    # Small brand lockup, bottom-left.
    tagline_font = regular(int(out_h * 0.035))
    draw.text(
        (text_x, out_h * 0.86),
        "SMS Toolbox",
        font=tagline_font,
        fill=BRAND_PRIMARY_CONTAINER,
    )

    out_dir.mkdir(parents=True, exist_ok=True)
    img.save(out_dir / filename, "PNG")
    print(f"Wrote screenshots/{out_dir.name}/{filename}")


def generate_tablet_screenshots(phone_shots: list[tuple[str, list[str], Image.Image]]) -> None:
    for out_dir, _label, out_w, out_h in TABLET_PROFILES:
        for filename, caption_lines, phone_img in phone_shots:
            generate_tablet_screenshot(out_dir, out_w, out_h, filename, phone_img, caption_lines)


def main() -> None:
    generate_icon()
    generate_feature_graphic()
    phone_shots = [
        ("01-backup.png", ["Back up your SMS", "safely to your Gmail"], screenshot_backup()),
        ("02-redirect-rules.png", ["Forward SMS", "by simple rules"], screenshot_redirect_home()),
        ("03-create-rule.png", ["Pick a sender,", "pick a target"], screenshot_create_rule()),
        ("04-schedule-list.png", ["Schedule texts", "to send later"], screenshot_schedule_list()),
        ("05-schedule-form.png", ["Repeats daily, weekly,", "monthly or yearly"], screenshot_schedule_form()),
        ("06-settings.png", ["Your data stays", "on your device"], screenshot_settings()),
        ("07-history.png", ["Full history of every", "forwarded message"], screenshot_history()),
        ("08-restore.png", ["Restore your texts", "to a new phone"], screenshot_restore()),
    ]
    generate_tablet_screenshots(phone_shots)


if __name__ == "__main__":
    main()
