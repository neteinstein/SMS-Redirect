# SMS Toolbox — Architecture

## Overview

SMS Toolbox is an Android app with three independent features, presented as bottom-navigation tabs on the home screen:

- **Backup** — copies SMS messages to the user's own Gmail account over IMAP. This is the app's primary feature and the landing tab.
- **Redirect** — intercepts incoming SMS messages matching user-defined rules and forwards them to a configured target phone number via SMS.
- **Schedule** — lets the user compose a message once and have it sent automatically at a future date/time, optionally repeating on a recurring pattern.

All three share the same app shell (permissions flow, Settings screen, deep-link handling) but are otherwise independent end-to-end: separate domain models, separate Room tables, separate use cases, separate WorkManager jobs.

> **A note on the word "backup".** It means two different things in this codebase.
> `ExportBackupUseCase` / `ImportBackupUseCase` predate the Gmail feature and refer to the
> local JSON export of rules, schedules and settings. Everything belonging to the SMS→Gmail
> feature is prefixed `SmsBackup*` or `Gmail*`. Keep that split when adding code.

## Why Backup leads

Google Play's SMS & Call Log Permissions policy only grants `READ_SMS` / `RECEIVE_SMS` for a
declared core use case, and "backup and restore" is on the permitted list while "SMS forwarder"
is not. Backup is therefore the declared purpose, the first tab, and the screen the app opens
on — the declaration has to match what the app visibly is.

## SMS Backup to Gmail Pipeline

```
[ Incoming SMS ]                        [ Backup tab: "Enable" / "Backup all previous" ]
 │                                       │
 └─► SmsReceiver                         └─► SmsBackupScheduler
      │  (also enqueues ForwardSmsWorker)      │
      ▼                                        ▼
   BackupSmsWorker  ◄────────────── re-enqueues itself while work remains
      │
      ▼
   RunSmsBackupUseCase
      │
      ├─► SmsMessageSource ──► content://sms/inbox  (keyset paging on date,_id)
      ├─► SmsBackupStateRepository ──► filter out anything already dealt with
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

`SmsBackupKey` is `SHA-256(normalizedAddress | body | dateEpochMs)` — not the provider `_id`, which
SQLite reuses after a delete and which does not survive a device restore. Only the hash is stored,
never the message text.

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

#### 3. RunSmsBackupUseCase
- No-ops when backup is off or no usable credentials are stored
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
| `X-SMS-Type` | `inbox` or `sent` (only `inbox` is backed up today; carried now so sent support needs no format revision) |
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
[ Backup tab: "Restore messages" ] → 3-step dialog → RoleManager.ROLE_SMS request
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
come from the SMS-role holder, so there is no other way. The role is held only for the restore.
`BackupUiState.shouldSwitchBack` drives a card button, a screen banner and a notification that all
send the user to the system default-apps screen. Android cannot hand the role to a *specific* app,
so that screen is as far as the app can take them. `refreshDefaultSmsApp()` runs on every resume,
because the hand-back happens outside the app with no callback.

**Role components** (`DefaultSmsAppComponents.kt`). Android only offers the role to an app
declaring an `SMS_DELIVER` receiver, a `WAP_PUSH_DELIVER` receiver, a `SENDTO` activity and a
`RESPOND_VIA_MESSAGE` service. While the role is held, SMS_DELIVER goes *only* to the role holder,
so `SmsDeliverReceiver` writes incoming SMS to the inbox; it is not a stub. Quick replies are
sent. MMS is out of scope: an arriving MMS cannot be downloaded and the user is notified.
`SMS_RECEIVED` is still broadcast to everyone, so Redirect and live backup keep working.

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

Rules are evaluated in sort order. A rule matches if:
- The sender text **contains** the rule's `senderText` (case-insensitive), OR
- The sender's digits match the rule's `phoneNumber` digits exactly

First matching rule wins. If no rule matches, the message is not forwarded.

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
| `RuleEntity` | `rules` | Forwarding rules (sender match → target number) |
| `AppSettingsEntity` | `app_settings` | App-wide settings (roaming, history, prefixes, notifications) |
| `ForwardHistoryEntity` | `forward_history` | Log of forwarded messages (Redirect) |
| `ScheduledSmsEntity` | `scheduled_sms` | Scheduled SMS definitions (name, category, contact, message, date, repeat pattern, stop-at, sort order) |
| `ScheduledSmsHistoryEntity` | `scheduled_sms_history` | Log of sent scheduled SMS (Schedule) |
| `BackupSettingsEntity` | `backup_settings` | Gmail credentials and backup configuration (single row, `id = 1`) |
| `SmsBackupStateEntity` | `sms_backup_state` | One row per message already dealt with, keyed by a content hash. Makes a killed backup resumable and idempotent |

Database version is **10**. `MIGRATION_9_10` adds the two tables above and rebuilds `app_settings`
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
| `backupPreviousEnabled` | `false` | Whether the whole inbox is eligible, or only messages from when backup was switched on |
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

## Permissions

Permissions are requested contextually rather than upfront at first launch — there is no onboarding screen that gates the app behind granting everything. Each permission is requested the first time its feature is used, after a rationale dialog (Proceed/Cancel) explains why it's needed:

| Permission | Requested when | Purpose |
|---|-----------|---------|
| `READ_SMS` / `RECEIVE_SMS` / `SEND_SMS` | Adding the first rule or scheduled SMS | Read the inbox for backup; detect and forward/send SMS (MMS/RCS are out of scope). `READ_SMS` was declared but unused before the Backup feature — it is now genuinely exercised |
| `READ_CONTACTS` | Tapping "pick contact" for the first time | Pick contacts for rules and scheduled SMS |
| `POST_NOTIFICATIONS` | Turning on "Show notifications" in Settings, **or** enabling Gmail backup | Backup progress; notify after a successful forward or scheduled send. The Backup tab requests it too, because otherwise a user who never opens the notification toggle would silently never see backup progress |
| `INTERNET` / `ACCESS_NETWORK_STATE` | Not runtime-requested (install-time) | Reach `imap.gmail.com`. Both previously arrived only via manifest merge from the Firebase/WorkManager AARs; they are now declared explicitly |
| Battery optimization exemption | Right after creating a new rule or scheduled SMS | Prevent Doze from throttling background delivery; declining shows a persistent warning banner on Home until enabled |
| Default SMS app role (`RoleManager.ROLE_SMS`) | Confirming "Restore messages", after a dialog explaining the three steps | Write restored messages to `content://sms`. Held only for the restore; the Backup tab prompts the user to switch back as soon as no restore needs it |

