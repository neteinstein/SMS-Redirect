# SMS Toolbox — Architecture

## Overview

SMS Toolbox is an Android SMS/MMS messaging app with a toolbox around it, presented as five bottom-navigation tabs on the home screen, in this order:

- **Messages** — a full messaging client used as the device's default SMS app: conversation list, conversations, sending and receiving SMS and MMS (pictures, group messages), notifications with reply. This is the app's primary feature and the landing tab.
- **Automate** (the tab formerly called Redirect; internally still `REDIRECT` / `HomeScreen`) — rules matched against every incoming SMS. A rule either **redirects** the message to a configured target number via SMS, or runs a **phone action** on this device: ring at maximum volume with a chosen sound, vibrate in a pattern, or change the sound mode. See "Automation phone actions".
- **Schedule** — lets the user compose a message once and have it sent automatically at a future date/time, optionally repeating on a recurring pattern.
- **Backup** — copies SMS messages to the user's own Gmail account over IMAP.
- **Restore** — copies those backups back into the phone's SMS store.

**Two distributions.** The `distribution` flavor dimension (`app/build.gradle.kts`) builds the app twice from one codebase, with the same `applicationId`, signing key and `versionCode`:

| | `play` (Google Play) | `github` (GitHub release APK) |
|---|---|---|
| Features | All five tabs | All five tabs |
| Self-update | none (Play forbids it; `src/play/AndroidManifest.xml` removes `REQUEST_INSTALL_PACKAGES` as a guard) | daily GitHub release check + in-app install (`REQUEST_INSTALL_PACKAGES`) |
| SMS access for Automate / Schedule / Backup (`smsPermissionsWithoutRole`) | default SMS role offered first; SMS permissions alone only after the user declines it | SMS permissions asked for directly; the role is not required |

Each flavor has a `distribution/DistributionModule` (`src/play/…`, `src/github/…`) exposing an `AppFeatures` (`domain/model/`) and, in the github flavor only, the update source and installer. `AppFeatures` reaches everything through `AppContainer.features`: `MainActivity` filters the tabs, the landing screen and the deep links; `SettingsScreen` hides the Redirect/Schedule settings when neither ships; `SmsReceiver` skips forwarding and `SendScheduledSmsWorker` drops sends when their feature is off. No code outside `DistributionModule` checks the flavor.

All five share the same app shell (permissions flow, Settings screen, deep-link handling) but are otherwise independent end-to-end: separate domain models, separate Room tables, separate use cases, separate WorkManager jobs.

> **A note on the word "backup".** It means two different things in this codebase.
> `ExportBackupUseCase` / `ImportBackupUseCase` predate the Gmail feature and refer to the
> local JSON export of rules, schedules and settings. Everything belonging to the SMS→Gmail
> feature is prefixed `SmsBackup*` or `Gmail*`. Keep that split when adding code.

## Why Messages leads

Google Play's SMS & Call Log Permissions policy only grants `READ_SMS` / `RECEIVE_SMS` /
`SEND_SMS` for a declared core use case. The declared one is **"Default SMS handler"**: SMS
Toolbox is a full SMS/MMS messaging app, and Redirect, Schedule, Backup and Restore are tools
built on top of it. Messages is therefore the first tab and the screen the app opens on — the
declaration has to match what the app visibly is.

Two rules from that policy shape the code:

- **The role is requested before any SMS permission (Play build).** `MainActivity`'s default-SMS gate
  (`requireDefaultSmsApp` / `requireSmsAccess`) shows an explanation, then
  `RoleManager.createRequestRoleIntent(ROLE_SMS)` (the API-29+ form of
  `Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT`, a no-op since Android 10; minSdk is 33). Android
  grants the SMS permissions with the role; only contacts and notifications are asked for after
  it. It runs once on first launch, and again whenever an SMS feature is used without the role.
- **Without the role, Messages and Restore are disabled, the rest works.** Both tabs stay visible
  but greyed out (`DISABLED_ALPHA`), with a warning bar whose "Set as default" action re-runs the
  role request. Automate, Schedule and Backup go through `requireSmsAccess` and run on
  `toolSmsPermissions` (`RECEIVE_SMS`, `READ_SMS`, `SEND_SMS`) alone: `SmsReceiver` listens to
  `SMS_RECEIVED`, which Android sends to any app holding `RECEIVE_SMS`, and `SmsForwarder` /
  `ScheduledSmsSender` fall back to `SmsManager` without the role. How those permissions are
  asked for depends on `AppFeatures.smsPermissionsWithoutRole`:
  - **GitHub build (true):** `requireSmsAccess` requests `toolSmsPermissions` directly, with
    contacts and notifications alongside (optional: only the SMS grants are required); the role
    is not offered. Play's policy does not apply to a sideloaded APK.
  - **Play build (false):** the role is offered first, and only if the user declines it does the
    decline dialog offer "Use SMS permissions only". Once the user has declined Android's role
    dialog (`KEY_DEFAULT_SMS_DECLINED`), `requireSmsAccess` asks for `toolSmsPermissions`
    directly instead of offering the role again.

  Creating or editing a rule or a schedule is never gated: Automate and Schedule show a warning
  banner with "Allow" (which runs `requireSmsAccess`) while those permissions are missing.

## Messaging (SMS/MMS client)

```
[ SMS_DELIVER ] ─► SmsDeliverReceiver ─► MessageStore.saveIncomingSms ─► MessageNotifications
[ WAP_PUSH_DELIVER ] ─► MmsDeliverReceiver ─► AndroidMmsDownloader.onWapPush
                              │  MmsPduParser: M-Notification.ind → content location
                              │  saveMmsNotification: "not downloaded" row (m_type 130, st, ct_l)
                              ▼
                       SmsManager.downloadMultimediaMessage (writes cache/mms/*.pdu via FileProvider)
                              ▼
                       MessageStatusReceiver (ACTION_MMS_DOWNLOADED)
                              ├─► MmsPduParser: M-Retrieve.conf → parts
                              ├─► MessageStore.saveIncomingMms  (content://mms + parts + addr)
                              ├─► delete the "not downloaded" row (or mark it failed → Download button)
                              ├─► M-Acknowledge.ind back to the MMSC
                              └─► MessageNotifications (or "not downloaded" + Retry)

[ Conversation: Send ] ─► SendMessageUseCase ─► MessageSender (AndroidMessageSender)
        text to one person → SMS: outbox row → sendMultipartTextMessage → MessageStatusReceiver → sent/failed
        attachment or group → MMS: MmsPduComposer M-Send.req → outbox row (+parts, addr)
                                   → sendMultimediaMessage → MessageStatusReceiver → M-Send.conf → sent/failed
```

