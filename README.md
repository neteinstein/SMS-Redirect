# SMS-Redirect

Android app built with Kotlin + Gradle Kotlin DSL implementing MVVM clean architecture (domain use cases, repository, and Room database) for SMS redirect rules.

## Features
- Splash screen with app logo text
- Home screen to list, add, edit, and delete rules
- Rule screen to define sender text and/or phone number (with contact picker)
- Runtime permission handling for SMS and Contacts
- Android 15+ support (min/target SDK 35)

## Run tests
```bash
./gradlew test
```
