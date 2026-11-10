# Publishing to Google Play Store

This document describes the configuration required to enable automated publishing of the SMS Redirect app to the Google Play Store via GitHub Actions.

## Prerequisites

1. **Google Play Developer Account** — You must have access to the [Google Play Console](https://play.google.com/console).
2. **App already created** — The app (`com.neteinstein.smsredirect`) must already exist in the Play Console with at least one manual upload completed (Google requires the first upload to be done manually).
3. **Google Cloud Service Account** — A service account with permissions to publish to the Play Store.

## Setup Steps

### 1. Create a Google Cloud Service Account

1. Go to the [Google Cloud Console](https://console.cloud.google.com/).
2. Select or create a project linked to your Play Console.
3. Navigate to **IAM & Admin > Service Accounts**.
4. Click **Create Service Account**.
5. Give it a descriptive name (e.g., `play-store-publisher`).
6. Skip granting project-level roles (permissions are granted in Play Console).
7. Click **Done**, then click on the created service account.
8. Go to the **Keys** tab, click **Add Key > Create new key**, and select **JSON**.
9. Download the JSON key file — you'll need this later.

### 2. Grant Permissions in Google Play Console

Google has moved this flow out of **API access**. Service account permissions are now granted from **Users and permissions**.

1. In the [Google Cloud Console](https://console.cloud.google.com/), copy the service account's email address (**IAM & Admin > Service Accounts**).
2. Open the [Google Play Console](https://play.google.com/console).
3. (Optional check) Go to **Setup > API access** to confirm the Google Cloud project is linked to this Play Console account. Link it if it isn't yet — there's no "Manage permissions" button here anymore.
4. Go to **Users and permissions** and click **Invite new users**.
5. Paste the service account's email address as the user.
6. Under the **App permissions** tab, click **Add app**, select `com.neteinstein.smsredirect`, and check:
   - **Release to production, exclude devices, and use Play App Signing**
   - **Manage store presence**
7. Click **Apply**, then **Invite user**. Service accounts get access immediately — there's no invite email to accept.

### 3. Configure GitHub Repository Secrets

Navigate to your repository **Settings > Secrets and variables > Actions** and add the following secrets:

| Secret Name | Description |
|---|---|
| `KEYSTORE_BASE64` | Base64-encoded release keystore file. Generate with: `base64 -i your-keystore.jks \| tr -d '\n'` |
| `KEYSTORE_PASSWORD` | Password for the keystore |
| `KEY_ALIAS` | Alias of the signing key within the keystore |
| `KEY_PASSWORD` | Password for the signing key |
| `PLAY_STORE_SERVICE_ACCOUNT_JSON` | The full contents of the Google Cloud service account JSON key file |

### 4. First Manual Upload

Before the automated workflow can publish updates, you must:

1. Build a signed AAB (Android App Bundle) locally.
2. Upload it manually via the Google Play Console.
3. Complete all store listing requirements (description, screenshots, content rating, etc.). For the required **Privacy Policy URL**, use the raw GitHub link to [PrivacyPolicy.md](PrivacyPolicy.md), e.g. `https://github.com/neteinstein/SMS-Redirect/blob/main/PrivacyPolicy.md`.
4. Publish at least once to any track (internal testing is sufficient).

### 5. Running the Workflow

Google Play gets the **play** flavor only: Backup and Restore, no `SEND_SMS`, no self-update
(see `app/build.gradle.kts` and `domain/model/AppFeatures.kt`). The **github** flavor, with every
feature, goes to GitHub releases. Both come from the same commit, with the same `applicationId`,
signing key and `versionCode`.

- **Automatically on merge to `main`** — the **Build & Release** workflow (`release.yml`) bumps the
  patch version, builds the signed GitHub APK and Play bundle, checks that the Play bundle's merged
  manifest does not request `SEND_SMS` or `REQUEST_INSTALL_PACKAGES`, creates the GitHub Release with
  the APK, and uploads the bundle to the **internal** track. (`ktlintCheck` and `test` already passed
  as PR checks.) If any step fails, nothing reaches the Play Store.
- **Manually, for other tracks** — the **Publish to Play Store** workflow rebuilds the Play bundle
  from `main` and uploads it to `alpha`, `beta`, or `production`:
  1. Go to the repository's **Actions** tab on GitHub.
  2. Select the **Publish to Play Store** workflow.
  3. Click **Run workflow**.
  4. Select the desired release track:
     - `internal` — Internal testing (default, recommended for initial testing)
     - `alpha` — Closed testing
     - `beta` — Open testing
     - `production` — Public release
  5. Leave **build_number** at `1` (raise it if you upload the same version again): the automatic
     upload already used this version's `versionCode`, and Play rejects a repeated one.
  6. Click **Run workflow** to start the publish process.

## Troubleshooting

- **403 Forbidden** — The service account does not have sufficient permissions in the Play Console. Verify the permissions from Step 2.
- **APK/AAB not found** — Ensure the build step completes successfully before the upload step.
- **Version code already exists** — Version codes are derived from `versionName` (`major*10000000 + minor*100000 + patch*100 + build`, see `app/build.gradle.kts`), so the automatic upload of a version uses `build` 0. A manual upload of the same version needs a higher **build_number** input.
- **First upload error** — Google requires the very first upload to be done manually through the Play Console.
- **Play upload failed but the GitHub release exists** — The upload is the last step of **Build & Release**. Re-run that job, or run **Publish to Play Store** by hand for the `internal` track with **build_number** `1`.
- **"Play bundle manifest declares a forbidden permission"** — Something (an edit to `src/main/AndroidManifest.xml` or a new library) added `SEND_SMS` or `REQUEST_INSTALL_PACKAGES` to the Play build. Move it to `app/src/github/AndroidManifest.xml`, or add a `tools:node="remove"` entry to `app/src/play/AndroidManifest.xml`.

## Security Notes

- Never commit the keystore file or service account JSON to the repository.
- All sensitive values are stored as GitHub encrypted secrets.
- The workflow cleans up decoded secret files after execution (even on failure).