**Store.** Messages live only in the Telephony provider (`content://sms`, `content://mms`,
`content://mms-sms/conversations?simple=true` + `canonical-addresses`), never in Room: a second
copy would change `PrivacyPolicy.md` and the Data safety form. `MessageStore` (domain) /
`ContentProviderMessageStore` (data) is the seam; `observeChanges()` is a `ContentObserver`
flow, so the list and the open conversation refresh on any provider change, whoever made it.
Empty threads (created by "new message" and abandoned) are hidden by `message_count = 0`.

**As the default SMS app, the app writes what it sends.** Android auto-writes sent messages only
for apps that are *not* the default. So `AndroidMessageSender` inserts an outbox row before
sending and moves it to sent/failed from the radio's result. `SmsForwarder` and
`ScheduledSmsSender` send through that same `MessageSender` while the app is the default SMS app,
so a forward or scheduled send that the radio rejects shows as failed rather than sent; when
another app is default they call `SmsManager` directly and the system writes the sent row.

**MMS codec.** The platform's PDU classes are hidden API, and third-party MMS libraries pull in
legacy APN code and their own permissions. `domain/mms/` is a small pure-Kotlin WSP/MMS
encoder/decoder (M-Notification.ind, M-Retrieve.conf, M-Send.req/conf, M-Acknowledge.ind),
bounds-checked (a truncated PDU parses to `null`) and unit-tested by round trips. Note the
`MmsHeaders` value constants are whole octets (high bit set) and are written with
`WspWriter.byte()`, not `shortInteger()`.

**Transport choice** (`requiresMms`, shared by `SendMessageUseCase` and the composer hint): text
to one phone number → SMS; any attachment, more than one recipient (a group conversation, so
replies stay together), or an email recipient → MMS, like the stock Messages app. Recipients are
compared with `MessagingAddresses.same`, which, unlike the restore-time
`SmsBackupKey.sameAddress`, never merges two numbers with different explicit country codes. Images over the carrier's `MMS_CONFIG_MAX_MESSAGE_SIZE` budget are re-encoded as
JPEG (quality, then dimensions) in `AndroidMessageSender`. A group MMS's conversation is the
sender plus the other recipients (`MmsRecipients`); this phone's own number is not known without
`READ_PHONE_NUMBERS`, which the app does not request, so a group can include it.

**Status.** Every send gets an explicit, mutable `PendingIntent` to the non-exported
`MessageStatusReceiver` (mutable so the platform can fill in the MMSC response). Each SMS part
reports; any failure marks the row failed, the last part marks it sent unless already failed.
Failed messages show "Not sent. Tap to retry"; retry resends the same text and attachments
(an MMS's stored parts are read back) and deletes the failed copy once the new one is queued. The
failure notification opens the message's conversation (`threadIdOf`).

**MMS that cannot be downloaded are never lost.** The announcement is saved *before* the download
as a "not downloaded" row in the sender's conversation (`m_type` 130, content location in
`ct_l`, state in `st` with the AOSP values in `MmsDownloadState`), as the stock messaging app
does. The conversation shows it as a "Picture message not downloaded yet" bubble with a
**Download** button (`DownloadMmsUseCase` → `MmsDownloadRequester`), or "Downloading…" while one
is in flight. A successful download saves the real message and deletes the row; a failure marks
it retryable. A second callback for an already-saved placeholder is ignored (`AlreadySaved`), so
a retry racing the first attempt cannot save the message twice. Downloads left "in progress" by a
killed process are reset to retryable on the next start. Each failure also gets its own
notification (keyed by content location) that opens the conversation and has a Retry action;
opening the app does not clear it.

**Delivery and read reports** (Settings → Message receipts, all off by default; stored in
SharedPreferences by `SharedPreferencesMessagingSettingsRepository`, no Room migration):
- *SMS delivery reports*: the last part of each SMS gets a delivery PendingIntent; the row is
  written with `STATUS_PENDING`, and `MessageStatusReceiver` (`ACTION_SMS_DELIVERED`) turns the
  status report's TP-Status into `STATUS_COMPLETE` / `_PENDING` / `_FAILED`
  (`MessageReceipts.smsStatusForReport`).
- *MMS delivery reports*: M-Send.req carries `X-Mms-Delivery-Report: Yes`; the MMSC's
  M-Delivery.ind arrives by WAP push and `ContentProviderMessageStore.recordMmsDelivery` stores
  its X-Mms-Status in the sent row's `st` (one recipient's "deferred" never hides another's
  "retrieved"). The same switch sets `X-Mms-Report-Allowed` in our M-Acknowledge.ind.
- *MMS read reports*: M-Send.req carries `X-Mms-Read-Report: Yes`; M-Read-Orig.ind is stored in
  `read_status`. A received MMS that asks for one keeps `rr = Yes`; when its conversation is
  marked read, `MarkConversationReadUseCase` first has `MmsReadReportSender` send an
  M-Read-Rec.ind for each such unread message. Each report is claimed atomically
  (`read_status` → app-private "sending", so concurrent mark-reads send it once), marked sent
  only when the MMS service confirms it, and otherwise left "retry" so the next mark-read of that
  conversation sends it again; a report "sending" when the process died is reset to retry.
- CDMA status reports carry the bearer error class, not a GSM TP-Status, and are decoded
  separately (`MessageReceipts.smsStatusForCdmaReport`).
- `MessageReceipts` maps the stored values to `MessageReceipt` (Delivered, Not delivered, Read),
  shown next to the time under sent messages.

**Notifications** (`MessageNotifications`): one MessagingStyle notification per thread
(appended to, not replaced), `VISIBILITY_PRIVATE` with a content-free public version, inline
reply (RemoteInput → `SendMessageUseCase`) and mark-as-read. Suppressed while that conversation
is on screen (`visibleThreadId`). `MainActivity.dismissAllNotifications()` leaves them alone.

**Entry points from other apps** (`ComposeSmsActivity`): `SENDTO`/`VIEW` on `sms:`/`smsto:`/
`mms:`/`mmsto:` and `SEND` (text, image, video) open a pre-filled conversation (or New message
when there is no recipient); nothing is sent until the user taps Send. `HeadlessSmsSendService`
sends call-screen quick replies through the same `MessageSender`.

## SMS Backup to Gmail Pipeline

