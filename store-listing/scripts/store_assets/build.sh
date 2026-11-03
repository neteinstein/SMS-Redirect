#!/usr/bin/env bash
# Rebuilds every Play Store graphic: graphics/icon-512.png, graphics/feature-graphic-1024x500.png
# and screenshots/{phone,sevenInchTablet,tenInchTablet}/*.png.
# Needs Node + Playwright with Chromium, and Python 3 + Pillow. Fonts and icons are bundled here.
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
cd "$here"
node render.js "$@"
# Play asks for a 32-bit PNG (with alpha) for the hi-res icon, and 24-bit PNGs without
# alpha for screenshots and the feature graphic. Chromium writes RGB, so only the icon changes.
python3 - <<'PY'
from PIL import Image
p = "../../graphics/icon-512.png"
Image.open(p).convert("RGBA").save(p)
PY
