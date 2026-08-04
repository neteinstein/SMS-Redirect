# Privacy Policy for SMS Redirect & Schedule

**Effective date:** July 12, 2026

SMS Redirect & Schedule ("the App", "we", "us", "our") is developed and maintained by [neteinstein](https://github.com/neteinstein). This Privacy Policy explains what data the App accesses, how it is used, and the choices you have. The App has no login/account system and no backend server — your rules, contacts, and messages are processed and stored on your device. The App's full source code is public at [github.com/neteinstein/SMS-Redirect](https://github.com/neteinstein/SMS-Redirect), so every claim below can be verified directly in the code.

## Summary

| Question | Answer |
|---|---|
| Do we run a server that receives your messages or contacts? | No |
| Is your data sold, rented, or used for advertising? | No |
| Where are your rules, schedules, and history stored? | Locally on your device only |
| What ever leaves your device? | Anonymous crash reports & usage analytics (Firebase); the SMS content you choose to forward/schedule (sent via your own carrier to the number **you** configured); a backup file only if you manually export and share one |

## 1. Permissions the App Uses, and Why

The App requests the following Android permissions. Each is used only for the stated purpose, and processed entirely on-device — none of them give us remote access to your data.

| Permission | Purpose |
|---|---|
| `READ_SMS` / `RECEIVE_SMS` | Detect incoming SMS so it can be checked against your forwarding rules |
| `SEND_SMS` | Send a forwarded message, or a scheduled message, to the number you configured |
| `READ_CONTACTS` | Let you pick a contact when creating a rule or scheduled message, and show a contact's name instead of a raw number in notifications/history |
| `POST_NOTIFICATIONS` | Show a notification after a message is forwarded or a scheduled message is sent |
| Battery optimization exemption | Prevent Android's Doze mode from delaying forwarding or scheduled sends |

## 2. Data You Create in the App

The App stores the following locally in an on-device database — none of it is transmitted to us:

- **Redirect rules** — rule name, sender match text/number, target phone number, sort order
- **Scheduled messages** — name, category, contact/number, message text, date/time, repeat pattern
- **Forward history** (on by default, can be disabled) — up to the last 50 forwarded messages: sender, target, matched rule, timestamp, and message body
- **Scheduled-send history** (on by default, can be disabled) — up to the last 50 sent scheduled messages
- **App settings** — roaming preference, notification preference, forwarding prefix, default country prefix, history toggles

You can view, edit, or delete this data at any time from within the App, and remove it entirely by clearing the App's storage or uninstalling it.

## 3. Message Content

When an SMS message matches one of your rules, the App reads the sender and message text on-device and, using Android's `SmsManager`, sends it via **your mobile carrier** to the target number you configured, with your configured prefix prepended. Scheduled messages are sent the same way, via your carrier, at the time you chose. In both cases the message goes only to the phone number you specified, over your device's normal SMS channel — the App does not relay, copy, or store this content on any server we operate.

## 4. Backup and Restore

The "Export Backup" feature (Settings) builds a JSON file containing your rules, scheduled messages (including message text and phone numbers), and settings. This file is generated only on your device and handed to Android's native share sheet — **you** choose its destination (e.g., saved locally, emailed, or uploaded to your own cloud storage). We never receive a copy. "Import Backup" reads a file you select back into the App's local database.

## 5. Diagnostics and Analytics (Firebase)

The App uses [Firebase](https://firebase.google.com/), a Google service, for:

- **Crashlytics** — automatic crash reports and non-fatal exception reports (e.g., a forwarding/sending failure), including device model, OS version, and stack traces, to help us find and fix bugs.
- **Analytics** — anonymous, aggregate usage events, e.g. a rule was created/updated/deleted, a message was forwarded, a scheduled message was sent, or a setting was toggled. These events never include phone numbers, contact names, or message contents — see the App's open-source event catalog (`domain/analytics/AnalyticsEvents.kt`) for every event and parameter we send.

Firebase may process and store this data on servers outside your country. See [Google's Privacy Policy](https://policies.google.com/privacy) for how Google handles it.

## 6. OS-Level Device Backup

The App permits Android's built-in backup mechanism. If you have Android device backup or device-to-device transfer enabled on your phone, your rules/settings/history (which may include phone numbers and message snippets) could be included in your own encrypted backup to your Google Account. This is controlled entirely by your device's operating system settings, not by us — manage it under your device's Settings > System > Backup.

## 7. Data We Do Not Collect

We do not collect your name, email address, or any account/profile information — the App has no login system. We do not integrate advertising SDKs, and we do not sell or share your data with third parties for marketing purposes.

## 8. Data Retention and Deletion

Rule, schedule, and history data remains in local app storage until you delete it in-app, clear the App's storage, or uninstall the App, at which point it is removed from your device (subject to any OS-level device backup you've separately enabled — see Section 6). Firebase Crashlytics/Analytics data is retained by Google under its own retention policies and is not linked back to your identity by us.

## 9. Children's Privacy

The App is not directed at children under 13, and we do not knowingly collect personal data from children.

## 10. Your Choices and Rights

- Revoke any runtime permission at any time in Android Settings — the App degrades gracefully rather than crashing (the affected feature, e.g. forwarding, scheduled sending, contact picking, or notifications, simply stops working until you re-grant it). If background running is not enabled, the App shows a persistent banner on the home screen until you enable it.
- Turn off "Keep history" or "Keep schedule history" in Settings to stop local history logging.
- Uninstall the App to remove all locally stored data from your device.
- Depending on your local law (e.g., GDPR, CCPA), you may have rights to access, correct, export, or delete data. Since all rule/schedule/history data lives only on your device, you already have full access via the App itself; for questions about Firebase-processed diagnostic/analytics data, contact us using the details below.

## 11. Changes to This Policy

We may update this Privacy Policy as the App changes. Updates will be posted at this same location with a revised effective date.

## 12. Contact Us

Questions about this policy or your data? Contact us at **play-store-neteinstein@googlegroups.com**.
