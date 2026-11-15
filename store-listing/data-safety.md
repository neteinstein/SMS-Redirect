# Data safety cheat-sheet (Play Console → App content → Data safety)

This is a starting point for the **Data safety** questionnaire, derived from
[`PrivacyPolicy.md`](../PrivacyPolicy.md) and the app's actual code. It is
**not legal advice** — always complete the live questionnaire in Play Console
yourself; wording and categories change over time.

**This form covers the Play build only.** The app ships in two flavors from one
codebase (see `app/build.gradle.kts`): the **play** flavor uploaded to Google Play
is the SMS/MMS messaging app (declared as the **Default SMS handler**) with
Redirect, Schedule, Backup and Restore, and no self-update; the **github** flavor
is an APK attached to GitHub releases that additionally checks GitHub for updates.
Only the Play build is distributed through Play, so only its behaviour is declared
here. The GitHub build's update check is covered by [`PrivacyPolicy.md`](../PrivacyPolicy.md).

The key rule Google uses: **"collected" means transmitted off the device**
to the developer or a third party. Data the app only reads/writes locally
(contacts) and never transmits is **not** "collected" for this form, even
though the app requests the runtime permission for it (that's covered
separately by the **Permissions declaration form** — see the README's warning
about `SmsAndCallLogPolicy`).

**Being the default SMS app does not by itself change these answers.** The
messaging feature reads, shows and stores SMS/MMS in Android's own message
store on the device. Messages the user sends go, at the user's request, to the
recipients the user picked, through the carrier: Google's definition of
"sharing" excludes transfers the user initiates and expects, so this is neither
collection nor sharing. The same holds for photos/videos/audio the user attaches
to an MMS and for incoming MMS, which Android's MMS service downloads from the
carrier's MMSC onto the device. Nothing in the messaging feature reaches the
developer or Firebase: notifications are local, and no analytics event or crash
report carries a number, name, attachment or message text.

**Delivery and read reports do not change these answers either.** They are off by default,
turned on by the user in Settings → Message receipts, and travel between the user's phone, the
carrier and the other party (user-initiated, like the messages). An MMS read report tells the
sender when the user read their message; it never reaches the developer.

⚠️ **The Gmail backup feature changes this answer for SMS content.** When a
user enables it, SMS content *is* transmitted off the device. The destination
is the user's own Gmail account, which the developer neither controls nor can
read, and Google's guidance is arguably that this is not "collection" by the
developer. **Declare it anyway.** An under-declaration that a reviewer
disagrees with is an app-removal risk; an over-declaration is not. The rows
below take the conservative reading.

**Backing up sent SMS does not change these answers.** Gmail backup covers
messages the user sent as well as received ones. Both are the same data type
("SMS or MMS content") going to the same destination, so the row below
already covers them.

**Restore from Gmail does not change these answers.** A restore moves data *onto*
the device: from the user's own Gmail label into the phone's SMS store, over
read-only IMAP. Nothing new is transmitted off the device. The backup emails
now also carry a few `X-SMS-*` headers (sender number and the exact
received/sent timestamps). These repeat what the email body already showed and
go to the same destination, so the "SMS or MMS content" row below already
covers them.

## Suggested answers

**Does your app collect or share any of the required user data types?**
→ Yes — anonymous diagnostics via Firebase, plus SMS content and an email
address when the user enables Gmail backup.

| Data type | Collected (transmitted off-device)? | Shared with 3rd parties? | Purpose | Notes |
|---|---|---|---|---|
| SMS or MMS content | **Yes — only when the user enables Gmail backup** | No | App functionality | Uploaded over TLS (IMAPS) to **the user's own Gmail account**, using an app password the user supplies. Not to any server the developer operates, and the developer cannot read it. Off by default. |
| Contacts | No | No | — | Used on-device to show names in conversations and notifications, and to put a contact's name in a backup email; never transmitted to the developer. |
| Device or other IDs | **Yes**, via Firebase SDKs | No (processed by Firebase under Google's data processing terms, not sold/shared for ads) | Analytics, Crash reporting | Firebase attaches an installation identifier, and Firebase Analytics may read the **advertising ID**: the manifest declares `com.google.android.gms.permission.AD_ID` for it. It is used for analytics only, never ads. Declare it; leaving it out while the permission is in the manifest is a mismatch Play flags. To stop collecting it instead, remove the `AD_ID` permission (or set `google_analytics_adid_collection_enabled` to `false`) and update this row and `PrivacyPolicy.md` §7. |
| Phone number | No | No | — | Phone numbers leave the device only as the recipients of messages the user sends (to the carrier, user-initiated) and inside the user's own Gmail backup. |
| App activity (app interactions) | Yes | No | Analytics | A setting was changed (for example Gmail backup turned on or off) — see `domain/analytics/AnalyticsEvents.kt`. No phone numbers, names, or message text included. |
| Crash logs / diagnostics | Yes | No | Crash reporting (Crashlytics) | Device model, OS version, stack traces. |
| Location | No | No | — | App never requests location permissions. |
| Personal info — email address | **Yes — only when the user enables Gmail backup** | No | App functionality, Account management | The user's own Gmail address, entered by them. Stored on-device (the app password alongside it is encrypted with an Android Keystore key) and transmitted only to Google as the IMAP login. Never sent to the developer. |
| Personal info (name, other profile data) | No | No | — | No account/login system operated by the developer exists. |
| Financial info | No | No | — | Not applicable. |
| Photos, videos, audio, files | No | No | — | Only as MMS attachments the user picks and sends to recipients they chose (user-initiated, via the carrier), or receives by MMS. Stored only in Android's message store on the device; never sent to the developer. |

## Security practices section

- **Data encrypted in transit:** Yes. Firebase Analytics/Crashlytics traffic
  is HTTPS; Gmail backup uses IMAP over TLS on port 993 with server identity
  checking on. Everything else never leaves the device.
- **Users can request data deletion:** Yes, and say how. There is still no
  developer-side account or server record. Users have full control: delete
  the Gmail label (or individual messages) in their own Gmail account; revoke
  the app password at myaccount.google.com/apppasswords, which immediately
  cuts the app's access; remove the credentials in-app via Settings → Gmail
  backup → Remove; clear app storage or uninstall. Put this in the "data deletion" free-text field.
- **Committed to the Play Families Policy / independent security review:**
  Answer per your actual practices; not assessed here.

## Firebase's own disclosure guidance

Google/Firebase publish official per-SDK Data safety mappings — cross-check
against the current guidance before submitting, since default data collected
by Analytics/Crashlytics has changed over SDK versions:
<https://firebase.google.com/docs/android/play-data-disclosure>