```
[ Incoming SMS ]           [ Any change to content://sms ]     [ Backup tab: "Enable" / "Backup all previous" ]
 │                          │  (a sent SMS turning "sent")       │
 └─► SmsReceiver            └─► SmsBackupProviderWatchWorker     └─► SmsBackupScheduler
      │  (also enqueues          │  (content-URI trigger,              │  (arms the watch too)
      │   ForwardSmsWorker)      │   re-arms itself)                   │
      ▼                          ▼                                     ▼
   BackupSmsWorker  ◄────────────── re-enqueues itself while work remains
      │
      ▼
   RunSmsBackupUseCase
      │
      ├─► SmsMessageSource ──► content://sms, type IN (inbox, sent)  (keyset paging on date,_id)
      ├─► SmsBackupStateRepository ──► filter out anything already dealt with
      │      (nothing pending → stop here, no IMAP connection)
      │
      └─► MailTransport.withFolder(label)   ── one IMAP connection per run
             │
             └─► BackupSmsMessageUseCase ──► SmsMailFactory ──► IMAP APPEND
                        │                    (subject, threading, dates)
                        ▼
                  mark resolved in sms_backup_state
```

**IMAP APPEND, not SMTP.** A Gmail label *is* an IMAP folder, so APPEND can file messages under
the user's chosen label and create it on first use; SMTP can only deliver to the inbox and would
need a server-side filter the app cannot create. APPEND also preserves each message's original
timestamp, and is not subject to Gmail's ~500/day sending cap that a full-history backfill would
trip immediately.

**One source of truth for message identity.** Both the live path and the historical backfill read
the content provider. `SmsReceiver` deliberately passes *nothing* from the broadcast: the broadcast
carries the SMSC timestamp while the provider stores the device receive time, and the two differ by
seconds to minutes. Hashing one against the other would give the same message two identities and
back it up twice. Reading only the provider means one code path and one key.

`SmsBackupKey` is `SHA-256(normalizedAddress | body | dateEpochMs)` for a received message and
`SHA-256("sent" | normalizedAddress | body | dateEpochMs)` for a sent one — not the provider `_id`,
which SQLite reuses after a delete and which does not survive a device restore. Only the hash is
stored, never the message text. The box prefix exists so that texting your own number, which puts
the same address and body in both boxes, can never make one copy look "already backed up". Inbox
keys deliberately keep the original input: changing them would orphan every row in
`sms_backup_state` and re-upload every user's whole inbox. No sent message was backed up before
the prefix existed, so it orphans nothing. A restore marks each message with the key of its own
box, so a restored sent message is not uploaded again.

**Received and sent, never drafts.** The source reads `content://sms` filtered to
`MESSAGE_TYPE_INBOX` and `MESSAGE_TYPE_SENT`, in one ordered scan, and tags each row with its
`SmsBox`. Drafts, outbox, queued and failed rows are excluded: they were never delivered, and an
outgoing message qualifies once the messaging app updates its row to "sent". MMS is not backed up
(the backup format is SMS-only; see "Backup format").

### Components

#### 1. SmsReceiver (shared with Redirect)
- After enqueueing `ForwardSmsWorker`, also calls `BackupSmsWorker.enqueueForIncomingSms(context)`
- Unconditional: backup must happen for every incoming SMS regardless of whether a rule matched.
  `SmsForwarder` early-returns on missing `SEND_SMS`, on roaming and on no rule match — none of
  those should stop a backup
- A ~10s initial delay gives the default SMS app time to write the row to the provider; our
  receiver and that write race each other

#### 2. BackupSmsWorker (CoroutineWorker)
- Unique work `sms_backup`. `KEEP` coalesces the burst from several messages arriving together into
  one run; a continuation re-enqueue uses `REPLACE` so it actually takes effect
- `NetworkType.CONNECTED` constraint, exponential backoff from 30s, capped at 5 attempts
- **Not expedited** — expedited work has a per-app quota and ignores network constraints, and email
  backup is not time-critical
- Bounded per run, then re-enqueues itself while work remains. This keeps every execution inside
  WorkManager's ten-minute ceiling **without a foreground service** — `FOREGROUND_SERVICE` was
  deliberately removed from this manifest, and restoring it would mean a `FOREGROUND_SERVICE_DATA_SYNC`
  declaration and a second Play Console review on top of the SMS permissions one
- Unlike `SmsForwarder`, it does not swallow exceptions: retry/backoff only works if a failure is
  actually reported

#### 2a. SmsBackupProviderWatchWorker (the sent-message trigger)
Nothing is broadcast when the user *sends* an SMS, so `SmsReceiver` cannot cover outgoing
messages. Instead a one-time WorkManager request carries a **content-URI trigger** on
`content://sms` (descendants included). When the provider changes, it enqueues `BackupSmsWorker`
(`APPEND_OR_REPLACE`, the same coalescing as an incoming SMS) and then re-arms itself, since the
trigger is one-shot.

Alternatives considered:
- **`ContentObserver` registered while backup is enabled.** It lives only as long as the process,
  so it misses everything sent while the app is not running. Keeping the process alive would need
  the foreground service this app deliberately does not have.
- **Periodic WorkManager scan.** It wakes the device on a schedule whether or not anything was
  sent, and still lags by up to its 15-minute minimum interval.
- **Content-URI trigger (chosen).** JobScheduler holds the observer outside the app process,
  WorkManager keeps it across reboots, and nothing runs until the provider actually changes.

Battery and cost:
- The trigger waits for **15s of quiet** (`setTriggerContentUpdateDelay`), capped at **2 min**
  (`setTriggerContentMaxDelay`). An outgoing SMS is first written as an outbox row and updated to
  "sent" a few seconds later. The quiet period lets that settle, and it coalesces a burst of
  changes into a single run.
- It fires on *every* provider change: incoming messages, read flags, deletes. `RunSmsBackupUseCase`
  therefore checks for pending messages **before** it connects. A firing with nothing new costs a
  local provider scan and a hash lookup, with no network and no IMAP login.
- **Armed** by `SmsBackupScheduler.requestBackup()` (enabling backup) and by every `BackupSmsWorker`
  run that finds backup configured (`KEEP`, so it is a no-op when already armed). The second path
  covers users who enabled backup before the trigger existed: their next backup run arms it.
  Until then nothing is lost, because every run scans both boxes in full.
- **Disarmed** by `cancelBackup()`, and by a run that ends `NotConfigured` or `AuthFailed`. A
  stale trigger that fires after backup was switched off does not re-arm, so it dies after at
  most one firing.
- No new permissions: the trigger needs `READ_SMS`, which backup already holds.

#### 3. RunSmsBackupUseCase
- No-ops when backup is off or no usable credentials are stored
- Scans for the first pending message before opening an IMAP connection, and returns `Completed`
  without connecting when there is none. The scan then resumes from that batch rather than from
  zero
- Pages the provider on `(date, _id)` rather than an offset, so the scan stays stable while new
  messages arrive mid-backfill