WorkManager's own scheduling permissions were already present for the Redirect feature — Schedule required no new manifest permissions.

## Presentation Layer

Uses Jetpack Compose with a simple NavHost. The `Home` route hosts a bottom navigation bar (`MainBottomBar`) with three tabs (`MainTab.BACKUP` / `REDIRECT` / `SCHEDULE`, in that order); switching tabs swaps the body between `BackupScreen`, `HomeScreen` and `ScheduleListScreen` while the `bottomBar` and the `Home` back-stack entry itself stay put, so the selected tab survives round-trips to any child screen. **`BACKUP` is the initial tab.**

`MainBottomBar` iterates `MainTab.entries` rather than hardcoding items, and shows a label only for the selected tab — three icon+label pairs plus the FAB do not fit a narrow phone, and the Portuguese labels are longer. The FAB has no "create" action on the Backup tab, so it is hidden there (`showFab = false`) and the pill takes the full width.

- **SplashScreen** → **BackupScreen** (`Home` route, Backup tab — the landing screen: Gmail config, enable toggle, progress; or an empty state prompting for credentials when none are stored)
  - **BackupScreen** → **SettingsScreen** (where the Gmail address and app password are entered)
- **SplashScreen** → **HomeScreen** (`Home` route, Redirect tab: rule list with drag-to-reorder)
  - **HomeScreen** → **RuleScreen** (create/edit rule)
  - **HomeScreen** → **SettingsScreen** (toggles, prefix config — shared/global, see below)
  - **SettingsScreen** → **HistoryScreen** (last 50 forwarded messages, Redirect-specific)
- **SplashScreen** → **ScheduleListScreen** (`Home` route, Schedule tab: scheduled SMS list with drag-to-reorder, same visual pattern as the rule list — categories, names, empty state)
  - **ScheduleListScreen** → **ScheduleFormScreen** (create/edit scheduled SMS: name, category, contact, message, date, hour/minute, repeat pattern, stop-at)
  - **ScheduleListScreen** → **SettingsScreen** (same shared screen as Redirect's top-right settings icon)
  - **SettingsScreen** → **ScheduleHistoryScreen** (last 50 sent scheduled SMS, with its own persisted "keep schedule history" toggle, defaulting to enabled)

Both tabs use the same top-right icon pattern (history icon → that tab's own history screen; settings icon → the one shared `SettingsScreen`). Deep links are supported via `smsredirect://app` URI scheme for both `history` and `schedule_history`, resolved by a single `resolveDeepLinkRoute()` helper in `MainActivity`.

### Duplicate Rule Prevention

When saving a rule, the ViewModel checks all existing rules (post-normalization) for an identical `senderText` + `targetNumber` pair. If found, the save is blocked with an error message. Scheduled SMS have no equivalent duplicate check — multiple scheduled messages to the same contact are expected (e.g. several different reminders).

## Dependency Wiring

Manual DI via `AppContainer` (created in `SmsRedirectApplication.onCreate()`). No framework — just constructor injection through a single data class holding all use cases (Redirect and Schedule), `SmsForwarder`, `ScheduledSmsSender`, and cross-cutting services like `ContactNameResolver`.

## Contact Resolution

The `ContactNameResolver` interface (in `domain/contacts/`) resolves phone numbers to display names from the device contacts. The Android implementation (`AndroidContactNameResolver` in `data/contacts/`) queries the system `ContentResolver`. This is used by both `SmsForwarder` and `ScheduledSmsSender` to enrich notifications with readable contact names, and by the Rule and Schedule list/form ViewModels to display resolved names next to raw phone numbers. The same contact-picker component used for a Rule's target number is reused for a scheduled SMS's contact field.

## Crash Handling

`DeviceCompatibilityCrashHandler` (installed in `SmsRedirectApplication.onCreate()`) wraps the process's default `Thread.UncaughtExceptionHandler`. Some OEM builds report a higher `Build.VERSION.SDK_INT` than the APIs their framework.jar actually implements, so platform/AndroidX code (e.g. Jetpack Compose's font resolver) throws `NoSuchFieldError`/`NoSuchMethodError` on those devices — a device/OS bug no amount of app code can fix. The handler recognizes only those two error types, reports them to Crashlytics as non-fatal (so they don't skew crash-free metrics or get conflated with real regressions) and terminates the process cleanly instead of showing the system "App has stopped" dialog; every other throwable is forwarded unchanged to the previous handler so normal fatal-crash reporting is unaffected. Separately, `androidx.profileinstaller`'s App Startup initializer is disabled in `AndroidManifest.xml` — it calls an API-33-only method unconditionally on any device reporting API 33+, which crashes on the same class of non-compliant OEM builds; baseline/cloud profile installation is a background perf optimization the app doesn't depend on, so it's simplest to stop it from running at all.
