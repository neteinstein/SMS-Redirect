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

The **Publish to Play Store** workflow runs in two ways:

- **Automatically after a GitHub release** — on merge to `main`, the **Build & Release APK** workflow runs first (`ktlintCheck` and `test` have already passed as part of PR checks before the merge). Once that workflow successfully creates the GitHub Release, it triggers **Publish to Play Store**, which builds the AAB and publishes it to the **internal** track. If the release workflow fails, nothing is published to the Play Store.
- **Manually, for other tracks** — to publish to `alpha`, `beta`, or `production`, trigger it by hand:
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
- **Version code already exists** — Version codes are derived from the GitHub Actions run number, which always increases, so this should only happen if a version was also uploaded manually or from another source. Increment `versionCode` in `app/build.gradle.kts` if needed.
- **First upload error** — Google requires the very first upload to be done manually through the Play Console.
- **Publish job skipped** — The **Build & Release APK** workflow failed (or hasn't finished yet) for that push to `main`, so **Publish to Play Store** was never triggered. Check the **Build & Release APK** run logs; nothing is published to the Play Store until it succeeds.

## Security Notes

- Never commit the keystore file or service account JSON to the repository.
- All sensitive values are stored as GitHub encrypted secrets.
- The workflow cleans up decoded secret files after execution (even on failure).

## MCP Integration (Claude Code)

This repo's `.mcp.json` configures the [`play-store-mcp`](https://github.com/lusky3/play-store-mcp) MCP server, letting a Claude Code session query and manage the Play Console directly (releases, store listings, reviews, subscriptions, testers) instead of going through the Play Console UI or GitHub Actions.

### Setup

The MCP server reuses the **same GitHub secret** already set up for CI publishing (`PLAY_STORE_SERVICE_ACCOUNT_JSON`, see [Configure GitHub Repository Secrets](#3-configure-github-repository-secrets)) rather than a separate local key file.

1. Confirm the `PLAY_STORE_SERVICE_ACCOUNT_JSON` repository secret exists and its service account has the Play Console permissions from [Setup Steps](#1-create-a-google-cloud-service-account) above: **Release to production, exclude devices, and use Play App Signing** and **Manage store presence**.
2. Whatever runs the MCP server (a Claude Code environment, a GitHub Actions job, etc.) must expose that secret as the environment variable `PLAY_STORE_SERVICE_ACCOUNT_JSON` — full JSON key contents, not a file path. `.mcp.json` passes it through to the server as `GOOGLE_PLAY_STORE_CREDENTIALS`, the inline-JSON credential form `play-store-mcp` expects.
3. Ensure [`uv`](https://docs.astral.sh/uv/)/`uvx` is installed — Claude Code launches the server with `uvx play-store-mcp`, which fetches and runs it on demand.
4. Restart Claude Code (or run `/mcp`) so it picks up `.mcp.json`.

### Access level

The server is configured with full read/write access — it **can** deploy builds, promote releases across tracks (including to production), and reply to reviews, not just read data. Treat prompts that trigger a release or a public reply the same as you would a manual Play Console action: review what's about to happen before confirming. To restrict it to read-only, set `PLAY_STORE_MCP_READ_ONLY=true` in the `env` block of `.mcp.json`.

### Security Notes

- No credential material lives in the repo — `.mcp.json` only references the `PLAY_STORE_SERVICE_ACCOUNT_JSON` env var name, and its value comes solely from the GitHub secret.
- This is the same service account/secret used by the **Publish to Play Store** workflow, so rotating or revoking it (Step 1) affects both CI publishing and the MCP server identically.
