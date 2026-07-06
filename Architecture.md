# SMS Redirect — Architecture

## Overview

SMS Redirect is an Android app that intercepts incoming SMS and RCS messages matching user-defined rules and forwards them to a configured target phone number via SMS.

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

## Data Layer

```
┌─────────────────────────────────────────────┐
│              AppDatabase (Room)              │
├─────────────────┬────────────┬──────────────┤
│   RuleEntity    │ AppSettings│ForwardHistory │
│                 │  Entity    │   Entity      │
├─────────────────┼────────────┼──────────────┤
│   RuleDao       │AppSettings │ForwardHistory │
│                 │   Dao      │    Dao        │
└─────────────────┴────────────┴──────────────┘
         │                │              │
         ▼                ▼              ▼
  RuleRepository   SettingsRepository  ForwardHistoryRepository
         │                │              │
         ▼                ▼              ▼
      Use Cases (domain layer)
         │
         ▼
   ViewModels (presentation layer)
```

### Entities

| Entity | Purpose |
|--------|---------|
| `RuleEntity` | Forwarding rules (sender match → target number) |
| `AppSettingsEntity` | App-wide settings (roaming, history, prefixes) |
| `ForwardHistoryEntity` | Log of forwarded messages |

### Settings (AppSettingsEntity)

| Field | Default | Description |
|-------|---------|-------------|
| `sendWhileRoaming` | `false` | Allow forwarding when network is roaming |
| `keepHistory` | `true` | Record forwarded messages |
| `defaultPrefix` | `+351` | Country code prepended to local numbers |
| `forwardPrefix` | `SMS Redirect:` | Text prepended to forwarded message body |
| `showNotifications` | `true` | Show a notification after each successful forward |

## Permissions

The app requires the following permissions, requested in order:

| # | Permission | Purpose |
|---|-----------|---------|
| 1 | `READ_SMS` | Access SMS content |
| 2 | `RECEIVE_SMS` | Receive SMS broadcast |
| 3 | `READ_CONTACTS` | Pick contacts for rules |
| 4 | `SEND_SMS` | Forward messages via SMS |
| 5 | `READ_PHONE_STATE` | Check roaming status |
| 6 | Battery Optimization exemption | Prevent Doze from throttling |
| 7 | **Notification Access** | Capture RCS messages (last, via system settings) |

## Presentation Layer

Uses Jetpack Compose with a simple NavHost:

- **SplashScreen** → **HomeScreen** (rule list with drag-to-reorder)
- **HomeScreen** → **RuleScreen** (create/edit rule)
- **HomeScreen** → **SettingsScreen** (toggles, prefix config)
- **SettingsScreen** → **HistoryScreen** (last 50 forwarded messages)

Deep links are supported via `smsredirect://app` URI scheme.

### Duplicate Rule Prevention

When saving a rule, the ViewModel checks all existing rules (post-normalization) for an identical `senderText` + `targetNumber` pair. If found, the save is blocked with an error message.

## Dependency Wiring

Manual DI via `AppContainer` (created in `SmsRedirectApplication.onCreate()`). No framework — just constructor injection through a single data class holding all use cases, the `SmsForwarder` instance, and cross-cutting services like `ContactNameResolver`.

## Contact Resolution

The `ContactNameResolver` interface (in `domain/contacts/`) resolves phone numbers to display names from the device contacts. The Android implementation (`AndroidContactNameResolver` in `data/contacts/`) queries the system `ContentResolver`. This is used by `SmsForwarder` to enrich forwarded-message notifications with readable sender names.