- The cursor advances across the whole batch including skipped messages, so a fully-backed-up batch
  cannot make the scan loop on itself
- Paces appends (~100ms) to stay clear of Gmail's IMAP throttling; throttling responses map to
  `Transient` and are retried, not treated as fatal
- On `AuthFailed`, switches backup off so the UI cannot keep showing a green "enabled" state

#### 4. MailTransport / JavaMailImapTransport
- The seam that keeps `javax.mail` out of every tested path. `SmsForwarder` is untested today
  precisely because it reaches straight for `SmsManager` and a raw `Context`; everything deciding
  *what* to send lives behind this interface and is covered by plain JUnit tests
- `withFolder` opens one folder for a whole batch. Gmail caps IMAP at ~15 concurrent connections
  per account and throttles rapid connect/disconnect cycles
- Sets explicit connect/read/write timeouts — JavaMail defaults them to infinite, which would hang
  a worker until WorkManager kills it at ten minutes with no diagnostic
- `MimeMessage.getReceivedDate()` is overridden because `Folder.appendMessages()` derives IMAP's
  INTERNALDATE from it, and Gmail sorts by INTERNALDATE rather than the `Date:` header. Without it
  a backfill of years of SMS all sorts as "today"
- Exception messages are never surfaced verbatim — JavaMail text can echo the IMAP command line,
  which for a failed LOGIN would include the app password

#### 5. SmsMailFactory
- Pure Kotlin, JDK-only. Subject templating uses literal `String.replace`, never `Regex.replace`:
  in a Kotlin regex replacement `$` introduces a group reference, so `$CONTACT_NAME` would be
  parsed as a backreference
- Every message from one address shares a synthetic thread root, which is what makes Gmail group a
  contact's history into a single conversation. Synthesized ids use the RFC 6761 reserved
  `.invalid` TLD, so nothing can ever be delivered to them
- Writes the restore metadata as `X-SMS-*` headers (`SmsMailHeaders`), see the next section

### Backup format: headers, not body

Every backup email carries what a restore needs to rebuild the provider row exactly:

| Header | Value |
|---|---|
| `X-SMS-Toolbox-Format` | `1`. Bump on any incompatible change; the parser keeps accepting older values |
| `X-SMS-Type` | `inbox` or `sent`. Sent messages also get a `To:` / `Sent:` body preamble instead of `From:` / `Received:`. Restore puts each message back in the matching box |
| `X-SMS-Address` | The raw provider address, not the normalized one, so restored messages thread exactly as before. RFC 2047-encoded only if non-ASCII |
| `X-SMS-Date` | Provider `date`, epoch **milliseconds** |
| `X-SMS-Date-Sent` | Provider `date_sent`, omitted when unknown (0 on many OEMs) |

Why headers rather than structured data in the body:
- **The body is the user's view in Gmail.** It stays a clean transcript (`From:` / `Received:` /
  blank line / text) rather than growing key/value noise.
- **The body is ambiguous to parse.** A contact name can contain parentheses, so the
  `From: Name (number)` line is not a reliable source for the number.
- **The body is lossy.** `Received:` is second precision in the device's time zone. The restore
  needs the exact millisecond `date` to recompute the original `SmsBackupKey`. Without it,
  restored messages look new and the backup would upload them all again.
- Gmail keeps custom headers verbatim on APPEND, returns them on FETCH and keeps them in Takeout's
  mbox. They are also independent of the user-editable subject template.

The body is still parsed as a **fallback for legacy backups** written before the headers existed.
Address comes from the last ` (` … `)` of the `From:` line, the timestamp from the `Date:` header
(second precision), and the box is always inbox.

### Credential storage

`SecretCipher` (domain) / `AndroidKeystoreSecretCipher` (data) — AES/GCM with a key held in the
Android Keystore. An interface, not a concrete class, so `SmsBackupSettingsRepositoryImpl` stays
JVM-testable.

`res/xml/data_extraction_rules.xml` opts the database into cloud backup and device transfer, but
the keystore key does not travel with it. On a new device `decrypt` returns null; the repository
reports "no credentials" and the UI asks for the password again rather than blaming Gmail. This is
a deliberate property, not a bug — the password is not portable off the device.

Uses `java.util.Base64`, never `android.util.Base64`: this module sets
`testOptions.unitTests.isReturnDefaultValues = true`, under which every `android.jar` call silently
returns null instead of throwing, and a test would pass on a lie.

## SMS Restore from Gmail Pipeline

```
[ Restore tab: "Restore messages" ] → dialog → (default SMS role, if not held)
 │
 └─► SmsRestoreScheduler ──► RestoreSmsWorker  ◄── re-enqueues itself with its cursor
                                │
                                ▼
                          RunSmsRestoreUseCase
                                │
                                ├─► MailTransport.withFolder(label, readOnly = true)
                                │      └─► MailFolder.readAfter(uid, 100) ──► BackedUpMail
                                ├─► SmsMailParser  (headers, or legacy body fallback)
                                ├─► SmsMessageWriter.findAround(date, ±1s)  ── skip duplicates
                                ├─► SmsMessageWriter.insert  ──► content://sms
                                └─► SmsBackupStateRepository.markBackedUp(key)
```

**Why the default SMS role.** Android silently drops every write to `content://sms` that does not
come from the SMS-role holder. SMS Toolbox, as a messaging app, normally holds the role already; if
not, the Restore tab goes through the same default-SMS gate as Messages before
`RestoreViewModel.onRestoreConfirmed()`, which re-checks the role and refuses to start without it.

**Role components** (`DefaultSmsAppComponents.kt`). Android only offers the role to an app
declaring an `SMS_DELIVER` receiver, a `WAP_PUSH_DELIVER` receiver, a `SENDTO` activity and a
`RESPOND_VIA_MESSAGE` service. All four do real work (see "Messaging"). `SMS_RECEIVED` is still
broadcast to everyone, so Redirect and live backup run off `SmsReceiver` regardless of the role.

**Idempotent, so resumable without state.** The cursor (IMAP UID + counters) travels in the work
input between bounded chunks rather than in a table. Before each insert the provider is searched
for the same body within ±1s from a matching address (`SmsBackupKey.sameAddress`, which also
matches `+351912…` against `912…` on the last nine digits). That makes a killed run, a restore run
twice, or a restore onto a phone that still holds some messages all safe. The ±1s covers legacy
backups' second-precision timestamps.

**No re-upload after restore.** Each restored message's `SmsBackupKey` is recorded in
`sms_backup_state`. For current-format backups the key equals the original, because the timestamp
is millisecond-exact.

