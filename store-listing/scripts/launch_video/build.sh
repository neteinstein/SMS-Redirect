#!/usr/bin/env bash
# Rebuilds store-listing/video/sms-toolbox-launch.{mp4,jpg}.
# Needs: Node + Playwright with Chromium, Python 3 + numpy, ffmpeg (libx264), and the Inter font
# (INTER_DIR, default /usr/share/fonts/opentype/inter).
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
out="$here/../../video"
inter="${INTER_DIR:-/usr/share/fonts/opentype/inter}"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

cp "$here"/composition.html "$here"/render.js "$here"/music.py "$work"/
mkdir "$work/fonts"
for f in Inter-Regular Inter-Medium Inter-SemiBold Inter-Bold InterDisplay-Bold InterDisplay-ExtraBold; do
  cp "$inter/$f.otf" "$work/fonts/"
done

cd "$work"
python3 music.py
node render.js
node render.js --poster 23
# The poster replaces frame 0 (not an extra frame), so duration and audio sync are unchanged.
ffmpeg -loglevel error -y -i video_silent.mp4 -i poster.png -i music.wav \
  -filter_complex "[0:v][1:v]overlay=enable='eq(n,0)'[v]" -map "[v]" -map 2:a \
  -c:v libx264 -preset slow -crf 16 -pix_fmt yuv420p -c:a aac -b:a 192k -movflags +faststart -shortest \
  "$out/sms-toolbox-launch.mp4"
ffmpeg -loglevel error -y -i poster.png -q:v 2 "$out/sms-toolbox-launch.jpg"
echo "Wrote $out/sms-toolbox-launch.mp4 and .jpg"
