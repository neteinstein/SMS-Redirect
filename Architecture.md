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
- Monitors notifications from Google Messages (`com.google.android.apps.messaging`), plus Samsung Messages and AOSP Messages as a bonus for non-Pixel devices
- Extracts new messages from `MessagingStyle` (used by Google Messages for RCS chats) — tracks a per-conversation last-seen timestamp so it forwards *every* new message in a burst, not just the latest one, since the OS can coalesce several messages into one notification update before the next callback
- Falls back to `EXTRA_BIG_TEXT`/`EXTRA_TEXT`/`EXTRA_TITLE` for notifications that aren't `MessagingStyle`
- Acts as a safety net for RCS chats and internet-based messaging — see "RCS Capture: Constraints & Known Limitations" below for why this is the *only* viable approach and what can still make it unreliable
- Logs `onListenerConnected`/`onListenerDisconnected` and writes diagnostic timestamps (`SharedPreferences`, not Room, to avoid `fallbackToDestructiveMigration()` wiping rules on schema change) so `MainActivity` can detect "granted but never bound" and self-heal via `NotificationListenerService.requestRebind()`
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

### RCS Capture: Constraints & Known Limitations

RCS has no OS-level interception point comparable to `SMS_RECEIVED`:

- There is no `RCS_RECEIVED` (or equivalent) broadcast on Android. Google's RCS engine (Jibe, bundled into Google Messages) stores RCS chat data in its own private app database, not in the shared `content://sms`/`content://mms` provider.
- `android.telephony.ims.RcsMessageStore` is the only public RCS API, but it requires **carrier privileges** — a signature-level trust relationship a sideloaded or Play Store app cannot obtain.
- **Becoming the default SMS app is not a viable alternative.** Only the platform's default SMS app may host Google's RCS (Jibe) client; switching the default SMS app away from Google Messages disables RCS device-wide and falls back to plain SMS/MMS for everyone. This would make the exact symptom this app tries to fix (missed RCS messages) permanent and universal rather than fixing it.

Given the above, `NotificationListenerService` reading Google Messages' notifications is the only realistic non-root interception method — but it comes with platform limitations that can look like app bugs:

| Limitation | Detail |
|---|---|
| Restricted settings (Android 13+) | Sideloaded builds silently block "Notification access" the same way they block SMS permissions, independent of the SMS restricted-settings toggle. See README "Required setup after install". |
| Listener granted but never bound | Known Pixel/OEM bug: the system can leave the service "enabled" in Settings without ever binding it. `RcsNotificationListener` now records a connect timestamp in `SharedPreferences`; `MainActivity` checks it on every resume and calls `NotificationListenerService.requestRebind()` when access is granted but the listener has never connected. |
| Per-category filtering (Android 14+) | Settings > Notification access > app now exposes independent toggles for Conversations / Alerting / Silent / Ongoing. The manifest declares `android.service.notification.default_filter_types = conversations\|alerting\|ongoing\|silent` to request all of them, but a user or OEM tool can still narrow this per-app. |
| OTP redaction (Android 15+) | The platform redacts one-time-passcode content from notifications before *any* `NotificationListenerService` — including this one — can read it. This is a platform anti-fraud measure with no opt-out for third-party apps; forwarding of RCS-delivered verification codes is inherently best-effort. |
| Best-effort delivery | Even when fully connected and unfiltered, this is a notification-shade mirror, not a message-store subscription: if Google Messages ever posts a MessagingStyle update without a genuinely new message (e.g. a read-receipt-only refresh), `selectNewMessages` intentionally forwards nothing new — verified by unit tests in `RcsNotificationListenerTest`. |

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
| 7 | **Notification Access** | Capture RCS messages (last, via system settings; see "RCS Capture: Constraints & Known Limitations" above — this permission is subject to restricted-settings blocking on sideloaded builds, just like the runtime permissions above) |

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