**Reading.** `JavaMailImapTransport` opens the label `READ_ONLY` and never creates it (a missing
label is `FolderNotFound`). It pages by position over the folder's lightweight message handles
and binary-searches the UID cursor once per run, so it does not re-list the rest of a large folder
on every batch. Each page is one FETCH (`BODY.PEEK[]`), which marks nothing as read.

**What counts as a backup.** The label can hold anything the user files there, so every mail
must pass `SmsMailParser` or it is skipped, counted (`notBackups`, shown in the result) and never
written. It must have our `<sms-…@sms-toolbox.invalid>` Message-ID (not the thread root), a body
with the preamble, and valid `X-SMS-*` headers (or a parseable legacy body). The Message-ID and
headers can be copied by anyone, so it must also be **from the account itself** (every backup ever
written is) and must **not have arrived over SMTP**. Backups are IMAP APPENDs and never carry a
`Received: from <host>` hop, while anything emailed in does, even with a forged `From:`. Without
these two checks, a crafted email filed under the label by a Gmail filter would be inserted as a
genuine-looking SMS from a number of the sender's choosing. Only the `from` clause of `Received:`
counts, so an internal `Received: by …` stamp Gmail might add on APPEND cannot drop real backups.

**Losing the role mid-restore** is checked per batch and after any refused insert. It ends the
run as `NOT_DEFAULT_SMS_APP` instead of counting every remaining message as failed.

## Message Capture & Forwarding Pipeline

```
[ Incoming Message ]
 │
 └─► (Standard SMS/MMS) ──► SmsReceiver ──► ForwardSmsWorker ──► SmsForwarder
                                    (Expedited)                 │
                                                                 ▼
                                                          Send SMS to target
```

### Components

#### 1. SmsReceiver (BroadcastReceiver)
- Registered for `android.provider.Telephony.SMS_RECEIVED` with high priority (999)
- Catches standard SMS, carrier fallback texts, and 2FA codes
- Immediately enqueues a `ForwardSmsWorker` with sender + body data

#### 2. ForwardSmsWorker (WorkManager CoroutineWorker)
- Receives sender + body as input data
- Uses **Expedited WorkRequest** (`OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST`) to ensure execution even in Doze mode
- Retries on failure for reliability
- Delegates actual forwarding to `SmsForwarder`

#### 3. SmsForwarder
- Core forwarding logic (pure domain, no Android framework coupling beyond SmsManager)
- Checks SEND_SMS permission
- Checks roaming status vs user preference via `CanForwardSmsUseCase`
- Deduplicates messages within a 30-second window (prevents rapid duplicate forwards)
- Finds matching rule via `FindMatchingRuleUseCase`
- Resolves contact names via `ContactNameResolver`
- Prepends configurable forward prefix to message body
- Sends via `SmsManager` (single or multipart)
- Records history if enabled
- Posts a notification if "Show Notifications" is enabled

## Rule Matching

Every rule has a `type` (`RuleType`): `REDIRECT` or `PHONE_ACTION`. The sender test is shared (`Rule.matchesSender`): a rule's sender matches if
- the sender **equals** the rule's `senderText` (case-insensitive, e.g. an alphanumeric sender ID), OR
- the sender's digits match the rule's `phoneNumber` digits exactly.

**Redirect rules** (`FindMatchingRuleUseCase`) are evaluated in sort order and the **first** match wins. Phone-action rules are skipped, even when sorted first. If no redirect rule matches, the message is not forwarded.

**Phone-action rules** (`Rule.triggersPhoneAction`, `FindMatchingPhoneActionRulesUseCase`) need a sender, a `messageText`, or both. The sender (if set) must match, and the body must **contain** `messageText` (if set, case-insensitive). **Every** matching phone-action rule runs, so one message can ring and vibrate. Redirect and phone-action matching are independent: one SMS can be forwarded and also ring the phone.

## Automation phone actions

```
SmsReceiver ──(goAsync)──► TriggerPhoneActionsUseCase ──► PhoneActionScheduler ──► PhoneActionWorker ──► PhoneActionExecutor
               match rules in memory          (WorkManagerPhoneActionScheduler)   (expedited, one per action)   (AndroidPhoneActionExecutor)
```

- `PhoneAction` (`domain/model/`) is a sealed interface: `Ring(soundUri, soundName)`, `Vibrate(VibrationPattern)`, `SetSoundMode(SoundMode)`. It is persisted as three strings, `kind` / `value` / `label`, in the `rules` table, the configuration export and the worker's input; `PhoneAction.decode` reads them back. Those names are a stored contract: add new kinds or enum constants, never rename one (`PhoneActionTest` pins them).
- `SmsReceiver` matches in the receiver (on `goAsync()`), so **only the matched actions** reach WorkManager, never the message body.
- `PhoneActionWorker` runs one action and reports failure (`Result.failure()`), never retries. Each request carries its queue time, and an action that starts more than `PhoneAction.MAX_START_DELAY_MILLIS` (2 min) late is dropped: once the expedited quota is spent WorkManager may defer the work, and a full-volume ring long after the SMS is worse than none.
- **Ring** plays with `MediaPlayer` on the **alarm** stream (`USAGE_ALARM`), which ignores the ringer mode, so it rings on silent. The alarm volume is set to maximum and restored afterwards. It loops until the notification's Stop (or a tap or swipe, via `StopRingingReceiver`; the notification is not ongoing, so it can be swiped on Android 13) or `PhoneAction.RING_DURATION_MILLIS` (60 s). If the notification cannot be shown (permission denied, notifications or the channel off) there is no Stop button, so the ring lasts only `RING_WITHOUT_STOP_DURATION_MILLIS` (15 s); the editor asks for the notification permission when a Ring rule is saved or tried. `ActiveRing` (a `RingCoordinator`) keeps a single ring per process: a new ring ends the previous one and inherits the saved volume. Starting and finishing, with their volume and notification changes, run under one lock (`RingCoordinatorTest`). A missing or unreadable sound falls back to the default alarm, then the ringtone. Songs come from `ACTION_OPEN_DOCUMENT` with a persisted read grant; system sounds come from the ringtone picker. No audio focus is requested (Android 15 refuses it to background apps). No foreground service is used: the expedited worker keeps the process alive for the minute.
- **Vibrate** uses `VibrationAttributes.USAGE_ALARM`, because Android drops other vibrations from background apps.
- **Sound mode** sets `AudioManager.ringerMode`. Any switch to or from silent toggles Do Not Disturb and needs notification-policy access (`SoundModeAccessChecker`). The editor shows a warning with a shortcut to the settings page, and a rule that fires without access posts a notification instead of failing silently.
- The editor's "Try it now" schedules the unsaved action through the same scheduler.
- Phone actions keep no history and send nothing off the device.


## Scheduled SMS Pipeline

