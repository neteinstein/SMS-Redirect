# Demo video: Permissions declaration form

`sms-toolbox-demo.mp4` is a 1:50 walkthrough of the whole app (1080x1920 portrait,
30 fps, H.264 with a silent AAC track). It shows every permission the app asks
for, at the moment it asks and why. `storyboard.png` is a contact sheet of the
same video with timestamps.

Regenerate both with:

```bash
python3 store-listing/scripts/generate_demo_video.py   # needs Pillow + ffmpeg (libx264)
```

## What it shows

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

## Important: this is a rendered walkthrough, not a screen recording

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

## Submitting it

Play Console → **App content** → the SMS and Call Log **Permissions
declaration form** (the one where "Backup and restore" is the declared core
use case) → the demo video field.
The field takes a link, not a file. Upload the MP4 to YouTube as **Unlisted**
(not Private, or the reviewer can't open it) and paste the link.
