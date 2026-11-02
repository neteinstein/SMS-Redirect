# Privacy Policy for SMS Toolbox

**Effective date:** November 2, 2026

SMS Toolbox ("the App", "we", "us", "our") is developed and maintained by [neteinstein](https://github.com/neteinstein). This Privacy Policy explains what data the App accesses, how it is used, and the choices you have. **We operate no backend server and have no account system** — your rules, contacts, and messages are processed and stored on your device. If you turn on Gmail backup, your device connects **directly to Google's Gmail servers** using credentials you supply; that content goes to your own mailbox and never passes through us. The App's full source code is public at [github.com/neteinstein/SMS-Redirect](https://github.com/neteinstein/SMS-Redirect), so every claim below can be verified directly in the code.

## Summary

| Question | Answer |
|---|---|
| Do we run a server that receives your messages or contacts? | No |
| Does the App send your SMS anywhere? | Only if **you** enable Gmail backup — then to **your own** Gmail account, over TLS, using an app password you provide |
| Is your data sold, rented, or used for advertising? | No |
| Where are your rules, schedules, and history stored? | Locally on your device only |
| What ever leaves your device? | Anonymous crash reports & usage analytics (Firebase); the SMS content you choose to forward/schedule (sent via your own carrier to the number **you** configured); your SMS messages, if you enable Gmail backup (sent to **your own** mailbox); a settings file only if you manually export and share one |
| Can messages be restored from Gmail? | Yes, if **you** start a restore. Your device downloads the backups from **your own** Gmail label and writes them into the phone's SMS storage. Nothing goes to us |
| Where is your Gmail app password kept? | Encrypted on your device, in the Android Keystore. It is sent only to Google, never to us, and cannot be recovered from a device backup |

## 1. Permissions the App Uses, and Why

The App requests the following Android permissions. Each is used only for the stated purpose. None of them give **us** remote access to your data; the only network destination the App ever contacts with your content is Google's Gmail servers, and only when you enable Gmail backup.

| Permission | Purpose |
|---|---|
| `READ_SMS` / `RECEIVE_SMS` | Read your SMS inbox so messages can be backed up to your Gmail account, and detect incoming SMS so they can be checked against your forwarding rules |
| `SEND_SMS` | Send a forwarded message, or a scheduled message, to the number you configured |
| `READ_CONTACTS` | Let you pick a contact when creating a rule or scheduled message, and show a contact's name instead of a raw number in notifications/history |
| `POST_NOTIFICATIONS` | Show backup progress, and a notification after a message is forwarded or a scheduled message is sent |
| `INTERNET` / `ACCESS_NETWORK_STATE` | Connect to Gmail to upload backed-up messages, and wait for a working connection before trying |
| Battery optimization exemption | Prevent Android's Doze mode from delaying backups, forwarding or scheduled sends |
| Default SMS app role (only while you restore) | Android lets only the default SMS app write messages to the phone's SMS storage. When you start a restore, the App asks you to make it the default SMS app. It then asks you to switch back to your usual messaging app as soon as the restore is done. While the App holds the role, any new incoming SMS is saved to your inbox exactly as your usual app would save it. Picture messages (MMS) cannot be received during that time |

## 2. Data You Create in the App

The App stores the following locally in an on-device database — none of it is transmitted to us:

- **Redirect rules** — rule name, sender match text/number, target phone number, sort order
- **Scheduled messages** — name, category, contact/number, message text, date/time, repeat pattern
- **Forward history** (on by default, can be disabled) — up to the last 50 forwarded messages: sender, target, matched rule, timestamp, and message body
- **Backup record** — for each message already copied to Gmail, a one-way hash and a timestamp, so nothing is uploaded twice. No message text is kept.
- **Gmail backup settings** — your Gmail address, your app password (encrypted, see Section 3a), the email subject template and the label name
- **Scheduled-send history** (on by default, can be disabled) — up to the last 50 sent scheduled messages
- **App settings** — roaming preference, notification preference, forwarding prefix, default country prefix, history toggles

You can view, edit, or delete this data at any time from within the App, and remove it entirely by clearing the App's storage or uninstalling it.

## 3. Message Content

When an SMS message matches one of your rules, the App reads the sender and message text on-device and, using Android's `SmsManager`, sends it via **your mobile carrier** to the target number you configured, with your configured prefix prepended. Scheduled messages are sent the same way, via your carrier, at the time you chose. In both cases the message goes only to the phone number you specified, over your device's normal SMS channel — the App does not relay, copy, or store this content on any server we operate.

## 3a. SMS Backup to Gmail

This feature is **off until you turn it on**, and it does nothing until you supply a Gmail address and an app password in Settings.

When enabled, the App reads messages from your device's SMS inbox and uploads them to your own Gmail account using IMAP over TLS (`imap.gmail.com:993`), filing them under the label you choose (default: `SMS`). Each backed-up message becomes an email in **your** mailbox, addressed from and to your own address. We never see this traffic: the connection is between your device and Google, and there is no server of ours in the path.

**Your Gmail credentials.** The address and app password you enter are stored on your device, with the password encrypted using a key held in the Android Keystore. The password is transmitted only to Google's IMAP servers as your login. We never receive it, and it is never written to logs, analytics, or crash reports. Because the encryption key lives in the Keystore and does not leave the device, a cloud backup or device transfer cannot carry the password with it — you will be asked to enter it again on a new device.

An app password grants access to your whole Gmail mailbox, not just the backup label. You can revoke it at any time at [myaccount.google.com/apppasswords](https://myaccount.google.com/apppasswords), which immediately stops the App from connecting. You can also remove it from within the App (Settings → Gmail backup → Remove).

**What each backup email contains.** The email body is a readable transcript: the sender's name (if they are in your contacts) and number, the time the message was received, and the message text. A few hidden email headers repeat the sender's number and the exact received/sent timestamps in a machine-readable form, so the message can be restored exactly. These headers add no new kind of information beyond what the body already shows, and they stay inside your own mailbox like the rest of the email.

**What the App records locally.** To avoid backing the same message up twice, the App stores a one-way hash of each backed-up message plus a timestamp. It does **not** keep a second copy of your message text.

**Deleting backed-up messages.** Messages already uploaded live in your Gmail account and are under your control — delete the label or the individual messages in Gmail to remove them.

## 3b. Restoring SMS from Gmail

Restore is **only ever started by you**, from the Backup tab. It uses the same Gmail address and app password as backup.

When you start a restore, your device connects directly to Google's Gmail servers (IMAP over TLS, read-only) and reads the emails under your backup label. It reads only the emails the App itself created and ignores any other mail under that label. Each message is written back into your phone's SMS storage. A message that is already on the phone is skipped. The download goes from your own mailbox to your own device, with no server of ours in the path, and nothing is sent anywhere else. Only text messages (SMS) are restored, not picture messages (MMS).

For each restored message the App stores the same one-way hash it stores for a backup, so restored messages are not uploaded to Gmail a second time. As with backup, it keeps no second copy of the message text.

## 4. Settings Backup and Restore (local file)

This is a separate feature from the Gmail backup described above. The "Export Backup" feature (Settings) builds a JSON file containing your rules, scheduled messages (including message text and phone numbers), and settings. **It deliberately does not include your Gmail address or app password**, because the file is handed to the share sheet and could end up anywhere. This file is generated only on your device and handed to Android's native share sheet — **you** choose its destination (e.g., saved locally, emailed, or uploaded to your own cloud storage). We never receive a copy. "Import Backup" reads a file you select back into the App's local database.

## 5. Diagnostics and Analytics (Firebase)

The App uses [Firebase](https://firebase.google.com/), a Google service, for:

- **Crashlytics** — automatic crash reports and non-fatal exception reports (e.g., a forwarding/sending failure), including device model, OS version, and stack traces, to help us find and fix bugs.
- **Analytics** — anonymous, aggregate usage events, e.g. a rule was created/updated/deleted, a message was forwarded, a scheduled message was sent, or a setting was toggled. These events never include phone numbers, contact names, or message contents — see the App's open-source event catalog (`domain/analytics/AnalyticsEvents.kt`) for every event and parameter we send.

Firebase may process and store this data on servers outside your country. See [Google's Privacy Policy](https://policies.google.com/privacy) for how Google handles it.

## 6. OS-Level Device Backup

The App permits Android's built-in backup mechanism. If you have Android device backup or device-to-device transfer enabled on your phone, your rules/settings/history (which may include phone numbers and message snippets) could be included in your own encrypted backup to your Google Account. This is controlled entirely by your device's operating system settings, not by us — manage it under your device's Settings > System > Backup.

## 7. Data We Do Not Collect

We do not collect your name or any account/profile information — the App has no login system and no server of ours to log in to. If you enable Gmail backup you supply a Gmail address, but it is stored on your device and sent only to Google as part of your IMAP login; **we never receive it**. We do not integrate advertising SDKs, and we do not sell or share your data with third parties for marketing purposes.

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