```
[ Add / Edit Scheduled SMS ]
 │
 ▼
AddScheduledSmsUseCase / UpdateScheduledSmsUseCase
 │                                   │
 ▼ (persists)                       ▼ (schedules)
ScheduledSmsRepository        ScheduledSmsScheduler (interface)
                                     │
                                     ▼
                    WorkManagerScheduledSmsScheduler
                    (OneTimeWorkRequest, delay = date − now,
                     unique work name "scheduled_sms_<id>")
                                     │
                                     ▼ (fires at the scheduled time)
                          SendScheduledSmsWorker (CoroutineWorker)
                                     │
                                     ▼
                             ScheduledSmsSender
                             ├─► Send SMS via SmsManager
                             ├─► Post "Scheduled SMS was sent to <Contact>" notification
                             ├─► Record history (if "keep schedule history" is enabled)
                             └─► RecurrenceCalculator.nextOccurrence(...) ──► scheduler.scheduleNext(...)
                                 (re-arms the next occurrence, or does nothing if RepeatType.NONE
                                  or the pattern has reached its "stop at" date)
```

### Components

#### 1. ScheduledSmsScheduler (domain interface) / WorkManagerScheduledSmsScheduler (impl)
- Abstracts the platform scheduling mechanism out of the domain layer, mirroring the `ContactNameResolver` domain/data split.
- `scheduleInitial` / `scheduleNext` both enqueue a unique `OneTimeWorkRequest` keyed by `"scheduled_sms_<id>"` with `ExistingWorkPolicy.REPLACE`, so editing or re-arming a scheduled SMS always supersedes any previously pending work for that id.
- `cancel` removes the unique work when a scheduled SMS is deleted, or when it's edited (cancel-then-reschedule-fresh, see `UpdateScheduledSmsUseCase`).

#### 2. SendScheduledSmsWorker (WorkManager CoroutineWorker)
- Receives the scheduled SMS's row id as input data and delegates to `ScheduledSmsSender`.
- Returns `Result.retry()` on failure so WorkManager's backoff policy retries transient errors.

#### 3. ScheduledSmsSender
- Loads the `ScheduledSms` by id, sends via `SmsManager` (single or multipart, same as `SmsForwarder`).
- Posts a notification ("Scheduled SMS was sent to `<Contact>`", using the resolved contact name when available) gated by the same global "Show Notifications" setting used by Redirect.
- Records a history entry gated by the schedule-specific "keep schedule history" setting, then trims history to the latest 50.
- Re-arms the next occurrence via `RecurrenceCalculator` + `scheduler.scheduleNext(...)` when the pattern repeats.

### Recurrence Calculation

`RecurrenceCalculator` (pure Kotlin, `domain/scheduling/`, zero Android dependencies) computes every occurrence directly from the **original anchor date** the user picked — never by chaining off the previously computed occurrence. This matters for two reasons:

