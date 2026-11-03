# SMS Toolbox

Android app built with Kotlin + Gradle Kotlin DSL implementing MVVM clean architecture (domain use cases, repository, and Room database).

Three features, as bottom-navigation tabs: **Backup** SMS to your own Gmail account, **Redirect** incoming SMS by rule, and **Schedule** messages to send later.

## Quick start

### Prerequisites

- Android Studio (latest stable)
- Android SDK for API 33+
- JDK 17 (used by modern Android Gradle builds)

### Build, test, and lint

```bash
./gradlew assembleDebug
./gradlew test
./gradlew lint
```

### Install on a connected device

```bash
./gradlew installDebug
```

## Features

### SMS Backup to Gmail
- Copies SMS messages to your own Gmail account over IMAP (`imap.gmail.com:993`, TLS)
- Authenticates with a Gmail **app password** you supply; verified on save, stored encrypted with an Android Keystore key
- Files messages under a Gmail label of your choice (default: `SMS`), created on first use
- Configurable email subject template with `$CONTACT_NAME` and `$PHONE_NUMBER` placeholders
- Groups each contact's messages into one Gmail conversation, and preserves each message's original timestamp
- Backs up both received and sent SMS (not drafts, failed sends or MMS). A sent message is picked up within about two minutes
- "Backup all previous SMS" backfills your existing messages; otherwise only messages received or sent after you enable it
- Resumable: progress is stored in the database, so a killed app continues where it left off and never uploads a message twice
- Progress notification while a backup runs

> **Why IMAP APPEND and not SMTP:** a Gmail label *is* an IMAP folder, so APPEND can file messages under your chosen label. SMTP can only deliver to the inbox, stamps everything with the send time (so years of history would all be dated today), and would hit Gmail's ~500/day sending cap during a backfill.

### Message Capture & Forwarding
- Background SMS forwarding via BroadcastReceiver (high priority 999)
- WorkManager-based forwarding with expedited execution and retry on failure
- Roaming-aware forwarding (configurable "Send while roaming" setting)
- Configurable forward prefix prepended to forwarded messages (default: `SMS Toolbox:`)
- Multipart SMS support for long messages

### Rule Management
- Home screen to list, add, edit, and delete rules with drag-and-drop reordering
- Rule screen to define a rule name, sender (text or contact), and target number (with contact picker)
- Dual rule matching: sender text substring match (case-insensitive) OR exact digit match
- First matching rule wins (evaluated in sort order)
- Auto-generated rule names ("Rule #N") when left blank
- Duplicate rule prevention (same sender + target blocked)
- Phone number validation (minimum 4 digits, restricted to `0-9 + - ( )`)
- Rule deletion with confirmation dialog
- Default +351 country code for numbers without a country prefix

### Settings
- Send while roaming toggle (default: off)
- Keep history toggle (default: on)
- Show notifications toggle (default: off) — posts a system notification after each successful forward
- Forward prefix text field (default: `SMS Toolbox:`)
- Gmail account section — address and app password, verified against Gmail when saved
- Default country prefix dropdown (None / +351 Portugal)
- Forward history viewer (last 50 forwarded messages with sender, target, rule, timestamp, and body)

### General
- Splash screen with app emoji and name
- Contextual runtime permission requests (SMS, Contacts, Notifications) with a rationale dialog shown before each system prompt, rather than an upfront onboarding screen
- Battery optimization exemption prompt shown after creating your first rule or scheduled SMS, with a persistent warning banner on Home if declined
- Deep link support (`smsredirect://app` URI scheme)
- Contact name resolution for forwarded-message notifications
- Message deduplication (30-second window prevents double-forwards)
- Dark mode support (Material3 dynamic colors)
- Android 13+ support (min SDK 33, target SDK 37)

## Testing on a device (sideloaded via ADB)

When deploying the app from Android Studio or ADB, it is considered **sideloaded**. On Android 13+, the system restricts SMS permissions for sideloaded apps by default — the permission toggle may be grayed out, or may appear to grant successfully but not actually take effect, in both cases.

### Required setup after install

1. Go to **Settings > Apps > SMS Toolbox**
2. Tap the **three-dot menu** (top right)
3. Select **"Allow restricted settings"**
4. Back in the app, add a rule or scheduled SMS to trigger the SMS permission dialog, then grant RECEIVE_SMS, SEND_SMS, and READ_SMS when prompted
5. Tap "pick contact" from a rule or scheduled SMS to trigger and grant **Contacts** permission if you want to use the contact picker

### Verifying the receiver works

Filter logcat to the app's tags while sending a test SMS:

```bash
adb logcat -s SmsReceiver ForwardSmsWorker SmsForwarder FindMatchingRule
```

You should see output like:

