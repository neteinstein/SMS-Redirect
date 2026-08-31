# Data safety cheat-sheet (Play Console → App content → Data safety)

This is a starting point for the **Data safety** questionnaire, derived from
[`PrivacyPolicy.md`](../PrivacyPolicy.md) and the app's actual code. It is
**not legal advice** — always complete the live questionnaire in Play Console
yourself; wording and categories change over time.

The key rule Google uses: **"collected" means transmitted off the device**
to the developer or a third party. Data the app only reads/writes locally
(contacts) and never transmits is **not** "collected" for this form, even
though the app requests the runtime permission for it (that's covered
separately by the **Permissions declaration form** — see the README's warning
about `SmsAndCallLogPolicy`).

⚠️ **The Gmail backup feature changes this answer for SMS content.** When a
user enables it, SMS content *is* transmitted off the device. The destination
is the user's own Gmail account, which the developer neither controls nor can
read, and Google's guidance is arguably that this is not "collection" by the
developer. **Declare it anyway.** An under-declaration that a reviewer
disagrees with is an app-removal risk; an over-declaration is not. The rows
below take the conservative reading.

## Suggested answers

**Does your app collect or share any of the required user data types?**
→ Yes — anonymous diagnostics via Firebase, plus SMS content and an email
address when the user enables Gmail backup.

| Data type | Collected (transmitted off-device)? | Shared with 3rd parties? | Purpose | Notes |
|---|---|---|---|---|
| SMS or MMS content | **Yes — only when the user enables Gmail backup** | No | App functionality | Uploaded over TLS (IMAPS) to **the user's own Gmail account**, using an app password the user supplies. Not to any server the developer operates, and the developer cannot read it. Off by default. Separately, rule-matched messages are forwarded via the *user's own carrier* to a number the user configured — also not via a developer server. |
| Contacts | No | No | — | Used only for the in-app contact picker and name resolution; never transmitted. |
| Phone number / Device IDs | Only via Firebase SDKs (see below) | No (processed by Firebase under Google's data processing terms, not sold/shared for ads) | Analytics, Crash reporting | Firebase Analytics/Crashlytics may include an installation/instance identifier. No advertising ID use in this app. |
| App activity (app interactions) | Yes, anonymized | No | Analytics | Rule created/updated/deleted, SMS forwarded, scheduled SMS sent, setting toggled — see `domain/analytics/AnalyticsEvents.kt`. No phone numbers, names, or message text included. |
| Crash logs / diagnostics | Yes | No | Crash reporting (Crashlytics) | Device model, OS version, stack traces. |
| Location | No | No | — | App never requests location permissions. |
| Personal info — email address | **Yes — only when the user enables Gmail backup** | No | App functionality, Account management | The user's own Gmail address, entered by them. Stored on-device (the app password alongside it is encrypted with an Android Keystore key) and transmitted only to Google as the IMAP login. Never sent to the developer. |
| Personal info (name, other profile data) | No | No | — | No account/login system operated by the developer exists. |
| Financial info | No | No | — | Not applicable. |
| Photos, videos, audio, files | No | No | — | Not applicable (Export/Import Backup is a user-initiated local file the app writes/reads via the share sheet — it never uploads anywhere itself). |

## Security practices section

- **Data encrypted in transit:** Yes. Firebase Analytics/Crashlytics traffic
  is HTTPS; Gmail backup uses IMAP over TLS on port 993 with server identity
  checking on. Everything else never leaves the device.
- **Users can request data deletion:** Yes, and say how. There is still no
  developer-side account or server record. Users have full control: delete
  the Gmail label (or individual messages) in their own Gmail account; revoke
  the app password at myaccount.google.com/apppasswords, which immediately
  cuts the app's access; remove the credentials in-app via Settings → Gmail
  backup → Remove; delete/edit rules & schedules in-app; clear app storage or
  uninstall. Put this in the "data deletion" free-text field.
- **Committed to the Play Families Policy / independent security review:**
  Answer per your actual practices; not assessed here.

## Firebase's own disclosure guidance

Google/Firebase publish official per-SDK Data safety mappings — cross-check
against the current guidance before submitting, since default data collected
by Analytics/Crashlytics has changed over SDK versions:
<https://firebase.google.com/docs/android/play-data-disclosure>
