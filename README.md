# SMS-Redirect

Android app built with Kotlin + Gradle Kotlin DSL implementing MVVM clean architecture (domain use cases, repository, and Room database) for SMS and RCS redirect rules.

## Features

### Message Capture & Forwarding
- Background SMS forwarding via BroadcastReceiver (high priority 999)
- RCS/internet message capture via NotificationListenerService (Google Messages)
- WorkManager-based forwarding with expedited execution and retry on failure
- Roaming-aware forwarding (configurable "Send while roaming" setting)
- Configurable forward prefix prepended to forwarded messages (default: `SMS Redirect:`)
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
- Show notifications toggle (default: on) — posts a system notification after each successful forward
- Forward prefix text field (default: `SMS Redirect:`)
- Default country prefix dropdown (None / +351 Portugal)
- Forward history viewer (last 50 forwarded messages with sender, target, rule, timestamp, and body)

### General
- Splash screen with app emoji and name
- Runtime permission handling for SMS, Contacts, and Phone State
- Permission status banner on Home and Settings screens showing missing permissions
- Battery optimization exemption prompt at startup
- Notification Access prompt for RCS capture
- Deep link support (`smsredirect://app` URI scheme)
- Contact name resolution for forwarded-message notifications
- Message deduplication (30-second window prevents double-forwards)
- Dark mode support (Material3 dynamic colors)
- Android 15+ support (min/target SDK 35)

## Testing on a device (sideloaded via ADB)

When deploying the app from Android Studio or ADB, it is considered **sideloaded**. On Android 13+, the system restricts SMS permissions **and Notification Access** for sideloaded apps by default — the permission toggle may be grayed out, or may appear to grant successfully but not actually take effect, in both cases.

### Required setup after install

1. Go to **Settings > Apps > SMS Redirect**
2. Tap the **three-dot menu** (top right)
3. Select **"Allow restricted settings"**
4. Go back to **Permissions** and grant all SMS permissions (RECEIVE_SMS, SEND_SMS, READ_SMS)
5. Also grant **Contacts** permission if you want to use the contact picker
6. For RCS forwarding, grant **Notification Access** *after* step 3 — if it was already toggled on before "Allow restricted settings" was applied, toggle it off and back on so it re-registers correctly

### Verifying the receiver works

Filter logcat to the app's tags while sending a test SMS:

