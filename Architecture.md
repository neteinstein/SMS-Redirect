# SMS Redirect & Schedule — Architecture

## Overview

SMS Redirect & Schedule is an Android app with two independent features, presented as bottom-navigation tabs on the home screen:

- **Redirect** — intercepts incoming SMS and RCS messages matching user-defined rules and forwards them to a configured target phone number via SMS.
- **Schedule** — lets the user compose a message once and have it sent automatically at a future date/time, optionally repeating on a recurring pattern.

Both features share the same app shell (permissions flow, Settings screen, deep-link handling) but are otherwise independent end-to-end: separate domain models, separate Room tables, separate use cases, separate WorkManager jobs, and separate history logs.

## Message Capture & Forwarding Pipeline

```
[ Incoming Message ]
 │
 ├─► (Standard SMS/MMS) ──► SmsReceiver ──────────┐
 │                                                 ▼
 └─► (RCS / Internet) ──► RcsNotificationListener ──► ForwardSmsWorker ──► SmsForwarder
                                                        (Expedited)          │
                                                                             ▼
                                                                      Send SMS to target
```

### Components

#### 1. SmsReceiver (BroadcastReceiver)
- Registered for `android.provider.Telephony.SMS_RECEIVED` with high priority (999)
- Catches standard SMS, carrier fallback texts, and 2FA codes
- Immediately enqueues a `ForwardSmsWorker` with sender + body data

#### 2. RcsNotificationListener (NotificationListenerService)
- Monitors notifications from Google Messages (`com.google.android.apps.messaging`)
- Extracts sender from `EXTRA_TITLE` and message body from `EXTRA_TEXT`
- Acts as a safety net for RCS chats and internet-based messaging
- Requires user to grant **Notification Access** in system settings

#### 3. ForwardSmsWorker (WorkManager CoroutineWorker)
- Receives sender + body as input data
- Uses **Expedited WorkRequest** (`OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST`) to ensure execution even in Doze mode
- Retries on failure for reliability
- Delegates actual forwarding to `SmsForwarder`

#### 4. SmsForwarder
- Core forwarding logic (pure domain, no Android framework coupling beyond SmsManager)
- Checks SEND_SMS permission
- Checks roaming status vs user preference via `CanForwardSmsUseCase`
- Deduplicates messages within a 30-second window (prevents double-forward when both SmsReceiver and RcsNotificationListener fire)
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

### Settings (AppSettingsEntity)

| Field | Default | Description |
|-------|---------|-------------|
| `sendWhileRoaming` | `false` | Allow forwarding when network is roaming |
| `keepHistory` | `true` | Record forwarded messages (Redirect history) |
| `keepScheduleHistory` | `true` | Record sent scheduled SMS (Schedule history) |
| `defaultPrefix` | `+351` | Country code prepended to local numbers (used by both features) |
| `forwardPrefix` | `SMS Redirect:` | Text prepended to forwarded message body |
| `showNotifications` | `true` | Show a notification after each successful forward or scheduled send |

## Permissions

The app requires the following permissions, requested in order:

| # | Permission | Purpose |
|---|-----------|---------|
| 1 | `READ_SMS` | Access SMS content |
| 2 | `RECEIVE_SMS` | Receive SMS broadcast |
| 3 | `READ_CONTACTS` | Pick contacts for rules and scheduled SMS |
| 4 | `SEND_SMS` | Forward messages / send scheduled SMS |
| 5 | `READ_PHONE_STATE` | Check roaming status |
| 6 | Battery Optimization exemption | Prevent Doze from throttling |
| 7 | **Notification Access** | Capture RCS messages (last, via system settings) |

`POST_NOTIFICATIONS` (for both forward-sent and scheduled-sent notifications) and WorkManager's own scheduling permissions were already present for the Redirect feature — Schedule required no new manifest permissions.

## Presentation Layer

Uses Jetpack Compose with a simple NavHost. The `Home` route hosts a bottom navigation bar (`MainBottomBar`) with two tabs (`MainTab.REDIRECT` / `MainTab.SCHEDULE`); switching tabs swaps the body between `HomeScreen` and `ScheduleListScreen` while the `bottomBar` and the `Home` back-stack entry itself stay put, so the selected tab survives round-trips to any child screen.

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
