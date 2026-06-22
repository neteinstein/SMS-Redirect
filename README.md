# SMS-Redirect

Android app built with Kotlin + Gradle Kotlin DSL implementing MVVM clean architecture (domain use cases, repository, and Room database) for SMS redirect rules.

## Features
- Splash screen with app logo text
- Home screen to list, add, edit, and delete rules with drag-and-drop reordering
- Rule screen to define a rule name, sender (text or contact), and target number (with contact picker)
- Auto-generated rule names ("Rule #N") when left blank
- Default +351 country code for numbers without an indicative
- Settings screen with a persisted "Send while in roaming" switch (default false)
- Runtime permission handling for SMS and Contacts
- Permission status banner on home screen showing missing permissions
- Battery optimization prompt at startup
- Background SMS forwarding via BroadcastReceiver with roaming-aware forwarding
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
adb logcat -s SmsReceiver SmsForwarder FindMatchingRule
```

You should see output like:

```
SmsReceiver: onReceive triggered — action=android.provider.Telephony.SMS_RECEIVED
SmsReceiver: SMS received from='+351...', bodyLength=12
SmsForwarder: forward() called — sender='+351...', bodyLength=12
FindMatchingRule: Checking 1 rules against sender='...'
FindMatchingRule:   Rule id=1 name='...': senderText='...' matchesText=true
SmsForwarder: Matched rule id=1 — forwarding to '...'
SmsForwarder: SMS forwarded successfully to '...'
```

### Troubleshooting

| Symptom | Cause | Fix |
|---------|-------|-----|
| No `SmsReceiver` log at all | Restricted settings not enabled | Follow "Allow restricted settings" steps above |
| `Skipping: SEND_SMS permission not granted` | Missing SEND_SMS permission | Grant in system settings |
| `Skipping: no matching rule found` | Rule sender doesn't match incoming SMS | Check rule sender text matches the originating address |
| `Skipping: forwarding disabled while roaming` | Device is roaming and setting is off | Enable "Send while roaming" in app settings |
| `Failed: SmsManager is null` | No default SIM/subscription | Ensure a SIM is active |

## Run tests

```bash
./gradlew test
```