```bash
adb logcat -s SmsReceiver ForwardSmsWorker SmsForwarder FindMatchingRule RcsNotificationListener
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

For RCS messages (via Google Messages notifications):

```
RcsNotificationListener: Listener connected — system has bound the service and will deliver notifications
RcsNotificationListener: onNotificationPosted — package=com.google.android.apps.messaging, category=msg, flags=64, key=0|com.google.android.apps.messaging|...
RcsNotificationListener: RCS MessagingStyle from 'Contact' via com.google.android.apps.messaging, bodyLength=15
ForwardSmsWorker: Enqueued forwarding work for sender='Contact'
```

The `Listener connected` line only appears once per bind (right after Notification Access is granted, or after the app/device restarts). If you never see it, the system never bound the service at all — see "RCS is more fragile than SMS" below before assuming the app code is at fault.

### RCS is more fragile than SMS — read this before debugging further

There is **no `RCS_RECEIVED` broadcast** and no public content provider for RCS, unlike SMS/MMS:

- RCS has no carrier-independent, OS-level broadcast. Google's RCS client (Jibe, bundled into Google Messages) stores chat messages in its own private app database, not in the shared `content://sms`/`content://mms` provider that SMS/MMS use.
- The only public, non-root API surface for RCS content is `android.telephony.ims.RcsMessageStore`, which requires **carrier privileges** (signature-level trust from the carrier) — not something a sideloaded or Play Store app can obtain.
- That leaves `NotificationListenerService` (reading Google Messages' notifications) as the only realistic interception point for a third-party app. It's a **best-effort safety net, not a guaranteed delivery channel** — the app can only forward what the OS chooses to hand it, when it chooses to hand it.

Known platform-level limitations that look like app bugs but aren't:

| Symptom | Cause | Fix |
|---------|-------|-----|
| No `RcsNotificationListener` log at all, ever — not even `onNotificationPosted` for unrelated apps | Notification Access toggle didn't actually take effect | Restricted settings also gate Notification Access on sideloaded builds — see "Required setup after install" above, step 6 |
| `Listener connected` never appears, but Settings shows access as granted | Known Android/Pixel bug where the listener is enabled but never bound (e.g. after toggling permission, an app update, or firmware quirks) | The app now self-heals this: on every resume of the Permissions screen, if access is granted but the listener has never connected, it calls `NotificationListenerService.requestRebind()` automatically. If it's still stuck, manually toggle Notification Access off/on for the app, or reboot. |
| `Listener connected` appears, but no `onNotificationPosted` lines for `com.google.android.apps.messaging` even show up | Per-category notification filtering (Android 14+) | Settings > Apps > Special app access > Notification access > SMS Redirect now exposes toggles for **Conversations / Alerting / Silent / Ongoing**. Confirm all are enabled — the app declares them all by default, but the user or device can still narrow them. |
| `onNotificationPosted` fires but the forwarded message body is garbled/redacted (e.g. `••••••`) | **Android 15+ redacts one-time-passcode (OTP) content from notifications before any `NotificationListenerService` sees it** — a platform anti-fraud measure, not something this app can opt out of | Don't rely on this pipeline for 2FA/OTP codes; test with a plain conversational message first to confirm the pipeline itself works, then treat OTP forwarding as best-effort. |
| No log at all, and regular SMS forwarding also silently stopped working recently | App was auto-revoked from "unused apps" permission reset, or force-stopped | Open the app at least once; re-grant Notification Access if it shows as off. |

### Troubleshooting

| Symptom | Cause | Fix |
|---------|-------|-----|
| No `SmsReceiver` log at all | Restricted settings not enabled | Follow "Allow restricted settings" steps above |
| `Skipping: SEND_SMS permission not granted` | Missing SEND_SMS permission | Grant in system settings |
| `Skipping: no matching rule found` | Rule sender doesn't match incoming SMS | Check rule sender text matches the originating address |
| `Skipping: forwarding disabled while roaming` | Device is roaming and setting is off | Enable "Send while roaming" in app settings |
| `Failed: SmsManager is null` | No default SIM/subscription | Ensure a SIM is active |
| No `RcsNotificationListener` log | Notification Access not granted | Enable in Settings > Notifications > Notification access (see "RCS is more fragile than SMS" above if it still doesn't work) |
| `Ignoring notification from ... with empty body` | Notification has no text content | Expected for non-message notifications |

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

## Firebase (Crashlytics, Analytics & Performance)

The app integrates [Firebase](https://firebase.google.com/) for:

- **Crashlytics** — automatic crash reporting, plus non-fatal exception
  reporting for forwarding/sending failures.
- **Analytics** — anonymous, aggregate usage events for relevant app actions
  (e.g. a rule was created/updated/deleted, an SMS was forwarded, a scheduled
  SMS was sent, a setting was toggled). **No personal data is ever logged**
  (no phone numbers, contact names, or message contents) — see
  `domain/analytics/AnalyticsEvents.kt` for the full event catalog.
- **Performance Monitoring** — automatic app-start/rendering traces provided
  out of the box by the Firebase Performance SDK.

### Setup

The repository ships with a placeholder `app/google-services.json` so the
project builds out of the box, but it does **not** point at a real Firebase
project — Crashlytics/Analytics/Performance data won't be delivered anywhere
useful until you replace it:

1. Create a Firebase project at the [Firebase console](https://console.firebase.google.com/).
2. Add an Android app with package name `com.neteinstein.smsredirect`.
3. Download the generated `google-services.json` and replace
   `app/google-services.json` with it.
4. Rebuild the app — Crashlytics, Analytics and Performance start reporting
   automatically.

The analytics/crash abstractions (`domain/analytics/AnalyticsTracker.kt` and
`domain/analytics/CrashReporter.kt`) decouple the rest of the app from
Firebase, so the backend could be swapped without touching call sites.

## Architecture

See [Architecture.md](Architecture.md) for detailed system design documentation including the message capture pipeline, rule matching logic, data layer, and dependency wiring.

## Tech Stack

- **Language:** Kotlin
- **Build:** Gradle Kotlin DSL
- **UI:** Jetpack Compose + Material3
- **Navigation:** Jetpack Navigation Compose
- **Database:** Room (SQLite)
- **Background work:** WorkManager (expedited)
- **Analytics/Crash reporting/Performance:** Firebase (Analytics, Crashlytics, Performance Monitoring)
- **Architecture:** MVVM + Clean Architecture (Use Cases / Repository pattern)
- **DI:** Manual (AppContainer)
- **Min SDK:** 35 (Android 15)
