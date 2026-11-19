#!/usr/bin/env bash
# Rebuilds store-listing/video/sms-toolbox-navigation.{mp4,jpg}: the "how to navigate the app"
# walkthrough for Play Console's App access video link.
# Needs Node + Playwright with Chromium, and ffmpeg (libx264). Fonts and icons come from ../store_assets.
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
out="$here/../../video"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
cd "$work"
node "$here/render.js"
node "$here/render.js" --still 6 poster.png
# Silent stereo AAC track: some players and upload checks expect an audio stream.
ffmpeg -loglevel error -y -i video_silent.mp4 -f lavfi -i anullsrc=r=48000:cl=stereo \
  -map 0:v -map 1:a -c:v copy -c:a aac -b:a 64k -shortest -movflags +faststart \
  "$out/sms-toolbox-navigation.mp4"
ffmpeg -loglevel error -y -i poster.png -q:v 2 "$out/sms-toolbox-navigation.jpg"
echo "Wrote $out/sms-toolbox-navigation.mp4 and .jpg"
