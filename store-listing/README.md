# Play Store listing assets

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
│   ├── phone/                        7 captioned 1080x1920 screenshots
│   ├── sevenInchTablet/              same 7 moments, 1920x1080 "large screen" layout
│   └── tenInchTablet/                same 7 moments, 2560x1440 "large screen" layout
├── scripts/generate_assets.py        regenerates everything under graphics/ + screenshots/
└── data-safety.md                    cheat-sheet for the Data safety questionnaire
```

## Copy-pasting the text fields

| Play Console field | File | Limit | Current length |
|---|---|---|---|
| App name | `text/<locale>/title.txt` | 30 chars | 11 (en-US) / 11 (pt-PT) |
| Short description | `text/<locale>/short_description.txt` | 80 chars | 72 (en-US) / 77 (pt-PT) |
| Full description | `text/<locale>/full_description.txt` | 4000 chars | 3787 (en-US) / 3931 (pt-PT) |
| What's new (this release) | `text/<locale>/release_notes.txt` | 500 chars | 355 (en-US) / 381 (pt-PT) |

Paste each file's contents verbatim into the matching field for that locale.
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

`graphics/icon-512.png` and `graphics/feature-graphic-1024x500.png` are ready
to upload as-is — the icon is a faithful high-res recreation of the real
launcher icon (`app/src/main/res/drawable/ic_launcher_*.xml`: brand blue
background, white chat bubble, forward chevrons), and the feature graphic
reuses it with the app name and tagline.

`screenshots/phone/*.png` are **concept mockups**, not raw device captures —
generated from the app's real brand colors and strings (not from a running
emulator, since none was available in this environment) to give the listing
a strong, on-brand starting point:

1. `01-backup.png` — Backup tab, enabled and running (the app's landing screen)
2. `02-redirect-rules.png` — Redirect rule list, grouped by category
3. `03-create-rule.png` — Create Rule form
4. `04-schedule-list.png` — Scheduled SMS list
5. `05-schedule-form.png` — Create Schedule SMS form
6. `06-settings.png` — Settings (Gmail account, landing screen, privacy-forward toggles)
7. `07-history.png` — Redirect history

They already satisfy Play's technical requirements (1080x1920, 24-bit PNG, no
alpha) and can be uploaded as-is. For pixel-perfect real captures instead,
run the app on a device/emulator and use Android Studio's **Take Screenshot**
action or:

```bash
adb exec-out screencap -p > screenshot.png
```
then recreate the same 7 moments, optionally reusing the caption banners from
`scripts/generate_assets.py` (`new_screenshot_canvas`) as an overlay.

### Tablet ("large screens") screenshots

`screenshots/sevenInchTablet/*.png` (1920x1080) and `screenshots/tenInchTablet/*.png`
(2560x1440) cover Play Console's **7-inch tablet** and **10-inch tablet**
upload slots (grouped under "Large screens" in the current Console UI, which
also covers Chromebooks). The app has no dedicated tablet layout, so instead
of stretching the phone UI across a much bigger canvas, each image reuses the
*same rendered phone screen* from `screenshots/phone/`, recomposed into a
landscape 16:9 promotional frame with a large headline — the same visual
language as the feature graphic, and an honest representation of what the UI
actually looks like on a bigger screen.

They meet Play's current requirements for large-screen screenshots: 16:9
landscape, both dimensions between 1080 and 7680px, 24-bit PNG with no alpha,
and 7 images each (Play recommends a minimum of 4, allows up to 8). Uploading
them is optional — Play only requires phone screenshots to publish — but
recommended if the app is listed as tablet-compatible, since it isn't
restricted to phones (no `<supports-screens>` or `<compatible-screens>`
restriction in `AndroidManifest.xml`).

To tweak colors/copy and regenerate every graphic (phone + both tablet sizes):

```bash
pip install pillow
python3 store-listing/scripts/generate_assets.py
```

## Suggested Play Console fields not covered by a file

- **App category:** Tools (Communication is a reasonable alternate)
- **Tags:** SMS, forwarding, automation, productivity
- **Content rating:** Everyone (run the questionnaire — no user-generated
  content, no ads, no unrestricted web access)
- **Contact email:** `play-store-neteinstein@googlegroups.com` (from
  [PrivacyPolicy.md](../PrivacyPolicy.md))
- **Privacy Policy URL:**
  `https://github.com/neteinstein/SMS-Redirect/blob/main/PrivacyPolicy.md`
  (already documented in [PublishToStore.md](../PublishToStore.md))
- **Website:** `https://github.com/neteinstein/SMS-Redirect`

## ⚠️ Before this can go live: the SMS permissions declaration

This app requests `READ_SMS` / `RECEIVE_SMS` / `SEND_SMS`. Play Console will
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
- `SEND_SMS` is **not** one of the declaration-gated permissions — that set is
  `READ_SMS`, `RECEIVE_SMS`, `RECEIVE_WAP_PUSH`, `RECEIVE_MMS` plus the call-log
  ones — so keeping Redirect and Schedule alongside backup should be compatible.
  Confirm on the form itself; policy wording shifts.
- `READ_SMS` was declared but never actually used before this feature. It is now
  genuinely exercised by the backup scan, so the declared use and the observed
  behaviour finally agree — a mismatch between the two is one of the most common
  causes of SMS-app removal.
- **Resubmit the Data safety form in the same release.** Gmail backup transmits
  SMS content and an email address off-device; see [`data-safety.md`](data-safety.md).
- Ship the updated [`PrivacyPolicy.md`](../PrivacyPolicy.md) **before or with**
  this release. It is linked from the listing and its previous wording ("no
  backend server", "we do not collect your email address") is no longer accurate.

The `tools:ignore="SmsAndCallLogPolicy"` lint suppressions in `AndroidManifest.xml`
are a reminder that this review exists — they silence the lint warning, not
the Play Console policy review itself. Budget time for this before your first
production release; it's a manual review on Google's side and can take days.

## Data safety

See [`data-safety.md`](data-safety.md) for a field-by-field mapping of the
**App content > Data safety** questionnaire, derived from
[PrivacyPolicy.md](../PrivacyPolicy.md).
