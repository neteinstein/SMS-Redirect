# Privacy Policy for SMS Toolbox

**Effective date:** November 3, 2026

SMS Toolbox ("the App", "we", "us") is an Android app developed by [neteinstein](https://github.com/neteinstein). It backs up your text messages to your own Gmail account, restores them to a phone, forwards the messages you choose, and sends messages on a schedule.

**The short version:** we run no server and have no account system. Your messages, rules and schedules are processed on your phone. If you turn on Gmail backup, your phone talks **directly to Google's Gmail servers**, using credentials you create, and your messages go to **your own** mailbox. They never pass through us. The App's source code is public at [github.com/neteinstein/SMS-Redirect](https://github.com/neteinstein/SMS-Redirect), so every statement in this policy can be checked against the code.

## At a glance

| Question | Answer |
|---|---|
| Do we run a server that receives your messages, contacts or credentials? | **No.** We do not operate one |
| Does the App upload your text messages? | Only if **you** turn on Gmail backup. Then they go to **your own** Gmail account, encrypted in transit, using an app password you create |
| Can we read your backups? | **No.** They live in your Gmail account, which only you control |
| Where are your rules, schedules and history kept? | On your phone only |
| Where is your Gmail app password kept? | On your phone, encrypted with a key held in the Android Keystore. It is sent only to Google, as your login |
| Is your data sold, rented or used for advertising? | **No** |
| What else leaves your phone? | Crash reports and usage statistics (Firebase), which never include phone numbers, contact names or message text. Messages you forward or schedule, sent by your carrier to the numbers **you** chose. A settings file, only if you export and share one |

## 1. Gmail backup

Gmail backup is **off until you turn it on**, and it needs a Gmail address and an app password, which you enter in Settings.

**What is uploaded.** The App reads the SMS messages you have received and the ones you have sent, and uploads each one to your Gmail account over IMAP with TLS encryption (`imap.gmail.com`, port 993). Each message becomes an email in **your** mailbox, from and to your own address, filed under the label you choose (default: `SMS`). Drafts, messages that failed to send and picture messages (MMS) are not backed up. You choose whether to back up your existing messages or only new ones.

**What each backup email contains.** A readable transcript: the other person's name (if they are in your contacts) and number, whether the message was received or sent, the time, and the message text. A few hidden email headers repeat the number, the direction and the exact timestamps in a machine-readable form, so the message can be restored exactly. They contain nothing the transcript does not already show.

**Your Gmail credentials.** Your address and app password are stored on your phone, and the password is encrypted with a key held in the Android Keystore. The password is sent only to Google's IMAP servers, as your login. We never receive it, and it is never written to logs, analytics or crash reports. Because the Keystore key never leaves your phone, a device backup or transfer cannot carry the password: on a new phone you enter it again.

An app password gives access to your whole Gmail mailbox, not only the backup label. You can revoke it at any time at [myaccount.google.com/apppasswords](https://myaccount.google.com/apppasswords), which immediately stops the App from connecting, or remove it in the App (Settings → Gmail backup → Remove).

**What the App remembers.** To avoid uploading a message twice, the App stores a one-way hash of each backed-up message and the time it was backed up. It does **not** keep a second copy of your message text.

**Deleting backups.** Backed-up messages are emails in your own Gmail account. Delete the label, or individual emails, in Gmail to remove them.

## 2. Restoring from Gmail

A restore is **only ever started by you**, from the Backup tab, and uses the same Gmail address and app password as backup.

Your phone connects directly to Gmail (IMAP with TLS, read-only) and reads the emails under your backup label. It restores only emails the App itself created. Anything else under that label, including email that imitates a backup, is skipped and never written to your phone. Each restored message is written to your phone's SMS storage, in the inbox or the sent box as it was originally. Messages already on the phone are skipped. Only text messages (SMS) are restored, not picture messages (MMS).

Android lets only the default SMS app write messages, so during a restore the App asks you to make it your default SMS app. It asks you to switch back to your usual messaging app as soon as the restore is finished. While it holds that role, it saves new incoming SMS to your inbox, as your usual app would. Picture messages (MMS) cannot be received during that time.

For each restored message the App stores the same one-way hash it stores for a backup, so a restored message is not uploaded again. Nothing in a restore is sent anywhere except from your own mailbox to your own phone.

## 3. Forwarding and scheduled messages

When an incoming SMS matches one of your rules, the App reads its sender and text on your phone and sends it, with the prefix you configured, to the number you chose. It uses Android's standard SMS service, through your mobile carrier. Scheduled messages are sent the same way, at the time you chose. These messages go only to the numbers you specified. We do not relay, copy or store them on any server.

## 4. What stays on your phone

The App keeps the following in its local database. None of it is sent to us.

- **Redirect rules:** name, category, sender to match, target number and order
- **Scheduled messages:** name, category, contact or number, message text, date and time, repeat pattern and stop date
- **Forward history** (on by default, can be turned off): up to the last 50 forwarded messages, with sender, target, matched rule, time and message text
- **Scheduled-send history** (on by default, can be turned off): up to the last 50 sent scheduled messages
- **Backup record:** a one-way hash and a timestamp for each message already in Gmail. No message text
- **Gmail backup settings:** your Gmail address, your encrypted app password, the email subject template and the label name
- **App settings:** landing tab, roaming and notification preferences, forwarding prefix, default country prefix and history toggles

You can view, change or delete this data in the App at any time, and remove all of it by clearing the App's storage or uninstalling it.

## 5. What leaves your phone

This is the complete list:

| What | Where it goes | When |
|---|---|---|
| Your SMS messages (received and sent) | **Your own** Gmail account, via Google's IMAP servers | Only while Gmail backup is on |
| Your Gmail address and app password | Google's IMAP servers, as your login | Each time the App connects for a backup or restore |
| Messages you forward or schedule | The phone numbers **you** chose, via your carrier's SMS network | When a rule matches, or at the scheduled time |
| Crash reports and usage statistics | Google Firebase (see Section 6) | Automatically |
| A settings file (rules, schedules, settings) | Wherever **you** send it from Android's share sheet | Only if you tap Export |
| Your App data, as part of an Android device backup | Your own Google account, encrypted by Android | Only if Android device backup or transfer is on (see Section 7) |

## 6. Diagnostics and analytics (Firebase)

The App uses two Google [Firebase](https://firebase.google.com/) services to find bugs and understand which features are used:

- **Crashlytics:** crash reports and non-fatal error reports, for example a message that failed to send. They include the device model, Android version and technical stack traces.
- **Analytics:** usage events, for example that a rule was created, a message was forwarded, a scheduled message was sent or a setting was changed. Every event and parameter is listed in the open-source catalog `domain/analytics/AnalyticsEvents.kt`.

These reports and events **never include phone numbers, contact names, email addresses, Gmail credentials or message text.** Firebase attaches an installation identifier to them. Firebase Analytics may also read your device's advertising ID; the App declares Android's `AD_ID` permission for this. We use it for analytics only, never for advertising. You can reset or delete your advertising ID at any time in Android Settings → Privacy → Ads.

Google may process this data on servers outside your country. [Google's Privacy Policy](https://policies.google.com/privacy) explains how Google handles it.

## 7. Android device backup

The App allows Android's built-in backup and device-to-device transfer. If you have them turned on, the App's local data (rules, schedules, history and settings, which can include phone numbers and message text) may be included in your own encrypted backup in your Google account. Your Gmail app password cannot be recovered from such a backup (see Section 1). This is controlled by Android, not by us: manage it in Android Settings → System → Backup.

## 8. Settings export file

Separately from Gmail backup, Settings → Export Configurations creates a file with your rules, scheduled messages (including their text and phone numbers) and settings. It **never includes your Gmail address or app password**. The file is created on your phone and handed to Android's share sheet, and **you** choose where it goes. We never receive a copy. Import Configurations reads a file you pick back into the App.

## 9. Permissions and why the App needs them

Each permission is requested only when a feature needs it, and none of them gives **us** access to your data.

| Permission | Why |
|---|---|
| `READ_SMS` / `RECEIVE_SMS` | Read your received and sent messages to back them up to Gmail, and detect new messages to back up or check against your forwarding rules |
| `SEND_SMS` | Send forwarded and scheduled messages to the numbers you chose |
| `READ_CONTACTS` | Let you pick a contact for a rule or a scheduled message, and show names instead of numbers |
| `POST_NOTIFICATIONS` | Show backup and restore progress, and confirm forwarded and scheduled messages |
| `INTERNET` / `ACCESS_NETWORK_STATE` | Connect to Gmail for backup and restore, and wait for a connection before trying |
| Battery optimization exemption | Stop Android from delaying backups, forwards and scheduled sends |
| Default SMS app (only while a restore runs) | Write restored messages to the phone's SMS storage, which Android allows only for the default SMS app |
| `AD_ID` | Used by Firebase Analytics (see Section 6); never for advertising |

## 10. What we do not do

- We do not run a server, and there is no account or login.
- We do not receive your messages, contacts, Gmail address or app password.
- We do not include advertising SDKs or show ads.
- We do not sell, rent or share your data for marketing.

## 11. Security

Gmail connections use TLS with server identity checks. Your app password is encrypted with a key held in the Android Keystore. The App never logs message content or credentials. The backup record keeps only one-way hashes, not message text.

## 12. Keeping and deleting data

Data on your phone stays until you delete it in the App, clear the App's storage or uninstall it, subject to any Android device backup you have turned on (Section 7). Backups in Gmail stay until you delete them in Gmail. Firebase data is kept by Google under its own retention policies, and we do not link it to your identity.

## 13. Your choices and rights

- **Permissions:** revoke any permission in Android Settings. The feature that needs it stops working until you grant it again, and the rest of the App keeps working. If background running is not allowed, the App shows a banner until you allow it.
- **Gmail backup:** turn it off on the Backup tab, remove your credentials in Settings, or revoke the app password in your Google account.
- **History:** turn off Keep history or Keep schedule history to stop recording them, or clear them at any time.
- **Advertising ID:** reset or delete it in Android Settings → Privacy → Ads.
- **Everything:** uninstall the App to remove all of its data from your phone.

Depending on where you live (for example under the GDPR or the CCPA), you may have rights to access, correct, export or delete your data. Everything the App stores is already on your phone and under your control. For questions about the diagnostic data processed by Firebase, contact us using the details below.

## 14. Children

The App is not directed at children under 13, and we do not knowingly collect personal data from children.

## 15. Changes to this policy

We may update this policy as the App changes. Updates are published at this same address with a new effective date.

## 16. Contact

Questions about this policy or your data? Email **play-store-neteinstein@googlegroups.com**.
