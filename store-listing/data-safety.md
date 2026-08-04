# Data safety cheat-sheet (Play Console → App content → Data safety)

This is a starting point for the **Data safety** questionnaire, derived from
[`PrivacyPolicy.md`](../PrivacyPolicy.md) and the app's actual code. It is
**not legal advice** — always complete the live questionnaire in Play Console
yourself; wording and categories change over time.

The key rule Google uses: **"collected" means transmitted off the device**
to the developer or a third party. Data the app only reads/writes locally
(SMS content, contacts) and never transmits is **not** "collected" for this
form, even though the app requests the runtime permission for it (that's
covered separately by the **Permissions declaration form** — see the
README's warning about `SmsAndCallLogPolicy`).

## Suggested answers

**Does your app collect or share any of the required user data types?**
→ Yes (limited to anonymous diagnostics via Firebase — see below).

| Data type | Collected (transmitted off-device)? | Shared with 3rd parties? | Purpose | Notes |
|---|---|---|---|---|
| SMS or MMS content | No | No | — | Read on-device only, to match rules; forwarded messages go out via the *user's own carrier* to the number the user configured — not to a server the developer operates. |
| Contacts | No | No | — | Used only for the in-app contact picker and name resolution; never transmitted. |
| Phone number / Device IDs | Only via Firebase SDKs (see below) | No (processed by Firebase under Google's data processing terms, not sold/shared for ads) | Analytics, Crash reporting | Firebase Analytics/Crashlytics may include an installation/instance identifier. No advertising ID use in this app. |
| App activity (app interactions) | Yes, anonymized | No | Analytics | Rule created/updated/deleted, SMS forwarded, scheduled SMS sent, setting toggled — see `domain/analytics/AnalyticsEvents.kt`. No phone numbers, names, or message text included. |
| Crash logs / diagnostics | Yes | No | Crash reporting (Crashlytics) | Device model, OS version, stack traces. |
| Location | No | No | — | App never requests location permissions. |
| Personal info (name/email/etc.) | No | No | — | No account/login system exists. |
| Financial info | No | No | — | Not applicable. |
| Photos, videos, audio, files | No | No | — | Not applicable (Export/Import Backup is a user-initiated local file the app writes/reads via the share sheet — it never uploads anywhere itself). |

## Security practices section

- **Data encrypted in transit:** Yes for the data that does leave the device
  (Firebase Analytics/Crashlytics traffic is HTTPS). Not applicable to
  everything else, since it never leaves the device.
- **Users can request data deletion:** Not applicable in the traditional
  sense — there's no account/server-side record. Users have full local
  control: delete/edit rules & schedules in-app, clear app storage, or
  uninstall. Mention this in the "data deletion" free-text field if Play
  Console asks for it.
- **Committed to the Play Families Policy / independent security review:**
  Answer per your actual practices; not assessed here.

## Firebase's own disclosure guidance

Google/Firebase publish official per-SDK Data safety mappings — cross-check
against the current guidance before submitting, since default data collected
by Analytics/Crashlytics has changed over SDK versions:
<https://firebase.google.com/docs/android/play-data-disclosure>
