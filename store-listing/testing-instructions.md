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
[PrivacyPolicy.md](../PrivacyPolicy.md), §12). The app's **declared core use
case is "Default SMS handler"** (see the
[README's permissions-declaration section](README.md#before-this-can-go-live-the-sms-permissions-declaration)):
the Messages tab works as soon as the reviewer accepts Android's "set as
default SMS app" prompt, with no credentials. Only the Backup and Restore tabs
need a Gmail account, so give the reviewer one. Fill in the **"Instructions"**
free-text field with something like:

> SMS Toolbox is an SMS/MMS messaging app. No login is required. On first
> launch it asks to become the default SMS app (Android's own prompt): accept
> it, then grant Contacts and Notifications. The Messages tab (first tab)
> shows every conversation on the device; tap + to write a new message, or
> open a conversation to reply. Attach a picture with the photo button to send
> an MMS. Incoming SMS/MMS appear in the list and as notifications with Reply
> and Mark as read.
>
> The Backup and Restore tabs back up SMS to the user's own Gmail and restore
> them. They need a Gmail address and a 16-character Gmail **app password** in
> Settings → Gmail backup. We've provided a disposable test Gmail account
> (2-Step Verification on, as app passwords require):
>
> - Email: *(disposable test Gmail address — do not use a real/personal account)*
> - App password: *(generate at [myaccount.google.com/apppasswords](https://myaccount.google.com/apppasswords) and paste here)*

Generate a fresh app password for this specific submission, and treat it as
a **standing** credential for as long as the App access entry lists it —
see the **Security notes** at the end of this document for how to rotate it
safely instead of just revoking it. Never reuse a personal account's
credentials here.

### Demonstration video (Permissions declaration form)

The **App content > Sensitive app permissions > Permissions declaration form**
asks you to pick the core use case — choose **"Default SMS handler"** — and for
a short video showing the app *working as a messaging app* and *how it becomes
the default SMS handler*. Record one take, screen-recorded on a real device with
a live SIM (not an emulator, so SMS and MMS are real):

1. Fresh install → launch. The splash hands over to the **Messages** tab, and
   the app immediately explains that it is a messaging app and offers to
   become the default SMS app → **Continue**.
2. Android's own "Set SMS Toolbox as your default SMS app?" prompt → accept.
   Point out that **no SMS permission was asked for before this**: the SMS
   permissions arrive with the role. Grant Contacts and Notifications when
   asked right after.
3. The Messages tab now lists the device's existing conversations, with
   contact names and unread markers.
4. Tap **+** → type or pick a contact → **Next** → write a message → **Send**.
   Show it going from "Sending…" to its timestamp.
5. From a second phone, send an SMS to the test device: show the notification
   (sender + text), reply from the notification, then open the conversation.
6. Picture message: in the conversation, tap the photo button, pick a photo,
   send it (the hint reads "Picture or group message (MMS)"). From the second
   phone, send a picture back and show it arriving in the conversation.
7. Briefly show the other tabs — Redirect, Schedule, Backup, Restore — as
   features built on top of the messaging app.
8. Optional: Android Settings → Apps → Default apps → SMS app shows SMS
   Toolbox selected.

Never show the test Gmail app password on screen.

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

This is the **play** flavor: every tab (Messages, Redirect, Schedule, Backup,
Restore), no self-update. The **github** flavor adds only the update check
(test it with `./gradlew installGithubDebug`, see "GitHub build" below).

**Default SMS app and Messages tab**
- [ ] Fresh install: the first thing asked is to become the **default SMS app**
      (in-app explanation, then Android's role prompt). No SMS runtime
      permission dialog appears before it, ever.
- [ ] Declining the role: Messages shows "Make SMS Toolbox your messaging app"
      with a button, and no SMS permission prompt appears. Accepting later
      from that button works.
- [ ] Accepting: Contacts and Notifications are asked right after; the inbox
      lists existing conversations, newest first, unread ones bold.
- [ ] New message (+): a typed number, a picked contact (no Contacts prompt —
      the picker grants the one number), and several recipients (group hint
      shown) all open the right conversation.
- [ ] Sending an SMS: "Sending…" then the time. Long text (over 160 chars) is
      sent as one message. Airplane mode → "Not sent. Tap to retry"; retry
      after re-enabling the radio sends it once.
- [ ] Receiving an SMS: notification shows sender and text (lock screen: only
      "New message"); Reply sends and clears it; Mark as read clears it; no
      notification while that conversation is open.
- [ ] MMS: send a photo (large photos are shrunk, not rejected); receive a
      photo from another phone (needs mobile data); receive with mobile data
      off → "Picture message not downloaded" → Retry after enabling data.
- [ ] Group: send to two numbers → one group conversation; a reply from either
      lands in it.
- [ ] From another app: a contact's message button and Share → "Message" open
      a pre-filled conversation; nothing is sent until Send is tapped.
- [ ] Long-press: copy text, delete a message, delete a conversation.
- [ ] Redirect / Schedule: a forwarded or scheduled SMS also appears in the
      target's conversation in Messages.
- [ ] Switching the default SMS app away in Android settings: Messages shows
      the prompt again; nothing crashes.

**Backup tab**
- [ ] Permissions are requested contextually, not on first launch: enabling
      Backup prompts for `READ_SMS`/`RECEIVE_SMS` (and **not** for sending
      SMS) and, separately,
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
      messages (the local hash record dedupes — see `PrivacyPolicy.md` §2).
- [ ] Turn backup off, then back on — it should resume, not re-upload everything.
- [ ] Gmail → open a backed-up email → "Show original": the `X-SMS-Toolbox-Format`,
      `X-SMS-Type`, `X-SMS-Address` and `X-SMS-Date` headers are present.

**Restore tab**
- [ ] Delete a few SMS on the device, tap **Restore messages**, accept the
      dialog (and the system default-SMS prompt, if SMS Toolbox is not already
      the default). The deleted messages come
      back in the right conversations with their original dates. Everything
      else counts as "already on this phone", with no duplicates.
- [ ] Run the restore a second time: 0 restored, everything already on the phone.
- [ ] With another app as default, decline the system prompt: a dialog explains
      why it is needed, and nothing runs.
- [ ] Mid-restore, switch the default SMS app back in system Settings: the
      restore stops with a clear message instead of reporting thousands of
      failures.
- [ ] Restored messages show up in the Messages tab straight away, in the
      right conversations; there is no prompt to switch to another app.
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

**Play build specifics**
- [ ] Settings has no Updates section, and the app never offers to install an
      APK.
- [ ] Upgrading from the previous (Backup & Restore only) Play build: the app
      opens on Messages and asks to become the default SMS app; Gmail backup
      settings are kept, and the landing-screen setting now reads Messages.

### GitHub build (APK on GitHub releases)

Not distributed through Play, but released from the same pipeline with the same
versionCode and signing key. Before a release, on a device with the GitHub APK:

**Updates**
- [ ] Install an older release APK, open the app: an "SMS Toolbox X is
      available" banner appears; Update opens Settings → Updates.
- [ ] Download and install: the first time, Android asks to allow installs
      from SMS Toolbox; after allowing, the download shows progress and the
      system installer opens. After updating, the banner is gone.
- [ ] Reopen the app within 24 hours: no second network check (watch
      `adb logcat` or a proxy); "Check for updates" forces one.

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
      affected feature degrades gracefully (per `PrivacyPolicy.md` §15)
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
