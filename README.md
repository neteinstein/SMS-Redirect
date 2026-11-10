# SMS Toolbox

Android app built with Kotlin + Gradle Kotlin DSL implementing MVVM clean architecture (domain use cases, repository, and Room database).

Three features, as bottom-navigation tabs: **Backup** SMS to your own Gmail account (and **restore** them to a new phone), **Redirect** incoming SMS by rule, and **Schedule** messages to send later.

## Two versions

| | Google Play (`play` flavor) | GitHub APK (`github` flavor) |
|---|---|---|
| Backup & Restore | ✅ | ✅ |
| Redirect & Schedule | — | ✅ |
| `SEND_SMS` permission | not declared | declared |
| Updates | by Google Play | in-app: checks GitHub releases once a day, banner → Settings → Updates |

Both are the same app: same `applicationId` (`com.neteinstein.smsredirect`), same signing key and the same `versionCode` for a given release, so either one installs over the other. Every merge to `main` publishes both from [`release.yml`](.github/workflows/release.yml). Get the APK from the [latest release](https://github.com/neteinstein/SMS-Redirect/releases/latest); see [Install the APK](#install-the-apk-outside-google-play) below.

[![SMS Toolbox launch video: Backup, Restore, Redirect, Schedule](store-listing/video/sms-toolbox-launch-preview.gif)](store-listing/video/sms-toolbox-launch.mp4)

*Silent preview. The full 24-second launch video, with music, is [`store-listing/video/sms-toolbox-launch.mp4`](store-listing/video/sms-toolbox-launch.mp4) (download it to watch).*

## Install the APK (outside Google Play)

The GitHub APK has every feature (Backup & Restore, Redirect, Schedule). Installing it takes about five minutes the first time; after that the app updates itself from GitHub. If you only need Backup & Restore, the [Google Play version](https://play.google.com/store/apps/details?id=com.neteinstein.smsredirect) is simpler.

> The pictures are illustrations of stock Android 14–16. Wording and menu locations differ a little between phone makers (Samsung, Xiaomi, OnePlus…), but the steps are the same.

### 1. Download the APK

On your phone, open the [latest release](https://github.com/neteinstein/SMS-Redirect/releases/latest) and, under **Assets**, tap **`SMS-Toolbox_v….apk`**. Only download it from this page. If your browser warns that *"this type of file can harm your device"*, tap **Download anyway**.

<img src="docs/images/install-apk/01-download.png" alt="The GitHub release page with the SMS-Toolbox APK under Assets" width="300">

### 2. Turn on Developer options

1. Open **Settings → About phone**.
2. Tap **Build number** seven times. Enter your PIN if asked.
3. You see **"You are now a developer!"**

<img src="docs/images/install-apk/02-build-number.png" alt="About phone, with Build number highlighted and the message You are now a developer" width="300">

Then open **Settings → System → Developer options** and make sure **Use developer options** is on. Nothing else in there needs changing.

<img src="docs/images/install-apk/03-developer-options.png" alt="Developer options with Use developer options turned on" width="300">

> Some phones only allow installing apps from outside Google Play with Developer options on. On many phones this step is not needed; it does no harm, and you can turn Developer options off again once the app is installed.

### 3. Allow your browser to install apps

Open the downloaded file (from the download notification, or **Files → Downloads**). The first time, Android says your phone *"isn't allowed to install unknown apps from this source"*. Tap **Settings**, turn on **Allow from this source**, then go back.

<img src="docs/images/install-apk/04-unknown-source.png" alt="Install unknown apps, with Allow from this source turned on for Chrome" width="300">

### 4. Install, and accept the warnings

1. Android asks **"Do you want to install this app?"**: tap **Install**.

   <img src="docs/images/install-apk/05-install.png" alt="The Android installer asking Do you want to install this app" width="300">

2. **Google Play Protect** may stop the install because the app did not come from Play. Tap **More details**, then **Install anyway**. If it offers to scan the app, you can let it: the APK is built from the public source in this repository by [`release.yml`](.github/workflows/release.yml).

   <img src="docs/images/install-apk/06-play-protect.png" alt="Play Protect's Unsafe app blocked dialog, with Install anyway highlighted" width="300">

### 5. Allow restricted settings (Android 13 and later)

Android blocks SMS permissions for apps installed outside an app store until you allow them once. The app shows a reminder the first time you open it.

1. Open **Settings → Apps → SMS Toolbox**.
2. Tap the **⋮** menu in the top-right corner, then **Allow restricted settings**, and confirm with your PIN or fingerprint.

<img src="docs/images/install-apk/07-restricted-settings.png" alt="App info for SMS Toolbox with the menu open on Allow restricted settings" width="300">

Now open SMS Toolbox. It asks for each permission only when a feature needs it.

### 6. Updates

Once a day, when you open it, the app checks this repository for a new release. When there is one, a banner appears at the top of the main screen: tap **Update**, then **Download and install** in **Settings → Updates**. The first time, Android asks you to allow SMS Toolbox to install apps (like step 3). Your settings and backups are kept.

<img src="docs/images/install-apk/08-in-app-update.png" alt="Settings, Updates section with Download and install, and the update banner" width="300">

Moving between the Play and GitHub versions, and what the update check sends, are covered in the [full install guide](docs/install-apk.md).

## Development

Building, testing, the feature list, on-device and emulator testing, Firebase setup, and the tech stack are in [Development.md](Development.md). The system design is in [Architecture.md](Architecture.md) and the privacy policy in [PrivacyPolicy.md](PrivacyPolicy.md).
