# Play Store listing assets

> **This listing is for the Play build: an SMS/MMS messaging app** (Play core use
> case "Default SMS handler") with Redirect, Schedule, Backup and Restore. The app
> is built in two flavors (`app/build.gradle.kts`); the **play** flavor, uploaded to
> Google Play, and the **github** flavor, attached to each GitHub release, are
> identical except for the **Updates** section in Settings (GitHub only: Play forbids
> self-updates). Lead with messaging everywhere in this folder.

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
| App name | `text/<locale>/title.txt` | 30 chars | 26 (en-US) / 26 (pt-PT) |
| Short description | `text/<locale>/short_description.txt` | 80 chars | 77 (en-US) / 80 (pt-PT) |
| Full description | `text/<locale>/full_description.txt` | 4000 chars | 2693 (en-US) / 3124 (pt-PT) |
| What's new (this release) | `text/<locale>/release_notes.txt` | 500 chars | 328 (en-US) / 368 (pt-PT) |

Paste each file's contents verbatim into the matching field for that locale.
The store title is **"SMS Toolbox: Text Messages"** (pt-PT: "SMS Toolbox: Mensagens SMS"),
which leads with the declared core use case, SMS messaging. The app's own name on the device stays
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
name and the promise ("Your SMS & MMS app, with Gmail backup"), next to two phones
showing the Messages inbox and a conversation.

**Screenshots.** Each one pairs a headline with the real screen in a phone frame,
plus a few floating callouts (a message notification, the Gmail label, a restore
counter) that show the result of the feature. They lead with the declared core use
case, SMS messaging: the inbox and a conversation come first, then each tool in tab
order. Upload them in this order:

| # | File | Headline | Screen |
|---|---|---|---|
| 1 | `01-messages.png` | All your texts, one simple inbox | Messages tab (the landing screen) |
| 2 | `02-conversation.png` | Photos, groups and delivery reports | A conversation with a picture message (MMS) |
| 3 | `03-backup.png` | Every text, safe in your own Gmail | Backup tab, running |
| 4 | `04-restore.png` | New phone? Bring every text back | Restore tab, mid-run |
| 5 | `05-redirect.png` | Forward the texts that matter | Redirect tab, rules by category |
| 6 | `06-schedule.png` | Write it now, send it later | Schedule tab |
| 7 | `07-privacy.png` | No account. No server. Just your Gmail. | Settings → Gmail backup, dark theme |

Play shows up to 8 phone screenshots, so there is room for one more.

**How they are made.** `scripts/store_assets/store.html` rebuilds each screen in
HTML from the real Compose layouts (`app/src/main/java/.../presentation/`), the
real strings (`res/values/strings.xml`) and the brand colors (`theme/Color.kt`),
using Roboto, the app's font, and the official Material Icons. Both are bundled in
`scripts/store_assets/` with their licenses. `render.js` renders them with headless
Chromium. They are not device captures: no emulator was available where they were
made, and the surface colors follow Material You with a blue wallpaper. Every
name, number and message in them is fictional, and the photo in the picture
message is a drawn illustration. The bottom bar shows each tab as its icon:
`MainBottomBar` only labels the selected tab, and with five tabs and the FAB on a
phone there is no room for the label.

They meet Play's requirements:
- **Phone:** 1080x1920, 24-bit PNG, no alpha, 7 images (Play needs at least 2, allows 8).
- **Tablets:** `screenshots/sevenInchTablet/` (1920x1080) and
  `screenshots/tenInchTablet/` (2560x1440) cover the **7-inch** and **10-inch
  tablet** slots, under "Large screens" in Play Console. They are 16:9 landscape,
  with both dimensions between 1080 and 7680px, 7 images each.

The app has no tablet layout, so the tablet images show the same phone screens
beside the headline rather than a stretched UI. Uploading them is optional (Play
only requires phone screenshots), but recommended: the app isn't restricted to
phones, since `AndroidManifest.xml` has no `<supports-screens>` or
`<compatible-screens>` restriction.

To change copy, layout or colors, edit `store.html`, then regenerate everything:

```bash
store-listing/scripts/store_assets/build.sh                   # all graphics and screenshots
store-listing/scripts/store_assets/build.sh --only 02-conversation # one screenshot, all three sizes
```

`build.sh` needs Node with Playwright (Chromium), and Python 3 with Pillow.

## Promo video

