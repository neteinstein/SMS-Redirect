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

1. Open the [Google Play Console](https://play.google.com/console).
2. Go to **Settings > API access**.
3. Link the Google Cloud project if not already linked.
4. Find your service account and click **Manage permissions**.
5. Grant the following permissions:
   - **Release to production, exclude devices, and use Play App Signing**
   - **Manage store presence**
6. Apply permissions to the app (`com.neteinstein.smsredirect`).

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
3. Complete all store listing requirements (description, screenshots, content rating, etc.).
4. Publish at least once to any track (internal testing is sufficient).

### 5. Running the Workflow

1. Go to the repository's **Actions** tab on GitHub.
2. Select the **Publish to Play Store** workflow.
3. Click **Run workflow**.
4. Select the desired release track:
   - `internal` — Internal testing (default, recommended for initial testing)
   - `alpha` — Closed testing
   - `beta` — Open testing
   - `production` — Public release
5. Click **Run workflow** to start the publish process.

## Troubleshooting

- **403 Forbidden** — The service account does not have sufficient permissions in the Play Console. Verify the permissions from Step 2.
- **APK/AAB not found** — Ensure the build step completes successfully before the upload step.
- **Version code already exists** — Increment `versionCode` in `app/build.gradle.kts` before publishing.
- **First upload error** — Google requires the very first upload to be done manually through the Play Console.

## Security Notes

- Never commit the keystore file or service account JSON to the repository.
- All sensitive values are stored as GitHub encrypted secrets.
- The workflow cleans up decoded secret files after execution (even on failure).
