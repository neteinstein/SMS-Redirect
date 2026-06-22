# SMS-Redirect

Android app built with Kotlin + Gradle Kotlin DSL implementing MVVM clean architecture (domain use cases, repository, and Room database) for SMS redirect rules.

## Features
- Splash screen with app logo text
- Home screen to list, add, edit, and delete rules
- Rule screen to define sender text and/or phone number (with contact picker) and a target number
- Settings screen with a persisted "Send while in roaming" switch (default false)
- Runtime permission handling for SMS and Contacts
- Battery optimization prompt at startup
- Background SMS forwarding service/receiver with roaming-aware forwarding
- Android 15+ support (min/target SDK 35)

## Run tests
```bash
./gradlew test
```