1. **No clamping drift.** A monthly reminder anchored on the 31st clamps to the 28th/30th in short months (`java.time`'s `plusMonths` behavior), but the *following* occurrence is still computed as `anchor.plusMonths(2)`, correctly landing back on the 31st — not drifting to the 28th forever, which naive chaining off the previous result would cause.
2. **No catch-up bursts.** If the device was off or the app was killed for a long time, `nextOccurrence(afterEpochMs = now)` returns only the single next occurrence after "now" — missed occurrences are skipped silently instead of firing a burst of catch-up sends. A `MAX_ITERATIONS` safety cap (10,000) guarantees the search always terminates.

Supported `RepeatType`s: `NONE` (single send), `DAILY`, `WORK_WEEK_DAYS` (skips Saturday/Sunday), `BIWEEKLY`, `MONTHLY`, `YEARLY`. An optional `stopAtEpochMs` bounds how far the recurrence extends; once the next computed occurrence would land after it, recurrence stops.

## Data Layer

```
┌────────────┬─────────────┬────────────────┬─────────────────┬─────────────────────┐
│ RuleEntity │ AppSettings │ ForwardHistory │  ScheduledSms   │ ScheduledSmsHistory │
│            │   Entity    │     Entity     │     Entity      │       Entity        │
├────────────┼─────────────┼────────────────┼─────────────────┼─────────────────────┤
│  RuleDao   │ AppSettings │ ForwardHistory │ ScheduledSmsDao │ ScheduledSmsHistory │
│            │     Dao     │      Dao       │                 │         Dao         │
└────────────┴─────────────┴────────────────┴─────────────────┴─────────────────────┘
                                     │
                                     ▼  (each entity/DAO pair has its own repository)
   RuleRepository · SettingsRepository · ForwardHistoryRepository ·
   ScheduledSmsRepository · ScheduledSmsHistoryRepository
                                     │
                                     ▼
                     Use Cases (domain layer)
                                     │
                                     ▼
                    ViewModels (presentation layer)
```

Redirect and Schedule each own their own tables end-to-end — `ScheduledSmsEntity`/`ScheduledSmsHistoryEntity` are separate Room tables from `RuleEntity`/`ForwardHistoryEntity`, per their own DAOs and repositories. Only `AppSettingsEntity` (global settings) is shared between the two features.

### Entities

| Entity | Table name | Purpose |
|--------|-----------|---------|
| `RuleEntity` | `rules` | Automation rules: redirect (sender match → target number) or phone action (sender and/or message text → `phoneAction`/`phoneActionValue`/`phoneActionLabel`). v12 added `ruleType`, `messageText` and the three action columns (`MIGRATION_11_12`, checked against `12.json` by `AppDatabaseMigrationTest`) |
| `AppSettingsEntity` | `app_settings` | App-wide settings (roaming, history, prefixes, notifications) |
| `ForwardHistoryEntity` | `forward_history` | Log of forwarded messages (Redirect) |
| `ScheduledSmsEntity` | `scheduled_sms` | Scheduled SMS definitions (name, category, contact, message, date, repeat pattern, stop-at, sort order) |
| `ScheduledSmsHistoryEntity` | `scheduled_sms_history` | Log of sent scheduled SMS (Schedule) |
| `BackupSettingsEntity` | `backup_settings` | Gmail credentials and backup configuration (single row, `id = 1`) |
| `SmsBackupStateEntity` | `sms_backup_state` | One row per message already dealt with, keyed by a content hash. Makes a killed backup resumable and idempotent |

Database version is **11**. `MIGRATION_10_11` is data-only (no schema change; `11.json` equals
`10.json` but for the version): it moves `landingScreen` from the old default `BACKUP` to
`MESSAGES`. The column's SQL default deliberately stays `'BACKUP'` — changing it would change the
schema and force a table rebuild — and new rows get `MESSAGES` from the Kotlin default. `MIGRATION_9_10` adds the two tables above and rebuilds `app_settings`
solely to move the `forwardPrefix` default to `SMS Toolbox:` — SQLite cannot alter a column default
in place, and Room validates the migration DDL against the schema generated from the entity
annotations. `exportSchema` is on (`app/schemas/`), which is the only way a schema change here can
be reviewed: there is no `androidTest` source set to run `MigrationTestHelper` against.

The two new tables deliberately declare **no SQL defaults**. Room compares the migration DDL to its
own generated schema, and a mismatch crashes every upgrading user on open; omitting defaults removes
any question of how Room quotes a value containing `$`, parentheses or an empty string. Blank columns
mean "unset" and `SmsBackupSettingsRepositoryImpl` applies the real defaults, mirroring the existing
`?: "+351"` fallback style.

### Backup settings (BackupSettingsEntity)

| Field | Default (applied in the repository) | Description |
|-------|---------|-------------|
| `gmailAddress` | `""` | The user's Gmail address |
| `gmailAppPasswordCipher` | `""` | App password, encrypted by `SecretCipher`. Never stored or logged in plaintext, and deliberately absent from `ExportBackupUseCase` |
| `subjectTemplate` | `SMS with $CONTACT_NAME ($PHONE_NUMBER)` | Email subject; both placeholders are substituted per message |
| `gmailLabel` | `SMS` | IMAP folder, which Gmail surfaces as a label. Created on first use |
| `backupPreviousEnabled` | `false` | Whether every received and sent SMS is eligible, or only messages from when backup was switched on |
| `backupEnabled` | `false` | Master switch |
| `backupStartedAtMillis` | `0` | Watermark. Zero means "no watermark, back up everything" |

### Settings (AppSettingsEntity)

| Field | Default | Description |
|-------|---------|-------------|
| `sendWhileRoaming` | `false` | Allow forwarding when network is roaming |
| `keepHistory` | `true` | Record forwarded messages (Redirect history) |
| `keepScheduleHistory` | `true` | Record sent scheduled SMS (Schedule history) |
| `defaultPrefix` | `+351` | Country code prepended to local numbers (used by both features) |
| `forwardPrefix` | `SMS Toolbox:` | Text prepended to forwarded message body |
| `showNotifications` | `false` | Show a notification after each successful forward or scheduled send |

## In-app update (github flavor)

- `domain/update/`: `AppReleaseSource`, `AppUpdateRepository`, `AppUpdateInstaller` interfaces and `AppVersions` (numeric dotted-version comparison).
- `CheckForAppUpdateUseCase`: fetches the latest release at most once per 24 h unless forced ("Check for updates"); a failed fetch is not recorded, so the next launch retries. `GetAvailableAppUpdateUseCase` emits the stored latest release only while it is newer than `BuildConfig.VERSION_NAME`, so the banner disappears once the update is installed without another request.
- `data/update/SharedPreferencesAppUpdateRepository`: last-check time and latest-release metadata, in SharedPreferences (a disposable cache; no Room migration).
- `src/github/.../distribution/`: `GitHubReleaseSource` (unauthenticated `GET api.github.com/repos/neteinstein/SMS-Redirect/releases/latest`), `GitHubReleaseParser` (accepts only APK URLs under this repository's `releases/download/`), `ApkUpdateInstaller` (asks for "install unknown apps" if needed, downloads to `cacheDir/updates`, checks the archive's package name, opens the system installer via `FileProvider`).
- `presentation/update/AppUpdateViewModel`: activity-scoped in `MainActivity`; creating it runs the launch check. `UpdateAvailableBanner` sits in each home tab's `topBanner` slot and opens Settings, where `UpdatesSection` is the first section.

Debug builds disable it (`.test` applicationId suffix: a release APK could never install over them).

## Keep-alive reminder

Android resets the permissions of apps left unused for months ("Manage app if unused" / "Remove permissions and free up space"), which would silently stop Redirect, Schedule and Backup.

- `KeepAliveReminderWorker`: a 30-day periodic WorkManager job that posts a low-importance notification (`KeepAliveNotifications`, silent no-op without `POST_NOTIFICATIONS`). `MainActivity.onStart` restarts the countdown (`CANCEL_AND_REENQUEUE`), so the reminder only fires after a full month without opening the app.
- `UnusedAppRestrictionChecker` (domain) / `AndroidUnusedAppRestrictionChecker` (data) wraps `PackageManager.isAutoRevokeWhitelisted`. `ShouldSendKeepAliveReminderUseCase` skips the notification when the user already exempted the app; if the platform will not say, it sends.
- First open from the notification: `ShouldShowUnusedAppInfoUseCase` (one-time flag in SharedPreferences, `KeepAliveReminderRepository`) decides whether `MainActivity` shows a dialog explaining the setting, with a button to `ACTION_AUTO_REVOKE_PERMISSIONS`. Nothing leaves the device.

## Permissions

Permissions are requested contextually rather than upfront at first launch — there is no onboarding screen that gates the app behind granting everything. Each permission is requested the first time its feature is used, after a rationale dialog (Proceed/Cancel) explains why it's needed:

| Permission | Requested when | Purpose |
|---|-----------|---------|
| Default SMS app role (`RoleManager.ROLE_SMS`) | First launch, and any SMS feature used without it — always **before** any SMS permission | Be the messaging app: receive (`SMS_DELIVER`/`WAP_PUSH_DELIVER`), write sent/received/restored messages. Android grants the SMS permissions below with it |
| `READ_SMS` / `RECEIVE_SMS` / `SEND_SMS` / `RECEIVE_MMS` / `RECEIVE_WAP_PUSH` | Granted with the role. `RECEIVE_SMS` / `READ_SMS` / `SEND_SMS` (`toolSmsPermissions`) are also requested on their own for Automate/Schedule/Backup: directly in the github build, only after the role was declined in the play build | Show, receive and send SMS/MMS; back up; detect and forward; send scheduled messages |
| `READ_CELL_BROADCASTS` | Granted with the role only; never requested on its own | Not used directly. Android ties the default SMS app's exclusive `READ_CELL_BROADCASTS` app op to this permission: undeclared, the op stays denied and `Telephony.Sms.getDefaultSmsPackage()` returns null to the role holder itself. `AndroidDefaultSmsAppChecker` asks `RoleManager.isRoleHeld` for the same reason |
| `READ_CONTACTS` | Right after the SMS role is granted, or with the SMS permissions when a tool asks for them without the role; or tapping "pick contact" in a rule/schedule. The New message picker needs none (ACTION_PICK on phone numbers grants the one row) | Names in conversations and notifications; pick contacts for rules and scheduled SMS |
| `POST_NOTIFICATIONS` | Right after the SMS role is granted, or with the SMS permissions when a tool asks for them without the role; turning on "Show notifications"; enabling backup or starting a restore | Incoming messages; backup/restore progress; forward and scheduled-send confirmations |
| `INTERNET` / `ACCESS_NETWORK_STATE` | Not runtime-requested (install-time) | Reach `imap.gmail.com`. Both previously arrived only via manifest merge from the Firebase/WorkManager AARs; they are now declared explicitly |
| Battery optimization exemption | Right after creating a new rule or scheduled SMS | Prevent Doze from throttling background delivery; declining shows a persistent warning banner on Home until enabled |
| `VIBRATE` | Not runtime-requested (install-time) | Phone action: vibrate |
| `MODIFY_AUDIO_SETTINGS` | Not runtime-requested (install-time) | Phone actions: raise and restore the alarm volume, change the ringer mode |
| `ACCESS_NOTIFICATION_POLICY` ("Do Not Disturb" access) | Not a runtime permission: the user grants it in system settings, from the warning in the rule editor when a sound-mode action is chosen | Phone action: switch the ringer to or from silent |
| `REQUEST_INSTALL_PACKAGES` (github flavor only) | Not runtime-requested; the user allows "install unknown apps" for SMS Toolbox the first time they install an update | Hand the downloaded update APK to the system installer |

WorkManager's own scheduling permissions were already present for the Redirect feature — Schedule required no new manifest permissions.

## Presentation Layer

Uses Jetpack Compose with a simple NavHost. The `Home` route hosts a bottom navigation bar (`MainBottomBar`) with five tabs (`MainTab.MESSAGES` / `REDIRECT` / `SCHEDULE` / `BACKUP` / `RESTORE`, in that order); switching tabs swaps the body between `ConversationListScreen`, `HomeScreen`, `ScheduleListScreen`, `BackupScreen` and `RestoreScreen` while the `bottomBar` and the `Home` back-stack entry stay put, so the selected tab survives round-trips to any child screen. **`MESSAGES` is the initial tab** (`LandingScreen.MESSAGES`, user-changeable in Settings).

`MainBottomBar` iterates the tabs the build ships (`AppFeatures.availableTabs()`). It shows a label only for the selected tab — five icons plus the FAB do not fit labels on a narrow phone. The FAB is "new message" on Messages, "add" on Redirect/Schedule, and hidden on Backup and Restore.

- **SplashScreen** → **ConversationListScreen** (`Home` route, Messages tab — the landing screen; shown disabled, with a warning bar, until the app holds the SMS role)
  - → **NewMessageScreen** (`new_message`: recipients, contact picker) → **ConversationScreen**
  - → **ConversationScreen** (`conversation?threadId=…` or `?addresses=…&draft=…&attachment=…`: bubbles, attachments, composer, retry/delete). Also reached from a notification (`smsredirect://app/conversation?threadId=…`) and from `ComposeSmsActivity`
- **SplashScreen** → **HomeScreen** (Automate tab: rule list with drag-to-reorder; each card shows a redirect's target or a phone action)
  - **HomeScreen** → **RuleScreen** (create/edit rule)
  - **HomeScreen** → **SettingsScreen** (toggles, prefix config — shared/global, see below)
  - **SettingsScreen** → **HistoryScreen** (last 50 forwarded messages, Redirect-specific)
- **SplashScreen** → **ScheduleListScreen** (Schedule tab: scheduled SMS list with drag-to-reorder)
  - **ScheduleListScreen** → **ScheduleFormScreen** (create/edit scheduled SMS)
  - **SettingsScreen** → **ScheduleHistoryScreen** (last 50 sent scheduled SMS)
- **SplashScreen** → **BackupScreen** (Backup tab: Gmail config, enable toggle, progress; or an empty state prompting for credentials) → **SettingsScreen**
- **SplashScreen** → **RestoreScreen** (Restore tab: restore card, progress and result; `RestoreViewModel`)

Redirect and Schedule use the same top-right icon pattern (history icon → that tab's own history screen; settings icon → the one shared `SettingsScreen`). Deep links are supported via `smsredirect://app` URI scheme for `history`, `schedule_history` and `conversation?threadId=`, resolved by a single `resolveDeepLinkRoute()` helper in `MainActivity`.

### Duplicate Rule Prevention

When saving a rule, the ViewModel checks all existing rules (post-normalization) for an identical `senderText` + `targetNumber` pair. If found, the save is blocked with an error message. Scheduled SMS have no equivalent duplicate check — multiple scheduled messages to the same contact are expected (e.g. several different reminders).

## Dependency Wiring

Manual DI via `AppContainer` (created in `SmsRedirectApplication.onCreate()`). No framework — just constructor injection through a single data class holding all use cases, `SmsForwarder`, `ScheduledSmsSender`, the messaging services (`MessageStore`, and the concrete `AndroidMessageSender` / `AndroidMmsDownloader` that `MessageStatusReceiver` reports back to), and cross-cutting services like `ContactNameResolver`.

## Contact Resolution

The `ContactNameResolver` interface (in `domain/contacts/`) resolves phone numbers to display names from the device contacts. The Android implementation (`AndroidContactNameResolver` in `data/contacts/`) queries the system `ContentResolver`. This is used by both `SmsForwarder` and `ScheduledSmsSender` to enrich notifications with readable contact names, and by the Rule and Schedule list/form ViewModels to display resolved names next to raw phone numbers. The same contact-picker component used for a Rule's target number is reused for a scheduled SMS's contact field.

## Crash Handling

`DeviceCompatibilityCrashHandler` (installed in `SmsRedirectApplication.onCreate()`) wraps the process's default `Thread.UncaughtExceptionHandler`. Some OEM builds report a higher `Build.VERSION.SDK_INT` than the APIs their framework.jar actually implements, so platform/AndroidX code (e.g. Jetpack Compose's font resolver) throws `NoSuchFieldError`/`NoSuchMethodError` on those devices — a device/OS bug no amount of app code can fix. The handler recognizes only those two error types, reports them to Crashlytics as non-fatal (so they don't skew crash-free metrics or get conflated with real regressions) and terminates the process cleanly instead of showing the system "App has stopped" dialog; every other throwable is forwarded unchanged to the previous handler so normal fatal-crash reporting is unaffected. Separately, `androidx.profileinstaller`'s App Startup initializer is disabled in `AndroidManifest.xml` — it calls an API-33-only method unconditionally on any device reporting API 33+, which crashes on the same class of non-compliant OEM builds; baseline/cloud profile installation is a background perf optimization the app doesn't depend on, so it's simplest to stop it from running at all.
