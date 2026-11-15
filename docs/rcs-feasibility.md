# RCS feasibility for SMS Toolbox

*Research document, no app code changes. All sources accessed 2026-11-15 unless noted.*

## Verdict

**SMS Toolbox cannot add RCS today, and there is no announced path to it.** Android's platform RCS
APIs (IMS single registration) only serve an app that is **both** the default SMS app **and** a
*privileged app preinstalled on the system image*. A Play or GitHub APK can never be the second. The
older alternative, an app running its own independent IMS stack ("dual registration"), is not blocked
by Android permissions, but it needs the carrier (or Google's Jibe) to provision that client. There is
no public route for a third-party client to get that provisioning. Google
offers no public RCS API to third-party messaging apps; the one known Google Messages API is
allowlisted to Samsung. Every third-party app with "RCS" (Beeper, Messages by OzarksMade) gets it by
driving Google Messages, which must stay installed and in charge of RCS, and some of them also need a
server. **Recommendation: (d) do nothing on RCS and document why, plus (c′) a small set of serverless
SMS/MMS improvements (delivery and read reports) that use carrier features we already have.**

## 1. Can a third-party default SMS app do RCS?

### Android platform APIs

| API | What it does | Who can call it |
|---|---|---|
| `SipDelegateManager` (IMS single registration, Android 12+) | Lets an RCS app attach its RCS feature tags to the device's IMS registration and send/receive SIP: the actual RCS transport | Needs `android.permission.PERFORM_IMS_SINGLE_REGISTRATION`, granted only if the app is **a privileged (preinstalled) app _and_ the default SMS role holder**. If either is missing, the permission is denied. [AOSP: IMS single registration][aosp-sr] |
| `ProvisioningManager` RCS provisioning | Lets an OEM/carrier app push RCS provisioning when the carrier uses its own entitlement server | OEM/carrier apps. [AOSP: IMS single registration][aosp-sr] |
| `ImsRcsManager` / `RcsUceAdapter` capability exchange | Asks whether a contact is RCS-capable | Requesting capabilities needs `ACCESS_RCS_USER_CAPABILITY_EXCHANGE`, again **privileged + default messaging/dialer/contacts role**. [AOSP: IMS single registration][aosp-sr] The public SDK part only offers registration state (`READ_PRECISE_PHONE_STATE` or carrier privileges) and one opt-in check, `isUceSettingEnabled()`. [ImsRcsManager reference][imsrcs], [RcsUceAdapter reference][uce] |
| IMS APN via `ConnectivityManager` | Network path to the IMS core | `CONNECTIVITY_USE_RESTRICTED_NETWORKS` (signature/privileged). [AOSP: IMS single registration][aosp-sr] |

The single registration framework commit lists `createSipDelegate`, `setRcsClientConfiguration` and
`triggerRcsReconfiguration` as gated by that permission
([AOSP commit mirror][commit]). AOSP states that third-party apps therefore cannot use the single
registration APIs ([AOSP: IMS single registration][aosp-sr]).

AOSP also describes the older **dual registration** model (Android 11 and lower), in which RCS apps
*"manage their own IMS stack and connection to the carrier's network"* and set up their own
registration ([AOSP: IMS single registration][aosp-sr]). Android permissions do not forbid this for a
store app. What blocks it is provisioning: the client must be configured by the carrier's RCS
auto-configuration server or by Jibe, and Jibe provisions Google's client ([Jibe][jibe]). I found no
public program that provisions independent third-party P2P clients. It also needs a full
SIP/MSRP/Universal Profile stack and, for E2EE, MLS.

Being the default SMS app, which SMS Toolbox already is, satisfies only half of the platform-API condition. Carrier
privileges (a certificate in the SIM's access rules) are a separate mechanism, which a carrier grants
only to its own apps. Even with them, the RCS calls above still need the privileged-app half.

### Google's RCS platform and APIs

- **Jibe** is Google's carrier-side RCS cloud and hub. Carriers use it to run RCS, and Google ships the
  Android client (Google Messages) ([Jibe][jibe]). It is not a developer API.
- **RCS Business Messaging (RBM)** is for verified businesses sending A2P/P2A messages through
  registered "agents" ([Google RBM: register as a partner][rbm], [MoEngage RBM prerequisites][moengage]).
  It cannot carry a user's person-to-person chats and is not a client API.
- **Google Messages "Android Messages API"**: a hidden API that lets an app send SMS/MMS/RCS
  through Google Messages, behind `com.google.android.apps.messaging.EXTERNAL_MESSAGING_API`. XDA's
  update says it is *"currently not intended for use by third-party apps"* and is restricted to an
  allowlist of Samsung apps ([XDA, 2021-07-09][xda]).
- **Android 15/16/17**: I found no Google announcement of an RCS API for third-party apps. Searches
  for one turned up only vendor blogs and forum posts. One vendor blog says Android 17 *narrows*
  RCS access to the default messaging app ([PhoneLeash blog][phoneleash], unverified, commercial
  source). Consumer coverage in 2026 still describes Google Messages as the only Android app that can
  send RCS ([MakeUseOf][muo]).

### GSMA Universal Profile and E2EE

- UP 3.0 (March 2025) adds end-to-end encryption using IETF **MLS**. iOS 26.5 and Google Messages
  shipped cross-platform E2EE RCS in May 2026, on carriers that support UP 3.0. Typing indicators are
  not encrypted ([Privacy Guides, 2026-05-05][pg-e2ee], [Infosecurity Magazine][infosec]).
- The profile is an open spec, but implementing it does not help: the problem is reaching the carrier's
  IMS core and RCS provisioning, not the protocol. An independent client would also need its own MLS
  and key-distribution implementation that interoperates with Google's and Apple's.

## 2. What other apps do

| App | RCS? | How |
|---|---|---|
| **Samsung Messages** | Had RCS (preinstalled, privileged, Samsung-allowlisted). **Discontinued**: Samsung announced in April 2026 that it was ending the app and moved users to Google Messages. It ended in July 2026 ([Engadget][engadget], [Cybernews][cybernews], [Samsung Community][samsung]). | System app, so the privileged path above |
| **Google Messages** | Yes | Preinstalled/privileged on most devices, Jibe or carrier RCS. Google's own troubleshooting says to make it the default SMS app for RCS ([Google Messages Help][gm-help]) |
| **Messages by OzarksMade** (2026) | Only in "Companion Mode" | Uses Google Messages *"as a background engine"*. Third-party developers *"cannot directly access RCS networks"*. Google Messages must stay installed and running ([Notebookcheck, 2026-07-29][nbc], [Android Headlines][ah]) |
| **Beeper** | Yes | A Matrix bridge (`mautrix-gmessages`) to Google Messages' web pairing. *"All messages are proxied through the app, your phone must be connected"*, and QR pairing *"appears to no longer work"* ([mautrix docs][mau-auth], [mautrix/gmessages][mau]). Needs Google Messages + a bridge server |
| **Textra, Pulse, QKSMS, Fossify Messages** | No | No API. Community threads say there is no path for Fossify ([Lemmy discussion][lemmy], [Fossify issues][fossify]). Older reports say Beeper was told there is no public RCS API ([Hacker News][hn]) |

## 3. Permissions and Play policy

- **Permissions a platform-integrated RCS client needs** (`PERFORM_IMS_SINGLE_REGISTRATION`,
  `ACCESS_RCS_USER_CAPABILITY_EXCHANGE`, `CONNECTIVITY_USE_RESTRICTED_NETWORKS`) are privileged. A
  Play-distributed or sideloaded APK cannot be granted them on a stock device
  ([AOSP: IMS single registration][aosp-sr]). Declaring them does nothing. An independent-stack client
  over ordinary internet needs no special permission, but it still needs carrier/Jibe provisioning
  (see §1).
- **Play SMS & Call Log policy** does not mention RCS. It covers the SMS permissions we already hold under
  "Default SMS handler", and requires a new Permissions Declaration whenever the use of restricted
  permissions changes ([Play policy][play]). A Google Messages handoff or "companion" design would mean
  giving up the default SMS role (see below), which removes our declared core use case. Play would then
  require us to drop SMS permissions or re-declare.
- **Don't reverse-engineer Google Messages** (the Beeper/OzarksMade route). It depends on undocumented
  behaviour Google has already broken once (QR pairing), and it puts a Google app between the user and
  their messages, which conflicts with this app's "no intermediary" promises.

## 4. Options for SMS Toolbox, ranked

### (d) Do nothing on RCS and document why: **recommended**
- **Effort:** this document, plus a short FAQ line on the store listing / `docs/index.md` (follow-up).
- **Risk to understand and tell users:** when SMS Toolbox is the default app, Google Messages is not,
  so RCS stops on this phone. Contacts' RCS clients may keep trying RCS to the number for a while. The
  usual guidance is to turn off Google Messages' *Chat features* before switching, or use Google's online
  deregistration. Reports say RCS can otherwise linger for up to 8 days
  ([Droid Life][droidlife], [Android Central][ac-dereg]). **Follow-up:** show this hint in the
  "Set as default" flow (code change, separate PR).
- **Policy/data-safety:** no change.

### (c′) Serverless upgrades over the carrier channel we already use: **recommended, small**
These are SMS/MMS standards, not RCS, so they work with every phone and add no server:
- **SMS delivery reports:** `AndroidMessageSender` passes `null` delivery intents
  (`app/src/main/java/com/neteinstein/smsredirect/data/messaging/AndroidMessageSender.kt:92`). Wiring
  them gives a "Delivered" tick where the carrier supports it. Carrier config exposes
  `KEY_MMS_SMS_DELIVERY_REPORT_ENABLED_BOOL` ([CarrierConfigManager][ccm]).
- **MMS delivery and read reports:** the composer hard-codes `X-Mms-Delivery-Report` and
  `X-Mms-Read-Report` to "No" (`domain/mms/MmsPduComposer.kt:40-41`). The provider already has
  `d_rpt`, `rr`, `read_status` and `rpt_a` columns ([Telephony.BaseMmsColumns][mmscols]). Carrier keys:
  `KEY_MMS_MMS_DELIVERY_REPORT_ENABLED_BOOL` and `KEY_MMS_MMS_READ_REPORT_ENABLED_BOOL` ([CarrierConfigManager][ccm]).
  Sending a read report (`M-Read-Rec.ind`) needs a new PDU type and a round-trip test in `MmsPduTest`.
  It should be **opt-in**, because it tells the sender when you read their message.
- **Larger media within the carrier limit: already done.** `AndroidMessageSender` reads the carrier's
  `MMS_CONFIG_MAX_MESSAGE_SIZE` and budgets/compresses attachments to it, falling back to a fixed size
  only when the carrier gives none (`AndroidMessageSender.kt:110-113`, `buildParts()`). Nothing to add.
- **Effort:** about 2–4 dev-days with tests. **Risk:** carrier support varies, and some carriers charge
  for or ignore reports.
- **Policy/data-safety:** if read reports are added, `PrivacyPolicy.md` gains one line: when you turn
  them on, opening an MMS sends a read report to the sender through your carrier. The data-safety form is
  unchanged, since nothing reaches us or a third party other than the carrier.

### (c) Typing indicators, E2EE or high-res media over "our own data channel": **not feasible**
- Real-time features between two phones need a rendezvous/relay (push or a server). Both phones would
  also need to run SMS Toolbox. That would break *"we run no server"* in `PrivacyPolicy.md` and
  require new **Data collected/shared** entries in `store-listing/data-safety.md` (phone numbers,
  message metadata or content in transit). E2EE over SMS (Signal's old TextSecure approach) is
  serverless, but only between SMS Toolbox users, and it needs key management. That is a separate
  product decision, not RCS.

### (b) Interop/handoff to Google Messages: **not recommended**
- *"Open this conversation in Google Messages"* is just an intent. But RCS there only works while Google
  Messages is the default app, so users would be switching defaults back and forth, and SMS Toolbox
  would lose `SMS_DELIVER` and the Messages tab. That undermines the "Default SMS handler" declaration
  and the restore/write-what-you-send invariants in `CLAUDE.md`.
- A "companion" design like OzarksMade's relies on undocumented Google Messages behaviour. Changes to
  `PrivacyPolicy.md` and `data-safety.md` would be needed to say messages pass through Google
  Messages/Google's RCS servers.

### (a) Native RCS client: **not feasible**
- **Platform-integrated** (single registration): blocked by the privileged-app requirement
  ([AOSP][aosp-sr]). The only routes are an OEM/carrier preload, or a custom ROM where the app is
  installed in `/system/priv-app`. That build no Play user could run.
- **Independent IMS stack** (dual registration): not blocked by permissions, but it needs each
  carrier, or Google's Jibe, to provision SMS Toolbox as an RCS client ([Jibe][jibe]). There is no
  public route for that; it would take an arrangement with each carrier.
- Either way, the effort would be months (SIP/MSRP, UP provisioning, MLS).

## Sources

[aosp-sr]: https://source.android.com/docs/core/connect/ims-single-registration
[imsrcs]: https://developer.android.com/reference/android/telephony/ims/ImsRcsManager
[uce]: https://developer.android.com/reference/android/telephony/ims/RcsUceAdapter
[commit]: https://gerrit.omnirom.org/plugins/gitiles/android_frameworks_base/+/29dfa1cd76135b2e6ca8fd9a9fb4416a7baf6578%5E%21
[jibe]: https://jibe.google.com/jibe-platform
[rbm]: https://developers.google.com/business-communications/rcs-business-messaging/guides/get-started/register-partner?hl=en
[moengage]: https://help.moengage.com/hc/en-us/articles/35614044893460-Prerequisites
[xda]: https://www.xda-developers.com/google-messages-rcs-api-third-party-apps/
[phoneleash]: https://phone-leash.com/blog/android-17-rcs-forwarding
[muo]: https://www.makeuseof.com/google-messages-is-the-only-android-app-allowed-to-send-rcs-and-that-should-worry-you/
[pg-e2ee]: https://www.privacyguides.org/news/2026/05/05/apple-confirms-rcs-e2ee-will-ship-with-ios-26-5/
[infosec]: https://www.infosecurity-magazine.com/news/apple-google-rcs-end-to-end/
[engadget]: https://engadget.com/mobile/smartphones/samsung-will-discontinue-its-messages-app-in-july-and-replace-it-with-googles-162204307.html
[cybernews]: https://cybernews.com/tech/samsung-messages-discontinued/
[samsung]: https://us.community.samsung.com/t5/Samsung-Apps-and-Services/NEWS-Samsung-Messages-is-officially-being-discontinued/m-p/3522800
[gm-help]: https://support.google.com/messages/answer/9487020?hl=en
[nbc]: https://www.notebookcheck.net/Samsung-Messages-lives-on-thanks-to-this-unofficial-RCS-app.1354769.0.html
[ah]: https://www.androidheadlines.com/2026/07/ozarksmade-app-recreates-samsung-messages-one-ui-rcs.html
[mau-auth]: https://docs.mau.fi/bridges/go/gmessages/authentication.html
[mau]: https://github.com/mautrix/gmessages
[lemmy]: https://lemmy.nekusoul.de/post/427541
[fossify]: https://github.com/FossifyOrg/Messages/issues
[hn]: https://news.ycombinator.com/item?id=35979656
[play]: https://support.google.com/googleplay/android-developer/answer/10208820
[droidlife]: https://www.droid-life.com/2021/07/20/how-to-disable-rcs-chat-android/
[ac-dereg]: https://www.androidcentral.com/heres-how--deregister-your-phone-number-google-from-rcs-chat
[ccm]: https://developer.android.com/reference/android/telephony/CarrierConfigManager
[mmscols]: https://developer.android.com/reference/android/provider/Telephony.BaseMmsColumns

| Source | Date | Notes |
|---|---|---|
| [AOSP: IMS single registration][aosp-sr] | no page date; accessed 2026-11-15 | Primary: privileged + default SMS requirement |
| [ImsRcsManager][imsrcs], [RcsUceAdapter][uce], [Telephony.BaseMmsColumns][mmscols], [CarrierConfigManager][ccm] | accessed 2026-11-15 | Primary: Android SDK reference |
| [AOSP framework commit (mirror)][commit] | Android 12 era; accessed 2026-11-15 | API list gated by the permission |
| [Google Play SMS & Call Log policy][play] | accessed 2026-11-15 | No RCS mention |
| [Google Messages Help: RCS chats][gm-help] | accessed 2026-11-15 | Default-app troubleshooting step |
| [Jibe platform][jibe], [Google RBM partner registration][rbm] | accessed 2026-11-15 | Primary (Google) |
| [XDA hidden API][xda] | 2021-07-09; accessed 2026-11-15 | Samsung-only allowlist |
| [Notebookcheck on OzarksMade][nbc] | 2026-07-29 (upd. 2026-10-05); accessed 2026-11-15 | Companion Mode uses Google Messages |
| [mautrix-gmessages auth docs][mau-auth] | accessed 2026-11-15 | Beeper's bridge mechanism |
| [Privacy Guides on iOS 26.5 E2EE][pg-e2ee] | 2026-05-05; accessed 2026-11-15 | UP 3.0 / MLS |
| [Engadget][engadget], [Cybernews][cybernews], [Samsung Community][samsung] | 2026; accessed 2026-11-15 | Samsung Messages discontinued July 2026 |
| [PhoneLeash][phoneleash], [MakeUseOf][muo], [Lemmy][lemmy], [Hacker News][hn], [Droid Life][droidlife], [Android Central][ac-dereg] | various; accessed 2026-11-15 | Secondary/community, used only for corroboration |
