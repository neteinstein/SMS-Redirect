#!/usr/bin/env python3
"""Pillow drawing helpers for the permissions demo video (generate_demo_video.py).

They draw flat, concept-style phone screens from the app's brand colors and UI
strings. The Play Store icon, feature graphic and screenshots no longer come from
here: they are rendered from HTML by store_assets/build.sh, which rebuilds the real
Material 3 screens with Roboto and the official Material Icons.

Requires a TrueType font. Tries a short list of common system fonts and
falls back to Pillow's default bitmap font (lower quality) if none are found.
"""

from __future__ import annotations

import os

from PIL import Image, ImageDraw, ImageFont


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
# App icon (the demo video's title card)
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
