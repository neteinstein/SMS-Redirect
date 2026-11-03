# Testing instructions for Google Play

This document has two audiences and covers both, since Play Console asks for
each separately: **App content > App access** (what a Google reviewer needs
to see every feature) and the **internal testing track** (what a human tester
on your team needs to do before a release is promoted further).

## 1. App content → App access

Play Console asks: *"Do you need to provide access instructions...?"* — i.e.
is any part of the app gated behind a login the reviewer can't reach on their
own.

**Answer: Yes, provide access instructions.** `com.neteinstein.smsredirect`
has no developer-operated account system and no login screen (see
[PrivacyPolicy.md](../PrivacyPolicy.md), §10), so every screen is reachable
without a special "unlock" step — but the app's **declared core use case is
"Backup and restore"** (see the
[README's permissions-declaration section](README.md#before-this-can-go-live-the-sms-permissions-declaration)),
and that feature only does something once the user enters a Gmail address and
app password in Settings. A reviewer with no credentials sees the Backup
tab's empty/disabled state, not the feature actually working, which is enough
to make the "Backup and restore" declaration look unsubstantiated. Mark the
declaration as requiring access instructions and fill in the
**"Instructions"** free-text field with something like:

> No login is required to use most of this app — every screen is reachable
> without special access. The app's primary feature (SMS backup to Gmail,
> declared under "Backup and restore") requires the user to supply their own
> Gmail address and a 16-character Gmail **app password** in Settings →
> Gmail backup. We've provided a disposable test Gmail account below so you
> can exercise this feature directly; app passwords require 2-Step
> Verification to be enabled on the Google Account, which we've done for
> this account:
>
> - Email: *(disposable test Gmail address — do not use a real/personal account)*
> - App password: *(generate at [myaccount.google.com/apppasswords](https://myaccount.google.com/apppasswords) and paste here)*
>
> Steps: open the app (it lands on the Backup tab) → tap the gear/settings
> icon in the top app bar → "Gmail backup" → enter the address and app
> password above → Save (this validates the login against Gmail) → turn on
> "Backup all previous SMS" if you want existing messages included → back to
> the Backup tab → toggle it on → grant the SMS and notification permission
> prompts that appear → receive an SMS on the test device, or send one from
> it → within a couple of minutes the message appears as an email in the
> test account's Gmail, under the "SMS" label (sent messages are marked
> `To:` / `Sent:` instead of `From:` / `Received:`).

Generate a fresh app password for this specific submission, and treat it as
a **standing** credential for as long as the App access entry lists it —
see the **Security notes** at the end of this document for how to rotate it
safely instead of just revoking it. Never reuse a personal account's
credentials here.

### Demonstration video (Permissions declaration form)

The same **App content > Permissions declaration form** that requires the
"Backup and restore" use case also asks for a short demonstration video.
A rendered walkthrough of the full app, covering every permission prompt
(SMS, contacts, notifications, battery exemption), is in
[`video/`](video/README.md): use it as the shot list, or as a placeholder
until you have a device recording. Record one take, screen-recorded on a real
device (not an emulator, so the SMS flow is real):

1. Fresh install → launch (the app opens directly on the Backup tab; no
   permission prompt yet — this app requests permissions contextually, not
   on launch).
2. Tap the gear/settings icon in the top app bar → "Gmail backup" → enter
   the test Gmail address + app password from above → Save → show the
   success state (it verifies the login against Gmail before saving).
   Optionally turn on "Backup all previous SMS" to include existing messages.
3. Back to the Backup tab → toggle backup on → grant the SMS and
   notification permission prompts that appear at this point.
4. Receive an SMS on the test device, then send a reply from the usual
   messaging app. Both are backed up: received and sent messages alike.
5. Switch to the Gmail app/web on the same test account → show the message
   arriving as an email under the configured label (default: `SMS`).
6. Restore: on the Backup tab tap **Restore messages** → read the 3-step
   dialog → Continue → accept the system "set as default SMS app" prompt →
   show the progress → tap **Switch back to your messaging app** and pick the
   original app again. This shows the reviewer that the default-SMS role is
   used only for the restore.
7. Optionally show that Redirect and Schedule are separate, secondary tabs —
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
- [ ] Permissions are requested contextually, not on first launch: enabling
      Backup prompts for `READ_SMS`/`RECEIVE_SMS`/`SEND_SMS` and, separately,
      `POST_NOTIFICATIONS`; denying one doesn't crash the app, just disables
      the dependent feature.
- [ ] Settings (gear icon) → Gmail backup: entering a bad app password fails
      visibly at Save time (the app validates by connecting, not just by
      format).
- [ ] "Backup all previous SMS" defaults to **off** — with it off, enabling
      Backup sets a start-time watermark and only backs up messages that
      arrive from that point forward; existing SMS are *not* uploaded. Turn
      it on and confirm existing messages get backed up too.
- [ ] With valid credentials saved and backup on, receiving a new SMS
      triggers an upload.
- [ ] Sending an SMS from the usual messaging app also triggers an upload,
      within about two minutes (the trigger waits for the SMS provider to go
      quiet first). The email body starts with `To:` / `Sent:` and
      "Show original" shows `X-SMS-Type: sent`. A draft, or a message that
      failed to send, is **not** uploaded.
- [ ] Marking a message read, or deleting a thread, does not upload
      anything or duplicate an existing backup.
- [ ] Restore a backed-up sent message: it lands in the **sent** box, and the
      next backup run does not upload it again.
- [ ] Confirm in Gmail: each backed-up message is a separate email under the
      configured label, and re-running backup doesn't duplicate already-backed-up
      messages (the local hash record dedupes — see `PrivacyPolicy.md` §1).
- [ ] Turn backup off, then back on — it should resume, not re-upload everything.
- [ ] Gmail → open a backed-up email → "Show original": the `X-SMS-Toolbox-Format`,
      `X-SMS-Type`, `X-SMS-Address` and `X-SMS-Date` headers are present.

**Restore (Backup tab → Restore from Gmail)**
- [ ] Delete a few SMS on the device, tap **Restore messages**, accept the
      dialog and the system default-SMS prompt. The deleted messages come
      back in the right conversations with their original dates. Everything
      else counts as "already on this phone", with no duplicates.
- [ ] Run the restore a second time: 0 restored, everything already on the phone.
- [ ] Decline the system prompt: the card explains why it is needed, and nothing
      runs.
- [ ] Mid-restore, switch the default SMS app back in system Settings: the
      restore stops with a clear message instead of reporting thousands of
      failures.
- [ ] After a restore, the card and a notification both prompt you to switch
      back. Until you do, a banner stays on the Backup tab. Once you switch
      back, the banner disappears when you return to the app.
- [ ] While SMS Toolbox is still the default SMS app, receive an SMS: it is saved
      to the inbox (visible after switching back), and a notification without
      any message content says it arrived.
- [ ] Restore onto a fresh install (new device or cleared storage): after the
      restore, backup must **not** re-upload the restored messages to Gmail.
- [ ] Restore from a label containing backups made **before** this release
      (no `X-SMS-*` headers): they restore from the email body, inbox only, and
      nothing is duplicated when some are already on the phone.
- [ ] A label name that doesn't exist in Gmail fails with "No backups found",
      and no empty label is created in Gmail.
- [ ] **Do this one before release:** after a normal backup, open a backup email
      in Gmail → "Show original" and confirm there is **no** `Received: from …`
      header. Restore rejects any mail that has one, to block forged backups.
      If Gmail ever adds one to APPENDed mail, every restore would skip everything.
- [ ] File an ordinary email, and an email *sent* to the account that copies a
      backup's headers, under the label. Restore skips both, writes neither,
      and the result says "N emails … were not SMS backups and were skipped".

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
      confirm WorkManager still delivers it via its delayed `OneTimeWorkRequest`
      (unlike SMS forwarding, scheduled sends are not expedited work — this
      is what the battery-optimization exemption prompt is for: without it,
      Doze can defer the delayed work past its target time).

**Cross-cutting**
- [ ] Revoke SMS permission from Android Settings mid-use; confirm the
      affected feature degrades gracefully (per `PrivacyPolicy.md` §13)
      instead of crashing.
- [ ] Deep link `smsredirect://app` opens the app (used by any external
      trigger, e.g. a notification action).
- [ ] No message content appears in `adb logcat` output for any of the above
      (`CLAUDE.md`: never log message content in production).

### Security notes

- Never use a real/personal Gmail account for the app-access reviewer
  credentials or the demo video — use a disposable test account created
  solely for this purpose.
- The App access entry's credentials need to stay valid for as long as it's
  listed — Google re-reviews apps periodically, not just at submission — so
  don't revoke the app password right after your own review. If you need to
  rotate it, generate the new app password first, **update the Play Console
  App access entry with it**, and only then revoke the old one at
  [myaccount.google.com/apppasswords](https://myaccount.google.com/apppasswords);
  revoking before updating the Console entry leaves a later reviewer locked
  out.
- Never paste real credentials into this repository. The example fields
  above are placeholders — fill them into the Play Console form directly,
  not into a commit.