> ⚠️ **Both videos predate the messaging app.** They show Backup, Restore, Redirect
> and Schedule but no Messages tab, and the old SMS-permission prompt instead of
> the default-SMS-app request. Do not use them for the "Default SMS handler"
> declaration: record a new one following
> [`testing-instructions.md`](testing-instructions.md#demonstration-video-permissions-declaration-form).

`video/sms-toolbox-launch.mp4` is a 24-second launch video (1920x1080, 30 fps,
H.264 + AAC, with music) that walks through Backup, Restore, Redirect and
Schedule. Play Console's **Video** field on the main store listing takes a
YouTube URL, not a file: upload the MP4 to YouTube (public or unlisted, ads
off), then paste the link. `video/sms-toolbox-launch.jpg` is its poster frame.
See [`video/README.md`](video/README.md) for what it shows and how to rebuild it.

This is **not** the demonstration video for the permissions declaration form:
that one is `video/sms-toolbox-demo.mp4` and has to show the permission flow.

## Suggested Play Console fields not covered by a file

- **App category:** Communication
- **Tags:** SMS, MMS, messaging, backup, Gmail
- **Content rating:** Everyone (run the questionnaire — no user-generated
  content, no ads, no unrestricted web access)
- **Contact email:** `play-store-neteinstein@googlegroups.com` (from
  [PrivacyPolicy.md](../PrivacyPolicy.md))
- **Privacy Policy URL:**
  `https://github.com/neteinstein/SMS-Redirect/blob/main/PrivacyPolicy.md`
  (already documented in [PublishToStore.md](../PublishToStore.md))
- **Website:** `https://github.com/neteinstein/SMS-Redirect`

## ⚠️ Before this can go live: the SMS permissions declaration

The Play build requests `READ_SMS`, `RECEIVE_SMS`, `SEND_SMS`, `RECEIVE_MMS`,
`RECEIVE_WAP_PUSH` and `READ_CELL_BROADCASTS` (granted only with the default SMS role;
Android needs it to recognize the role holder as the default SMS app). Play Console blocks publishing until you complete **App content >
Sensitive app permissions > Permissions declaration form** and justify them under
one of Google's permitted core uses.

**Declare "Default SMS handler".** SMS Toolbox is a full SMS/MMS messaging app:
the Messages tab is the first tab and the landing screen, it receives SMS
(`SMS_DELIVER`) and MMS (`WAP_PUSH_DELIVER` → download → save), sends SMS and
MMS (pictures, group messages), notifies with inline reply, handles `sms:`
links and Share (`SENDTO`/`SEND`) and quick replies from the call screen
(`RESPOND_VIA_MESSAGE`). Redirect, Schedule, Backup and Restore are tools on top
of that messaging app. The declaration has to match what the app visibly is, and
a reviewer who opens it lands on the inbox.

How the app satisfies the policy, so the form and the video can say so:
- **The role comes first.** On first launch, and before *any* SMS feature, the app
  explains itself and opens Android's role request (`RoleManager.ROLE_SMS`, the
  API-29+ replacement for `Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT`, which no
  longer does anything on Android 10+; this app's minSdk is 33). The SMS
  permissions are granted *with* the role. Only when the user explicitly declines
  the role, and then asks to use Redirect, Schedule or Backup anyway ("Use SMS
  permissions only"), are the three SMS permissions those tools need requested
  on their own. Messages and Restore are then shown **disabled**, with a warning
  bar and a "Set as default" button: the messaging app and restore never work
  without the role.
  ⚠️ This fallback is a judgment call. Play's policy expects apps declaring
  "Default SMS handler" to use SMS permissions *as* the default handler. If a
  reviewer objects, removing the "Use SMS permissions only" button from the
  decline dialog in `MainActivity` makes every SMS feature require the role.
- **No prohibited use.** No OTP/account verification, no analytics on message
  content, no ad targeting, no keyboard. Firebase events never carry numbers,
  names or text (`domain/analytics/AnalyticsEvents.kt`).
- **No library requests SMS permissions on its own.** The only SMS code is the
  app's own (`data/messaging/`, `DefaultSmsAppComponents.kt`); the MMS PDU codec
  is written in-house (`domain/mms/`) rather than pulled from a third-party MMS
  library. Check the merged manifest of each release (`release.yml` fails the
  Play release if the four default-SMS components are missing, or if
  `REQUEST_INSTALL_PACKAGES` appears).

Notes for the submission:
- The form asks for a **demonstration video** showing the app sending and
  receiving messages and becoming the default handler. The shot list is in
  [`testing-instructions.md`](testing-instructions.md#demonstration-video-permissions-declaration-form).
- **Resubmit the Data safety form in the same release** (see
  [`data-safety.md`](data-safety.md)), and ship the updated
  [`PrivacyPolicy.md`](../PrivacyPolicy.md) **before or with** this release: it
  now states that the app's primary purpose is SMS messaging, as Play requires.
- The store title and description lead with messaging
  (`text/<locale>/title.txt`, `full_description.txt`).

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
