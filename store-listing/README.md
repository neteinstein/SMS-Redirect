# Play Store listing assets

> **This listing is for the Play build only: Backup and Restore.** The app is built
> in two flavors (`app/build.gradle.kts`). The **play** flavor, uploaded to Google
> Play, has no Redirect or Schedule, does not declare `SEND_SMS`, and never updates
> itself. The **github** flavor, attached to each GitHub release, has every feature.
> Nothing in this folder should mention forwarding or scheduling.

Everything needed to fill in the **Main store listing** (and get a head start on
**App content > Data safety**) for `com.neteinstein.smsredirect` in the
[Google Play Console](https://play.google.com/console) lives in this folder.

## What's here

```
store-listing/
├── text/
│   ├── en-US/   title.txt, short_description.txt, full_description.txt, release_notes.txt
│   └── pt-PT/   same four files, in European Portuguese
├── graphics/
│   ├── icon-512.png                  512x512 hi-res icon
│   └── feature-graphic-1024x500.png  1024x500 feature graphic
├── screenshots/
│   ├── phone/                        3 captioned 1080x1920 screenshots
│   ├── sevenInchTablet/              same 3 moments, 1920x1080 "large screen" layout
│   └── tenInchTablet/                same 3 moments, 2560x1440 "large screen" layout
├── scripts/store_assets/build.sh     regenerates everything under graphics/ + screenshots/ (needs Playwright)
├── scripts/generate_demo_video.py    regenerates the permissions demo video (needs Pillow + ffmpeg)
├── scripts/launch_video/build.sh     regenerates the launch video (needs Playwright + ffmpeg)
├── video/                            launch video, permissions demo video + storyboard (see video/README.md)
├── data-safety.md                    cheat-sheet for the Data safety questionnaire
└── testing-instructions.md           App access instructions + internal-track QA checklist
```

## Copy-pasting the text fields

| Play Console field | File | Limit | Current length |
|---|---|---|---|
| App name | `text/<locale>/title.txt` | 30 chars | 29 (en-US) / 30 (pt-PT) |
| Short description | `text/<locale>/short_description.txt` | 80 chars | 79 (en-US) / 80 (pt-PT) |
| Full description | `text/<locale>/full_description.txt` | 4000 chars | 3067 (en-US) / 3395 (pt-PT) |
| What's new (this release) | `text/<locale>/release_notes.txt` | 500 chars | 359 (en-US) / 359 (pt-PT) |

Paste each file's contents verbatim into the matching field for that locale.
The store title is **"SMS Toolbox: Backup & Restore"** (pt-PT: "SMS Toolbox: Backup e Restauro"),
which leads with the declared core use case. The app's own name on the device stays
"SMS Toolbox"; Play allows the two to differ.
`pt-PT` matches the tone/wording already used in `app/src/main/res/values-pt/strings.xml`
(European Portuguese — "ecrã", "definições", not Brazilian Portuguese).

Re-check lengths after editing:

```bash
python3 -c "
import pathlib
limits = {'title.txt': 30, 'short_description.txt': 80, 'full_description.txt': 4000, 'release_notes.txt': 500}
for d in sorted(pathlib.Path('text').iterdir()):
    for name, limit in limits.items():
        n = len((d / name).read_text(encoding='utf-8').rstrip('\n'))
        print(f'{d.name}/{name}: {n}/{limit}', 'OK' if n <= limit else 'OVER!')
"
```

## Graphics & screenshots

Everything here is ready to upload as-is.

**Icon** (`graphics/icon-512.png`, 512x512, 32-bit PNG). The real launcher glyph
from `app/src/main/res/drawable/ic_launcher_foreground.xml` (the chat bubble and
forward chevrons, path for path) on a brand-blue gradient, full bleed. Play applies
its own rounded mask, so the corners are left square.

**Feature graphic** (`graphics/feature-graphic-1024x500.png`). The icon, the app
name and the backup-and-restore promise, next to two phones showing the real
Restore and Backup screens.

**Screenshots.** Each one pairs a headline with the real screen in a phone frame,
plus a few floating callouts (a notification, the Gmail label, a restore counter)
that show the result of the feature. Upload them in this order, so the listing
leads with the declared core use case, backup and restore:

| # | File | Headline | Screen |
|---|---|---|---|
| 1 | `01-backup.png` | Every text, safe in your own Gmail | Backup tab, running (the landing screen) |
| 2 | `02-restore.png` | New phone? Bring every text back | Restore from Gmail, mid-run |
| 3 | `03-privacy.png` | No account. No server. Just your Gmail. | Settings → Gmail backup, dark theme |

**How they are made.** `scripts/store_assets/store.html` rebuilds each screen in
HTML from the real Compose layouts (`app/src/main/java/.../presentation/`), the
real strings (`res/values/strings.xml`) and the brand colors (`theme/Color.kt`),
using Roboto, the app's font, and the official Material Icons. Both are bundled in
`scripts/store_assets/` with their licenses. `render.js` renders them with headless
Chromium. They are not device captures: no emulator was available where they were
made, and the surface colors follow Material You with a blue wallpaper. Every
name, number and message in them is fictional.

They meet Play's requirements:
- **Phone:** 1080x1920, 24-bit PNG, no alpha, 3 images (Play needs at least 2).
- **Tablets:** `screenshots/sevenInchTablet/` (1920x1080) and
  `screenshots/tenInchTablet/` (2560x1440) cover the **7-inch** and **10-inch
  tablet** slots, under "Large screens" in Play Console. They are 16:9 landscape,
  with both dimensions between 1080 and 7680px, 3 images each.

The app has no tablet layout, so the tablet images show the same phone screens
beside the headline rather than a stretched UI. Uploading them is optional (Play
only requires phone screenshots), but recommended: the app isn't restricted to
phones, since `AndroidManifest.xml` has no `<supports-screens>` or
`<compatible-screens>` restriction.

To change copy, layout or colors, edit `store.html`, then regenerate everything:

```bash
store-listing/scripts/store_assets/build.sh                   # all graphics and screenshots
store-listing/scripts/store_assets/build.sh --only 02-restore # one screenshot, all three sizes
```

`build.sh` needs Node with Playwright (Chromium), and Python 3 with Pillow.

## Promo video

> ⚠️ **Both videos predate the Backup-only Play build.** They still show the
> Redirect and Schedule tabs and the `SEND_SMS` grant, so do not link them from
> the Play listing or the permissions declaration until they are re-recorded or
> re-rendered without those scenes (see [`video/README.md`](video/README.md)).

`video/sms-toolbox-launch.mp4` is a 24-second launch video (1920x1080, 30 fps,
H.264 + AAC, with music) that walks through Backup, Restore, Redirect and
Schedule. Play Console's **Video** field on the main store listing takes a
YouTube URL, not a file: upload the MP4 to YouTube (public or unlisted, ads
off), then paste the link. `video/sms-toolbox-launch.jpg` is its poster frame.
See [`video/README.md`](video/README.md) for what it shows and how to rebuild it.

This is **not** the demonstration video for the permissions declaration form:
that one is `video/sms-toolbox-demo.mp4` and has to show the permission flow.

## Suggested Play Console fields not covered by a file

- **App category:** Tools (Communication is a reasonable alternate)
- **Tags:** SMS, backup, restore, Gmail
- **Content rating:** Everyone (run the questionnaire — no user-generated
  content, no ads, no unrestricted web access)
- **Contact email:** `play-store-neteinstein@googlegroups.com` (from
  [PrivacyPolicy.md](../PrivacyPolicy.md))
- **Privacy Policy URL:**
  `https://github.com/neteinstein/SMS-Redirect/blob/main/PrivacyPolicy.md`
  (already documented in [PublishToStore.md](../PublishToStore.md))
- **Website:** `https://github.com/neteinstein/SMS-Redirect`

## ⚠️ Before this can go live: the SMS permissions declaration

The Play build requests `READ_SMS` / `RECEIVE_SMS` (not `SEND_SMS`). Play Console will
block publishing until you complete **App content > Permissions declaration
form** and justify these under one of Google's allowed core-use categories.

**Declare "Backup and restore".** That is a permitted core use case on Google's
list, and it is the reason the app was reframed as SMS Toolbox with Gmail backup
as the first tab and the landing screen: the declaration has to match what the
app visibly is. A reviewer who opens the app must land on the backup feature.
An SMS-forwarding utility does not map cleanly to any permitted category, which
is what made the previous framing unpublishable.

Notes for the submission:
- The form asks for a **demonstration video**. Record the whole flow: Settings →
  enter Gmail address + app password → Save (it verifies against Gmail) → Backup
  tab → Enable → messages appearing under the Gmail label. Show that backup is
  the app's purpose, not a side feature.
- The Play build no longer declares `SEND_SMS` at all, and has no Redirect or
  Schedule features: everything a reviewer can find in it is backup and restore.
  `release.yml` fails the release if the Play bundle's merged manifest ever
  requests `SEND_SMS` or `REQUEST_INSTALL_PACKAGES`. While SMS Toolbox holds the
  default-SMS role during a restore, the quick-reply ("respond via message")
  service therefore cannot send; the user replies from their own app.
- `READ_SMS` was declared but never actually used before this feature. It is now
  genuinely exercised by the backup scan, so the declared use and the observed
  behaviour finally agree — a mismatch between the two is one of the most common
  causes of SMS-app removal.
- **Resubmit the Data safety form in the same release.** Gmail backup transmits
  SMS content and an email address off-device; see [`data-safety.md`](data-safety.md).
- Ship the updated [`PrivacyPolicy.md`](../PrivacyPolicy.md) **before or with**
  this release. It is linked from the listing and its previous wording ("no
  backend server", "we do not collect your email address") is no longer accurate.

### Restore makes the app a (temporary) default SMS handler

To restore messages, the app has to hold Android's SMS role while it writes to
the SMS store. So the manifest now declares the four components that make an
app eligible for the role: `SMS_DELIVER` and `WAP_PUSH_DELIVER` receivers, a
`SENDTO` activity and a `RESPOND_VIA_MESSAGE` service. Play's SMS policy treats
default-SMS-handler capability as its own category. Before submitting:

- Keep **"Backup and restore"** as the declared core use case. Restore is the
  second half of it, so it now does exactly what the declaration says. On the
  form, explain that the app asks for the default-SMS role only for the length
  of a user-started restore, and then sends the user back to their usual app.
- Add the restore to the demonstration video (see
  [`testing-instructions.md`](testing-instructions.md)): Backup tab → Restore
  messages → the system's default-SMS prompt → progress → "Switch back to your
  messaging app".
- Check the form for a separate "default SMS handler" question and answer it
  consistently. Policy wording shifts, so confirm on the live form.

The `tools:ignore="SmsAndCallLogPolicy"` lint suppressions in `AndroidManifest.xml`
are a reminder that this review exists — they silence the lint warning, not
the Play Console policy review itself. Budget time for this before your first
production release; it's a manual review on Google's side and can take days.

## Data safety

See [`data-safety.md`](data-safety.md) for a field-by-field mapping of the
**App content > Data safety** questionnaire, derived from
[PrivacyPolicy.md](../PrivacyPolicy.md).

## Testing on Google Play

See [`testing-instructions.md`](testing-instructions.md) for the **App
content > App access** reviewer instructions (including the demonstration
video needed for the permissions declaration), and a manual QA checklist to
run against the `internal` testing track before promoting a build further.
