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
- Forward prefix text field (default: `SMS Redirect:`)
- Default country prefix dropdown (None / +351 Portugal)
- Forward history viewer (last 50 forwarded messages with sender, target, rule, timestamp, and body)

### General
- Splash screen with app emoji and name
- Runtime permission handling for SMS, Contacts, and Phone State
- Permission status banner on Home and Settings screens showing missing permissions
- Battery optimization exemption prompt at startup
- Notification Access prompt for RCS capture
- Dark mode support (Material3 dynamic colors)
- Android 15+ support (min/target SDK 35)

## Testing on a device (sideloaded via ADB)

When deploying the app from Android Studio or ADB, it is considered **sideloaded**. On Android 13+, the system restricts SMS permissions for sideloaded apps by default — the permission toggle may be grayed out even if it appears granted.

### Required setup after install

1. Go to **Settings > Apps > SMS Redirect**
2. Tap the **three-dot menu** (top right)
3. Select **"Allow restricted settings"**
4. Go back to **Permissions** and grant all SMS permissions (RECEIVE_SMS, SEND_SMS, READ_SMS)
5. Also grant **Contacts** permission if you want to use the contact picker

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
RcsNotificationListener: RCS/message notification from 'Contact' via com.google.android.apps.messaging, bodyLength=15
ForwardSmsWorker: Enqueued forwarding work for sender='Contact'
```

### Troubleshooting

| Symptom | Cause | Fix |
|---------|-------|-----|
| No `SmsReceiver` log at all | Restricted settings not enabled | Follow "Allow restricted settings" steps above |
| `Skipping: SEND_SMS permission not granted` | Missing SEND_SMS permission | Grant in system settings |
| `Skipping: no matching rule found` | Rule sender doesn't match incoming SMS | Check rule sender text matches the originating address |
| `Skipping: forwarding disabled while roaming` | Device is roaming and setting is off | Enable "Send while roaming" in app settings |
| `Failed: SmsManager is null` | No default SIM/subscription | Ensure a SIM is active |
| No `RcsNotificationListener` log | Notification Access not granted | Enable in Settings > Notifications > Notification access |
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

## Architecture

See [Architecture.md](Architecture.md) for detailed system design documentation including the message capture pipeline, rule matching logic, data layer, and dependency wiring.

## Tech Stack

- **Language:** Kotlin
- **Build:** Gradle Kotlin DSL
- **UI:** Jetpack Compose + Material3
- **Navigation:** Jetpack Navigation Compose
- **Database:** Room (SQLite)
- **Background work:** WorkManager (expedited)
- **Architecture:** MVVM + Clean Architecture (Use Cases / Repository pattern)
- **DI:** Manual (AppContainer)
- **Min SDK:** 35 (Android 15)
