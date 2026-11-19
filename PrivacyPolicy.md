# Privacy Policy for SMS Toolbox

**Effective date:** November 19, 2026

SMS Toolbox ("the App", "we", "us") is an Android app developed by [neteinstein](https://github.com/neteinstein). **Its primary purpose is SMS messaging:** it is a full SMS and MMS messaging app that you set as your phone's default SMS app, to read, send and receive your text and picture messages. Around that, it backs up your text messages to your own Gmail account, restores them to a phone, forwards the messages you choose, and sends messages on a schedule.

**Two versions.** The App is published in two versions built from the same source code:

- **Google Play version:** every feature in this policy except the update check in Section 10 (Google Play updates it).
- **GitHub version** (the APK attached to each release on GitHub): every feature in this policy, plus the update check described in Section 10. It also lets Automate, Schedule and Backup ask for SMS permissions without first offering to become your default SMS app (Section 1).

Everything else in this policy applies to both.

**The short version:** we run no server and have no account system. Your messages, rules and schedules are processed and stored on your phone. If you turn on Gmail backup, your phone talks **directly to Google's Gmail servers**, using credentials you create, and your messages go to **your own** mailbox. They never pass through us. The App's source code is public at [github.com/neteinstein/SMS-Redirect](https://github.com/neteinstein/SMS-Redirect), so every statement in this policy can be checked against the code.

## At a glance

| Question | Answer |
|---|---|
| Do we run a server that receives your messages, contacts or credentials? | **No.** We do not operate one |
| Where are your messages kept? | On your phone, in Android's own SMS/MMS storage, as with any messaging app. The App keeps no other copy |
| Does the App upload your text messages? | Only if **you** turn on Gmail backup. Then they go to **your own** Gmail account, encrypted in transit, using an app password you create |
| Can we read your backups? | **No.** They live in your Gmail account, which only you control |
| Where are your rules, schedules and history kept? | On your phone only |
| Where is your Gmail app password kept? | On your phone, encrypted with a key held in the Android Keystore. It is sent only to Google, as your login |
| Is your data sold, rented or used for advertising? | **No** |
| What else leaves your phone? | Messages you send, forward or schedule, delivered by your carrier to the people **you** chose. Crash reports and usage statistics (Firebase), which never include phone numbers, contact names or message text. A settings file, only if you export and share one. In the GitHub version, a once-a-day anonymous update check to GitHub |

## 1. Messaging (the App's main feature)

SMS Toolbox is a messaging app. It works once you make it your **default SMS app**: the App asks for this first, using Android's own prompt, before it requests any SMS permission (except, in the GitHub version, for the features described below), and Android then gives it access to your messages. You can choose another default SMS app at any time in Android Settings; the App then stops receiving messages.

**If you do not make it your default SMS app,** the Messages and Restore tabs are shown disabled, with a reminder and a button to change your mind. Automate, Schedule and Backup can still work. You can create automations and scheduled messages at any time; until the App has SMS access, those tabs show a reminder with an "Allow" button. In the Google Play version, the App first offers the default-SMS-app prompt, and only if you decline it asks for the SMS permissions these features need (to read, receive and send SMS) on their own; after you have declined once, "Allow" asks for those permissions directly. In the GitHub version, "Allow" asks for those permissions directly, without the default-SMS-app prompt.

**Reading.** The App shows the conversations and messages in your phone's SMS/MMS storage, with contact names from your phone's contacts. It reads them on your phone only, to display them to you.

**Receiving.** Android delivers incoming SMS and MMS to the default SMS app. The App saves each one in your phone's message storage, as any messaging app does, and shows a notification with the sender and the text, with Reply and Mark as read buttons. On the lock screen the notification only says "New message", unless you change that in Android's notification settings. A picture message (MMS) is downloaded from your carrier's MMS server over mobile data, by Android's MMS service, and then saved on your phone.

**Sending.** When you send a message, or reply from a notification or the incoming-call screen, the App hands it to Android, which sends it through your mobile carrier to the people you chose, and saves it in your phone's message storage. Text to one person is sent as SMS; a message with a picture or other attachment, or to several people, is sent as MMS. Large photos are resized on your phone first so the carrier accepts them. Your carrier may charge for messages as usual.

**Delivery and read reports.** Three switches in Settings → Message receipts, all **off** by default:
- *SMS delivery reports* and *MMS delivery reports* ask your carrier to report when your messages reach the other phone, and the App shows "Delivered" under them. With MMS delivery reports on, the App also lets the carrier tell people who send you an MMS that it reached you.
- *MMS read reports* asks the people you send an MMS to for a read report, and shows "Read" when one comes back. It also sends a read report — the fact that you read the message, and when — to whoever sent you an MMS and asked for one, at the moment you open that conversation.

These reports travel only between your phone, your carrier and the other person's phone, like the messages themselves. They are not sent to us.

**Sharing into the App.** Other apps can open a new message in the App (a contact's "message" button, an `sms:` link, or Share). Nothing is sent until you tap Send.

**Spam and fraud warnings.** Messages you receive from someone who is not in your contacts are checked on your phone for common signs of scams: links that hide or disguise where they go (link shorteners, bare IP addresses, look-alike domains) and wording such as requests for passwords or card details, prizes, or parcel fees. A message that looks like spam or fraud is marked in its notification, the conversation shows a warning, and the message's links do not open. Tapping such a link shows where it really goes and lets you open it once, or mark the sender as trusted: the warning then goes away and their links open. You can stop trusting a sender at any time from their conversation's menu. The check runs entirely on your phone; nothing about it is sent to us or anyone else. Your list of trusted senders is kept on your phone (Section 5).

**What the App does not do with your messages.** It does not send them to us, does not analyse them for any purpose other than showing them to you and the on-device spam and fraud check above, does not use them for advertising, and does not keep a copy of them anywhere except your phone's message storage (and your own Gmail, only if you turn on backup, Section 2). It does not use SMS to verify an account.

## 2. Gmail backup

Gmail backup is **off until you turn it on**, and it needs a Gmail address and an app password, which you enter in Settings.

**What is uploaded.** The App reads the SMS messages you have received and the ones you have sent, and uploads each one to your Gmail account over IMAP with TLS encryption (`imap.gmail.com`, port 993). Each message becomes an email in **your** mailbox, from and to your own address, filed under the label you choose (default: `SMS`). Drafts, messages that failed to send and picture messages (MMS) are not backed up. You choose whether to back up your existing messages or only new ones.

**What each backup email contains.** A readable transcript: the other person's name (if they are in your contacts) and number, whether the message was received or sent, the time, and the message text. A few hidden email headers repeat the number, the direction and the exact timestamps in a machine-readable form, so the message can be restored exactly. They contain nothing the transcript does not already show.

**Your Gmail credentials.** Your address and app password are stored on your phone, and the password is encrypted with a key held in the Android Keystore. The password is sent only to Google's IMAP servers, as your login. We never receive it, and it is never written to logs, analytics or crash reports. Because the Keystore key never leaves your phone, a device backup or transfer cannot carry the password: on a new phone you enter it again.

An app password gives access to your whole Gmail mailbox, not only the backup label. You can revoke it at any time at [myaccount.google.com/apppasswords](https://myaccount.google.com/apppasswords), which immediately stops the App from connecting, or remove it in the App (Settings → Gmail backup → Remove).

**What the App remembers.** To avoid uploading a message twice, the App stores a one-way hash of each backed-up message and the time it was backed up. It does **not** keep a second copy of your message text.

**Deleting backups.** Backed-up messages are emails in your own Gmail account. Delete the label, or individual emails, in Gmail to remove them.

## 3. Restoring from Gmail

A restore is **only ever started by you**, from the Restore tab, and uses the same Gmail address and app password as backup.

Your phone connects directly to Gmail (IMAP with TLS, read-only) and reads the emails under your backup label. It restores only emails the App itself created. Anything else under that label, including email that imitates a backup, is skipped and never written to your phone. Each restored message is written to your phone's SMS storage, in the inbox or the sent box as it was originally. Messages already on the phone are skipped. Only text messages (SMS) are restored, not picture messages (MMS).

Android lets only the default SMS app write messages, so a restore needs the App to be your default SMS app, which, as a messaging app, it normally already is. If it is not, the App asks you first.

For each restored message the App stores the same one-way hash it stores for a backup, so a restored message is not uploaded again. Nothing in a restore is sent anywhere except from your own mailbox to your own phone.

## 4. Forwarding, phone actions and scheduled messages

When an incoming SMS matches one of your redirect rules, the App reads its sender and text on your phone and sends it, with the prefix you configured, to the number you chose. It uses Android's standard SMS service, through your mobile carrier. Scheduled messages are sent the same way, at the time you chose. These messages go only to the numbers you specified, and appear in that conversation in the App like any other message you sent. We do not relay, copy or store them on any server.

A rule can instead run a **phone action**: ring at full volume with a sound you choose, vibrate, or change your phone's sound mode. The App compares the incoming message's sender and text with your rule on your phone and performs the action there. Nothing is sent anywhere, and the message is not stored again. A song you pick stays where it is; the App only keeps permission to play it.

## 5. What stays on your phone

Your SMS and MMS messages are kept in Android's own message storage on your phone, where every messaging app keeps them; deleting a message or conversation in the App deletes it there. In addition, the App keeps the following in its local database. None of it is sent to us.

- **Automation rules:** name, category, sender to match, and order; for a redirect, the target number; for a phone action, the text to look for in messages, the action, and the chosen sound's name and location on your phone
- **Scheduled messages:** name, category, contact or number, message text, date and time, repeat pattern and stop date
- **Forward history** (on by default, can be turned off): up to the last 50 forwarded messages, with sender, target, matched rule, time and message text
- **Scheduled-send history** (on by default, can be turned off): up to the last 50 sent scheduled messages
- **Backup record:** a one-way hash and a timestamp for each message already in Gmail. No message text
- **Gmail backup settings:** your Gmail address, your encrypted app password, the email subject template and the label name
- **App settings:** landing tab, roaming and notification preferences, forwarding prefix, default country prefix and history toggles
- **Trusted senders** (kept with the App's settings rather than in the database): the numbers you marked as trusted after a spam or fraud warning. Not included in the settings export file. You can remove a sender from it with "Stop trusting this sender" in their conversation

You can view, change or delete this data in the App at any time, and remove all of it by clearing the App's storage or uninstalling it.

## 6. What leaves your phone

This is the complete list:

| What | Where it goes | When |
|---|---|---|
| Messages you send (SMS and MMS, including attachments you add) | The people **you** chose, via your carrier's SMS/MMS network | When you tap Send, or reply from a notification or the call screen |
| A request to download a picture message sent to you | Your carrier's MMS server, by Android's MMS service | When a picture message arrives |
| Delivery reports you asked for, and (with MMS delivery reports on) permission for the carrier to confirm delivery of picture messages sent to you | Your carrier, and the sender of that message | Only with the matching switch on in Settings → Message receipts |
| A read report for a picture message sent to you | The person who sent it, via your carrier | Only with MMS read reports on, and only if they asked for one, when you open the conversation |
| Your SMS messages (received and sent) | **Your own** Gmail account, via Google's IMAP servers | Only while Gmail backup is on |
| Your Gmail address and app password | Google's IMAP servers, as your login | Each time the App connects for a backup or restore |
| Messages you forward or schedule | The phone numbers **you** chose, via your carrier's SMS network | When a rule matches, or at the scheduled time |
| Crash reports and usage statistics | Google Firebase (see Section 7) | Automatically |
| A settings file (rules, schedules, settings) | Wherever **you** send it from Android's share sheet | Only if you tap Export |
| An update check (GitHub version only) | GitHub's public API (`api.github.com`), and GitHub's download servers if you install an update | At most once a day when you open the App, when you tap Check for updates, and when you tap Download and install |
| Your App data, as part of an Android device backup | Your own Google account, encrypted by Android | Only if Android device backup or transfer is on (see Section 8) |

## 7. Diagnostics and analytics (Firebase)

The App uses two Google [Firebase](https://firebase.google.com/) services to find bugs and understand which features are used:

- **Crashlytics:** crash reports and non-fatal error reports, for example a message that failed to send. They include the device model, Android version and technical stack traces.
- **Analytics:** usage events, for example that a rule was created, a message was forwarded, a scheduled message was sent or a setting was changed. Every event and parameter is listed in the open-source catalog `domain/analytics/AnalyticsEvents.kt`.

These reports and events **never include phone numbers, contact names, email addresses, Gmail credentials or message text.** Firebase attaches an installation identifier to them. Firebase Analytics may also read your device's advertising ID; the App declares Android's `AD_ID` permission for this. We use it for analytics only, never for advertising. You can reset or delete your advertising ID at any time in Android Settings → Privacy → Ads.

Google may process this data on servers outside your country. [Google's Privacy Policy](https://policies.google.com/privacy) explains how Google handles it.

## 8. Android device backup

The App allows Android's built-in backup and device-to-device transfer. If you have them turned on, the App's local data (rules, schedules, history, settings and trusted senders, which can include phone numbers and message text) may be included in your own encrypted backup in your Google account. Your Gmail app password cannot be recovered from such a backup (see Section 2). This is controlled by Android, not by us: manage it in Android Settings → System → Backup.

## 9. Settings export file

Separately from Gmail backup, Settings → Export Configurations creates a file with your rules, scheduled messages (including their text and phone numbers) and settings. It **never includes your Gmail address or app password**. The file is created on your phone and handed to Android's share sheet, and **you** choose where it goes. We never receive a copy. Import Configurations reads a file you pick back into the App.

## 10. Update check (GitHub version only)

The GitHub version checks whether a newer release has been published, so it can tell you about it. When you open the App, at most once every 24 hours, it asks GitHub's public API (`api.github.com`) for the latest release of this repository. If you tap **Download and install**, it downloads the APK from GitHub and hands it to Android's installer, which asks you to confirm.

These requests carry no information about you, your messages or your settings. Like any connection to a website, GitHub sees your IP address and a generic app identifier (`SMS-Toolbox-Android`), under [GitHub's privacy statement](https://docs.github.com/site-policy/privacy-policies/github-general-privacy-statement). The App remembers on your phone only when it last checked and which version was the latest. The Google Play version does none of this.

## 11. Permissions and why the App needs them

Each permission is requested only when a feature needs it, and none of them gives **us** access to your data.

| Permission | Why |
|---|---|
| Default SMS app (role) | Be your messaging app: receive SMS and MMS, save them and the messages you send, and restore backups. Requested first, before any SMS permission. Android grants the SMS permissions below together with it. Messages and Restore need it; the other features can work without it |
| `READ_SMS` / `RECEIVE_SMS` | Granted with the default SMS app role, or asked for on its own when you first use Automate, Schedule or Backup: in the Google Play version only if you declined the role, in the GitHub version directly. Show your conversations, receive new messages, back them up to Gmail, and check them against your automation rules |
| `SEND_SMS` | Send the messages you write, forward and schedule |
| `RECEIVE_MMS` / `RECEIVE_WAP_PUSH` | Receive picture and group messages (MMS) |
| `READ_CELL_BROADCASTS` | Granted only with the default SMS app role. Android checks it to confirm that the App really is your default SMS app; the App does not read cell broadcasts (emergency alerts) |
| `VIBRATE` | Vibrate when a phone-action rule asks for it |
| `MODIFY_AUDIO_SETTINGS` | Raise the alarm volume for a ring and put it back afterwards, and change the sound mode when a rule asks for it |
| Do Not Disturb access (`ACCESS_NOTIFICATION_POLICY`) | Only if you create a rule that changes the sound mode, and only after you allow it in Android's settings: switch the ringer to or from silent |
| `REQUEST_INSTALL_PACKAGES` (GitHub version only) | Hand a downloaded update to Android's installer, which asks you to confirm |
| `READ_CONTACTS` | Show names instead of numbers in conversations, and let you pick a contact for a rule or a scheduled message |
| `POST_NOTIFICATIONS` | Notify you of new messages, show backup and restore progress, and confirm forwarded and scheduled messages |
| `INTERNET` / `ACCESS_NETWORK_STATE` | Connect to Gmail for backup and restore, and wait for a connection before trying |
| Battery optimization exemption | Stop Android from delaying messages, backups, forwards and scheduled sends |
| `AD_ID` | Used by Firebase Analytics (see Section 7); never for advertising |

## 12. What we do not do

- We do not run a server, and there is no account or login.
- We do not receive your messages, contacts, Gmail address or app password.
- We do not include advertising SDKs or show ads.
- We do not sell, rent or share your data for marketing.

## 13. Security

Message notifications are hidden on the lock screen by default. Gmail connections use TLS with server identity checks. Your app password is encrypted with a key held in the Android Keystore. The App never logs message content or credentials. The backup record keeps only one-way hashes, not message text.

## 14. Keeping and deleting data

Data on your phone stays until you delete it in the App, clear the App's storage or uninstall it, subject to any Android device backup you have turned on (Section 8). Your SMS and MMS messages stay in your phone's message storage until you delete them, also after uninstalling the App, as with any messaging app. Backups in Gmail stay until you delete them in Gmail. Firebase data is kept by Google under its own retention policies, and we do not link it to your identity.

## 15. Your choices and rights

- **Default SMS app:** choose another default SMS app in Android Settings → Apps → Default apps at any time.
- **Permissions:** revoke any permission in Android Settings. The feature that needs it stops working until you grant it again, and the rest of the App keeps working. If background running is not allowed, the App shows a banner until you allow it.
- **Gmail backup:** turn it off on the Backup tab, remove your credentials in Settings, or revoke the app password in your Google account.
- **History:** turn off Keep history or Keep schedule history to stop recording them, or clear them at any time.
- **Advertising ID:** reset or delete it in Android Settings → Privacy → Ads.
- **Everything:** uninstall the App to remove all of its data from your phone.

Depending on where you live (for example under the GDPR or the CCPA), you may have rights to access, correct, export or delete your data. Everything the App stores is already on your phone and under your control. For questions about the diagnostic data processed by Firebase, contact us using the details below.

## 16. Children

The App is not directed at children under 13, and we do not knowingly collect personal data from children.

## 17. Changes to this policy

We may update this policy as the App changes. Updates are published at this same address with a new effective date.

## 18. Contact

Questions about this policy or your data? Email **play-store-neteinstein@googlegroups.com**.