```
SmsReceiver: onReceive triggered — action=android.provider.Telephony.SMS_RECEIVED
SmsReceiver: SMS received from='+351...', bodyLength=12
ForwardSmsWorker: Enqueued forwarding work for sender='+351...'
ForwardSmsWorker: doWork() — sender='+351...', bodyLength=12
SmsForwarder: forward() called — sender='+351...', bodyLength=12
FindMatchingRule: Checking 1 rules against sender='...'
FindMatchingRule:   Rule id=1 name='...': senderText='...' matchesText=true
SmsForwarder: Matched rule id=1 — forwarding to '...'
SmsForwarder: SMS forwarded successfully to '...'
SmsForwarder: Forward saved to history
```

### Troubleshooting

| Symptom | Cause | Fix |
|---------|-------|-----|
| No `SmsReceiver` log at all | Restricted settings not enabled | Follow "Allow restricted settings" steps above |
| `Skipping: SEND_SMS permission not granted` | Missing SEND_SMS permission | Grant in system settings |
| `Skipping: no matching rule found` | Rule sender doesn't match incoming SMS | Check rule sender text matches the originating address |
| `Skipping: forwarding disabled while roaming` | Device is roaming and setting is off | Enable "Send while roaming" in app settings |
| `Failed: SmsManager is null` | No default SIM/subscription | Ensure a SIM is active |

### Test on the Emulator

#### How to test SMS broadcasts on the emulator

##### Method 1: Emulator Extended Controls (easiest)

1. Launch your emulator from Android Studio and make sure your app is installed.
2. Ensure SMS permissions are granted in emulator app settings (or via runtime permission prompt).
3. In the emulator sidebar, click the three dots (`...`) to open **Extended Controls**.
4. In the left menu, select **Phone**.
5. Type a mock number in the **From** field (for example `555-1234`).
6. Type your test message in the SMS message field.
7. Click **Send Message**.

You should see a native Android SMS notification in the emulator and `onReceive()` should execute. You can place a breakpoint in `SmsReceiver` to inspect the intent payload.

##### Method 2: Terminal using ADB/telnet

1. List active devices/emulators:

```bash
adb devices
```

2. Connect to the emulator modem console (typically port `5554`):

```bash
telnet localhost 5554
```

If asked for an auth token, run:

```bash
cat ~/.emulator_console_auth_token
```

Then authenticate in telnet with:

```text
auth <token>
```

3. Send an inbound SMS:

```text
sms send 5551234 "This is a test message"
```

## Run tests

```bash
./gradlew test
```

## Firebase (Crashlytics, Analytics & Performance Monitoring)

The app integrates [Firebase](https://firebase.google.com/) for:

- **Crashlytics** — automatic crash reporting, plus non-fatal exception
  reporting for forwarding/sending failures.
- **Analytics** — anonymous, aggregate usage events for relevant app actions
  (e.g. a rule was created/updated/deleted, an SMS was forwarded, a scheduled
  SMS was sent, a setting was toggled). **No personal data is ever logged**
  (no phone numbers, contact names, or message contents) — see
  `domain/analytics/AnalyticsEvents.kt` for the full event catalog.

- **Performance Monitoring** — automatic app-start and rendering traces via
  the `com.google.firebase.firebase-perf` Gradle plugin. This was previously
  disabled because the plugin didn't support Android Gradle Plugin 9 (it
  relied on the removed legacy `Transform` API — see
  [firebase-android-sdk#7293](https://github.com/firebase/firebase-android-sdk/issues/7293)),
  but a compatible plugin release has since shipped, so it's re-enabled.

### Setup

The repository ships with `app/google-services.json` wired to the
`sms-redirect-schedule` Firebase project (package name
`com.neteinstein.smsredirect`), so Crashlytics/Analytics/Performance
Monitoring report out of the box after building the app.

To point the app at a different Firebase project instead:

1. Create a Firebase project at the [Firebase console](https://console.firebase.google.com/).
2. Add an Android app with package name `com.neteinstein.smsredirect`.
3. Download the generated `google-services.json` and replace
   `app/google-services.json` with it.
4. Rebuild the app — Crashlytics and Analytics start reporting automatically.

The analytics/crash abstractions (`domain/analytics/AnalyticsTracker.kt` and
`domain/analytics/CrashReporter.kt`) decouple the rest of the app from
Firebase, so the backend could be swapped without touching call sites.

## Architecture

See [Architecture.md](Architecture.md) for detailed system design documentation including the message capture pipeline, rule matching logic, data layer, and dependency wiring.

## Privacy

See [PrivacyPolicy.md](PrivacyPolicy.md) for the app's privacy policy — all rules, schedules, and history stay on-device; only anonymous Firebase crash/analytics data ever leaves it.

## Tech Stack

- **Language:** Kotlin
- **Build:** Gradle Kotlin DSL
- **UI:** Jetpack Compose + Material3
- **Navigation:** Jetpack Navigation Compose
- **Database:** Room (SQLite)
- **Background work:** WorkManager (expedited)
- **Analytics/Crash reporting:** Firebase (Analytics, Crashlytics)
- **Architecture:** MVVM + Clean Architecture (Use Cases / Repository pattern)
- **DI:** Manual (AppContainer)
- **Min SDK:** 33 (Android 13)
