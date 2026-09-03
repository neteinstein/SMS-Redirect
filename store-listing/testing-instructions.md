# Testing instructions for Google Play

This document has two audiences and covers both, since Play Console asks for
each separately: **App content > App access** (what a Google reviewer needs
to see every feature) and the **internal testing track** (what a human tester
on your team needs to do before a release is promoted further).

## 1. App content → App access

Play Console asks: *"Do you need to provide access instructions...?"* — i.e.
is any part of the app gated behind a login the reviewer can't reach on their
own.

**Answer: No special access is required for the app itself.** `com.neteinstein.smsredirect`
has no developer-operated account system and no login screen (see
[PrivacyPolicy.md](../PrivacyPolicy.md), §7) — every screen is reachable the
moment the runtime permissions are granted.

That said, the app's **declared core use case is "Backup and restore"**
(see the [README's permissions-declaration section](README.md#before-this-can-go-live-the-sms-permissions-declaration)),
and the backup feature only does something once the user enters a Gmail
address and app password in Settings. A reviewer who only taps around
without those credentials will see the Backup tab's empty/disabled state,
not the feature actually working. To avoid a review bounce, still fill in
the **"Instructions"** free-text field with something like:

> No login is required to use this app. All screens are reachable without
> special access. The app's primary feature (SMS backup to Gmail, declared
> under "Backup and restore") requires the user to supply their own Gmail
> address and a 16-character Gmail **app password** in Settings → Gmail
> backup. We've provided a disposable test Gmail account below so you can
> exercise this feature directly; app passwords require 2-Step Verification
> to be enabled on the Google Account, which we've done for this account:
>
> - Email: *(disposable test Gmail address — do not use a real/personal account)*
> - App password: *(generate at [myaccount.google.com/apppasswords](https://myaccount.google.com/apppasswords) and paste here)*
>
> Steps: open the app → grant SMS/notifications permissions when prompted →
> Settings tab → "Gmail backup" → enter the address and app password above →
> Save (this validates the login against Gmail) → Backup tab → toggle it on
> → send/receive an SMS on the test device → the message appears as an email
> in the test account's Gmail, under the "SMS" label.

Generate a fresh app password for this specific submission and revoke it
afterward — see the **Security notes** at the end of this document instead
of reusing a personal account's credentials.

### Demonstration video (Permissions declaration form)

The same **App content > Permissions declaration form** that requires the
"Backup and restore" use case also asks for a short demonstration video.
Record one take, screen-recorded on a real device (not an emulator, so the
SMS flow is real):

1. Fresh install → grant SMS, Contacts, and Notifications permissions when prompted.
2. Settings tab → "Gmail backup" → enter the test Gmail address + app password from
   above → Save → show the success state (it verifies the login against Gmail
   before saving).
3. Backup tab (the app opens directly on this tab) → toggle backup on.
4. Receive (or send, from a second device) an SMS on the test device.
5. Switch to the Gmail app/web on the same test account → show the message
   arriving as an email under the configured label (default: `SMS`).
6. Optionally show that Redirect and Schedule are separate, secondary tabs —
   the reviewer should come away with backup as the app's obvious purpose.

## 2. Internal testing track (pre-release QA)

Automated `ktlintCheck` + `test` (see `.github/workflows/pr-checks.yml`) catch
regressions in logic, but **nothing in CI exercises the app on a device** —
`READ_SMS`/`RECEIVE_SMS` behavior, the Gmail IMAP round-trip, and notification
delivery all need a real phone with a live SIM (an emulator can fake incoming
SMS via `adb emu sms send`, but cannot receive real carrier SMS or test actual
Gmail delivery end-to-end). Run this checklist against the `internal` track
build before promoting to `alpha`/`beta`/`production`.

### Setting up testers

1. [Play Console](https://play.google.com/console) → your app → **Testing >
   Internal testing**.
2. **Testers** tab → create or edit the email list, add each tester's Google
   account email.
3. Share the **opt-in URL** from that tab with testers — they must open it and
   tap "Become a tester" before the app becomes visible to their Play Store
   account, even after a build is live on the track.
4. A build reaches this track automatically on merge to `main` (see
   [PublishToStore.md](../PublishToStore.md)); testers get it as a normal Play
   Store update once opted in.

### Manual test script

Run through all three tabs — the bottom nav order is Backup → Redirect →
Schedule, and Backup is intentionally the tab the app opens on (Play policy
reasons, not incidental).

**Backup tab**
- [ ] First launch prompts for `READ_SMS`/`RECEIVE_SMS`/`POST_NOTIFICATIONS`;
      denying one doesn't crash the app, just disables the dependent feature.
- [ ] Settings → Gmail backup: entering a bad app password fails visibly at
      Save time (the app validates by connecting, not just by format).
- [ ] With valid credentials saved, toggling backup on scans and uploads
      existing SMS, then keeps uploading new messages as they arrive.
- [ ] Confirm in Gmail: each backed-up message is a separate email under the
      configured label, and re-running backup doesn't duplicate already-backed-up
      messages (the local hash record dedupes — see `PrivacyPolicy.md` §3a).
- [ ] Turn backup off, then back on — it should resume, not re-upload everything.

**Redirect tab**
- [ ] Create two rules that could both match the same incoming sender; the
      rule earlier in the list order (`sortOrder`) must be the one that fires
      — **first match wins**, per `CLAUDE.md`. Reorder them and confirm the
      other one fires instead.
- [ ] Set a non-empty `forwardPrefix` in Settings containing a `$` character
      (e.g. `Fwd: $5 owed —`) and confirm it's forwarded literally, not
      interpreted as a regex backreference.
- [ ] Confirm the forwarded SMS actually sends via the device's own carrier
      (check the target device/number receives it) and shows up in History.
- [ ] Toggle "Keep history" off in Settings and confirm History stops
      recording new entries.

**Schedule tab**
- [ ] Create a scheduled message a few minutes out; confirm it fires at the
      scheduled time via `SmsManager` and appears in Schedule history.
- [ ] Test a repeating schedule; confirm it reschedules itself after firing.
- [ ] Kill the app (swipe away from recents) before the scheduled time fires;
      confirm WorkManager still delivers it (this is what the expedited work
      request + battery-optimization exemption prompt exist for).

**Cross-cutting**
- [ ] Revoke SMS permission from Android Settings mid-use; confirm the
      affected feature degrades gracefully (per `PrivacyPolicy.md` §10)
      instead of crashing.
- [ ] Deep link `smsredirect://app` opens the app (used by any external
      trigger, e.g. a notification action).
- [ ] No message content appears in `adb logcat` output for any of the above
      (`CLAUDE.md`: never log message content in production).

### Security notes

- Never use a real/personal Gmail account for the app-access reviewer
  credentials or the demo video — use a disposable test account created
  solely for this purpose.
- Revoke the test account's app password at
  [myaccount.google.com/apppasswords](https://myaccount.google.com/apppasswords)
  once the review completes; app passwords grant full mailbox access.
- Never paste real credentials into this repository. The example fields
  above are placeholders — fill them into the Play Console form directly,
  not into a commit.
