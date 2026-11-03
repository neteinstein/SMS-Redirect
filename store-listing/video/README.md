# Videos

This folder holds two videos with different jobs:

| File | Use it for |
|---|---|
| `sms-toolbox-launch.mp4` | The store listing's promo video, social posts, the README. 24 s, with music |
| `sms-toolbox-demo.mp4` | The Permissions declaration form's demonstration video. 1:50, shows every permission prompt |

## Launch video: `sms-toolbox-launch.mp4`

A 24-second launch video (1920x1080, 30 fps, H.264 with AAC music) covering all
four features. `sms-toolbox-launch.jpg` is its poster, and is also baked in as
frame 0 so platforms that thumbnail the first frame show it.

| Time | Scene | On screen |
|---|---|---|
| 0:00 | Hook | A chat bubble: "Lose the phone, lose the texts?" |
| 0:03 | Reveal | App icon, "SMS Toolbox", "Backup · Restore · Redirect · Schedule" |
| 0:05 | Backup | The Backup tab counts up "Backing up … of 3,214" while emails fill a Gmail "SMS" label |
| 0:09.5 | Restore | "Restore messages" is tapped; "Checking … of 3,214" runs to "3,214 messages restored" |
| 0:13 | Redirect | A WorkBank code arrives, the "Work OTP" rule matches, and the work phone gets "SMS Toolbox: (WorkBank) - Your code is 482913" |
| 0:17 | Schedule | The Create Schedule SMS form fills in and saves a yearly birthday text to Mom |
| 0:21 | Outro | Icon, the four features, "No account. No server. Just your Gmail." |

Like the demo video, it is rendered, not recorded: the screens are rebuilt in
HTML from the app's real strings (`strings.xml`), colors (`Color.kt`), icons and
the forwarded-message format in `SmsForwarder`. Every name, number and message
in it is fictional. The music and sound effects are synthesized by
`music.py` (120 BPM, every cut on the beat), so there is no third-party audio
to license.

For the Play listing, upload it to YouTube and paste the link in the store
listing's **Video** field (Play takes a URL, not a file).

Suggested share copy:

> Lose the phone, lose the texts? Not with SMS Toolbox: it backs up every SMS you send or receive to your own Gmail, restores them to a new phone in one tap, forwards the ones you choose, and sends texts on a schedule. No account, no server, just your Gmail. Android.

Rebuild it (needs Node + Playwright with Chromium, Python 3 + numpy, ffmpeg, and the Inter font):

```bash
store-listing/scripts/launch_video/build.sh
```

## Permissions demo: `sms-toolbox-demo.mp4`

`sms-toolbox-demo.mp4` is a 1:50 walkthrough of the whole app (1080x1920 portrait,
30 fps, H.264 with a silent AAC track). It shows every permission the app asks
for, at the moment it asks and why. `storyboard.png` is a contact sheet of the
same video with timestamps.

Regenerate both with:

```bash
python3 store-listing/scripts/generate_demo_video.py   # needs Pillow + ffmpeg (libx264)
```

### What it shows

| Time | Scene | Permission called out |
|---|---|---|
| 0:00 | Title card listing every permission shown | n/a |
| 0:04 | App opens on the Backup tab. Nothing is asked at launch | n/a |
| 0:08 | Settings → Gmail backup: address + app password → "Connected to Gmail" | `INTERNET` (no prompt; `imap.gmail.com` only) |
| 0:17 | Backup tab → "Backup all previous SMS" → Enable | n/a |
| 0:22 | In-app "SMS permission needed" explanation → Proceed | `READ_SMS` · `RECEIVE_SMS` · `SEND_SMS` |
| 0:28 | Android prompt "Allow SMS Toolbox to send and view SMS messages?" → Allow | `READ_SMS` · `RECEIVE_SMS` · `SEND_SMS` |
| 0:33 | Android prompt "Allow SMS Toolbox to send you notifications?" → Allow | `POST_NOTIFICATIONS` |
| 0:37 | Existing SMS backed up ("Backing up X of 3,214") | `READ_SMS` |
| 0:41 | A new SMS arrives and is backed up straight away | `RECEIVE_SMS` |
| 0:44 | The message in Gmail under the `SMS` label, named from contacts | n/a |
| 0:49 | Redirect tab → + → contact icon on Sender | n/a |
| 0:55 | In-app "Contacts permission needed" explanation → Proceed | `READ_CONTACTS` |
| 1:00 | Android prompt "Allow SMS Toolbox to access your contacts?" → Allow | `READ_CONTACTS` |
| 1:04 | Contact picker → Mom → rule filled → Save Rule | n/a |
| 1:10 | "Keep running in background" explanation → Android exemption prompt | `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` |
| 1:18 | A matching SMS arrives and is forwarded; logged in Redirect History | `RECEIVE_SMS` · `SEND_SMS` |
| 1:25 | Schedule tab → create a scheduled SMS (contact already allowed) → saved | `SEND_SMS` |
| 1:37 | "Why each permission is needed" summary card | all of the above |

The order and dialog text follow the code: each permission is asked for only
when a feature needs it, and the in-app explanation always comes before the
system prompt (`MainActivity.kt`: `requestSmsPermission`, `showContactsRationale`,
`showBatteryDialog`, and `backupNotificationsLauncher` when backup is enabled).
All copy comes from `app/src/main/res/values/strings.xml`.

### Important: this is a rendered walkthrough, not a screen recording

No emulator or device was available where this was made, so the video is
rendered from the app's real strings, colors and permission flow, the same way
as the store screenshots. The Permissions declaration form asks for a video of
the app working **on a device**, and a reviewer may reject a rendered one. Two
ways to use this file:

1. **Recommended:** use it as the shot list. Record the same sequence on a real
   phone with Android's built-in screen recorder (or
   `adb shell screenrecord /sdcard/demo.mp4`, which stops after 3 minutes), on a
   fresh install with the disposable test Gmail account from
   [`testing-instructions.md`](../testing-instructions.md). Turn on
   *Developer options → Show taps* so taps are visible. Optionally put this
   video's summary card (1:37 onward) at the end.
2. Upload it as is if you just need a placeholder to unblock the form, and
   replace it with a device recording before the review.

### Submitting it

Play Console → **App content** → the SMS and Call Log **Permissions
declaration form** (the one where "Backup and restore" is the declared core
use case) → the demo video field.
The field takes a link, not a file. Upload the MP4 to YouTube as **Unlisted**
(not Private, or the reviewer can't open it) and paste the link.
