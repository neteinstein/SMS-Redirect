#!/usr/bin/env python3
"""Upload an Android App Bundle to a Google Play track.

Replaces a third-party action whose `changesNotSentForReview` input had to be hardcoded. Play
requires that query parameter in some publishing states (e.g. after a rejected update, or with
managed publishing) and refuses it in others ("Changes are sent for review automatically. The
query parameter changesNotSentForReview must not be set."). The state changes in Play Console
without any change here, so every fixed value has eventually broken the release (#67, #72, #76).

Instead this script uploads the bundle once, then commits the edit. When Play rejects the commit
because of that parameter, it commits again with the opposite setting. It also falls back to a
draft release while the app itself is still a draft, and explains the errors that only a change
in Play Console can fix.

Needs `google-auth` and `requests`. Usage:
  play_publish.py --key play-store-key.json --package com.example --aab app.aab --track internal
"""

from __future__ import annotations

import argparse
import os
import sys

API = "https://androidpublisher.googleapis.com/androidpublisher/v3/applications/{package}"
UPLOAD_API = "https://androidpublisher.googleapis.com/upload/androidpublisher/v3/applications/{package}"
SCOPE = "https://www.googleapis.com/auth/androidpublisher"
REVIEW_FLAG = "changesNotSentForReview"

# Errors no retry can fix: what the maintainer has to do, keyed by a fragment of Play's message.
CONSOLE_ONLY_ERRORS = [
    (
        "sensitive permissions that have not been declared",
        "Play needs a permissions declaration for this version. In Play Console go to "
        "Policy and programs > App content > Sensitive app permissions (SMS and Call Log), "
        "declare the core use case (Default SMS handler) and submit it. Then upload again with "
        "the 'Publish to Play Store' workflow.",
    ),
    (
        "has already been used",
        "Play already has this versionCode. Run the 'Publish to Play Store' workflow with a "
        "higher build_number, or merge another change so the version is bumped.",
    ),
    (
        "does not have permission",
        "The service account in PLAY_STORE_SERVICE_ACCOUNT_JSON lacks access to this app. "
        "Grant it release permissions under Users and permissions in Play Console.",
    ),
]


class PlayError(Exception):
    def __init__(self, status: int, message: str):
        super().__init__(f"HTTP {status}: {message}")
        self.status = status
        self.message = message


def flipped_review_flag(message: str, current: bool) -> bool | None:
    """The other `changesNotSentForReview` setting when Play rejected [current], else None."""
    return (not current) if REVIEW_FLAG in message else None


def is_draft_app_error(message: str) -> bool:
    """"Only releases with status draft may be created on draft app." (app never published)."""
    return "draft app" in message.lower()


def is_edit_gone(error: PlayError) -> bool:
    message = error.message.lower()
    return error.status == 404 or ("edit" in message and any(w in message for w in ("deleted", "expired", "not found")))


def console_fix_for(message: str) -> str | None:
    for fragment, fix in CONSOLE_ONLY_ERRORS:
        if fragment in message:
            return fix
    return None


def warn(message: str) -> None:
    print(f"::warning::{message}", flush=True)


def prepare_edit(client, aab: str, track: str, status: str) -> tuple[str, int, str]:
    """Opens an edit holding [aab] on [track]. Returns the edit id, versionCode and status used."""
    edit = client.insert_edit()
    version_code = client.upload_bundle(edit, aab)
    try:
        client.update_track(edit, track, version_code, status)
    except PlayError as error:
        if status == "draft" or not is_draft_app_error(error.message):
            raise
        warn(f"The app is still a draft in Play Console, so this release is created as a draft instead of '{status}'.")
        status = "draft"
        client.update_track(edit, track, version_code, status)
    return edit, version_code, status


def publish(client, aab: str, track: str, status: str = "completed", review_flag: bool = False) -> int:
    """Uploads and commits, switching `changesNotSentForReview` if Play asks for the other one."""
    edit, version_code, status = prepare_edit(client, aab, track, status)
    try:
        client.commit(edit, review_flag)
        return version_code
    except PlayError as error:
        other = flipped_review_flag(error.message, review_flag)
        if other is None:
            raise
        warn(f"Play rejected {REVIEW_FLAG}={str(review_flag).lower()} ({error.message}); committing again with {str(other).lower()}.")

    try:
        client.commit(edit, other)
        return version_code
    except PlayError as error:
        if flipped_review_flag(error.message, other) is not None or not is_edit_gone(error):
            raise
    # The failed commit discarded the edit; nothing was published, so start over once.
    edit, version_code, _ = prepare_edit(client, aab, track, status)
    client.commit(edit, other)
    return version_code


class PlayClient:
    """The few Android Publisher API v3 calls this needs."""

    def __init__(self, key_file: str, package: str):
        from google.auth.transport.requests import AuthorizedSession
        from google.oauth2 import service_account

        credentials = service_account.Credentials.from_service_account_file(key_file, scopes=[SCOPE])
        self.session = AuthorizedSession(credentials)
        self.api = API.format(package=package)
        self.upload_api = UPLOAD_API.format(package=package)

    def _call(self, method: str, url: str, **kwargs) -> dict:
        response = self.session.request(method, url, timeout=600, **kwargs)
        if response.status_code >= 400:
            try:
                message = response.json()["error"]["message"]
            except (ValueError, KeyError, TypeError):
                message = response.text
            raise PlayError(response.status_code, message)
        return response.json() if response.content else {}

    def insert_edit(self) -> str:
        return self._call("POST", f"{self.api}/edits")["id"]

    def upload_bundle(self, edit: str, aab: str) -> int:
        with open(aab, "rb") as bundle:
            result = self._call(
                "POST",
                f"{self.upload_api}/edits/{edit}/bundles",
                params={"uploadType": "media"},
                headers={"Content-Type": "application/octet-stream"},
                data=bundle,
            )
        return int(result["versionCode"])

    def update_track(self, edit: str, track: str, version_code: int, status: str) -> None:
        release = {"versionCodes": [str(version_code)], "status": status}
        self._call("PUT", f"{self.api}/edits/{edit}/tracks/{track}", json={"track": track, "releases": [release]})

    def commit(self, edit: str, changes_not_sent_for_review: bool) -> None:
        params = {REVIEW_FLAG: "true"} if changes_not_sent_for_review else None
        self._call("POST", f"{self.api}/edits/{edit}:commit", params=params)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--key", required=True, help="service account JSON file")
    parser.add_argument("--package", required=True)
    parser.add_argument("--aab", required=True)
    parser.add_argument("--track", required=True)
    parser.add_argument("--status", default="completed")
    args = parser.parse_args(argv)

    try:
        version_code = publish(PlayClient(args.key, args.package), args.aab, args.track, args.status)
    except PlayError as error:
        fix = console_fix_for(error.message)
        print(f"::error::Google Play rejected the upload: {error.message}", flush=True)
        if fix:
            print(f"::error::This needs a change in Play Console, not in the code. {fix}", flush=True)
        return 1

    summary = f"Uploaded versionCode {version_code} to the Play '{args.track}' track."
    print(summary)
    if os.environ.get("GITHUB_STEP_SUMMARY"):
        with open(os.environ["GITHUB_STEP_SUMMARY"], "a") as out:
            out.write(summary + "\n")
    return 0


if __name__ == "__main__":
    sys.exit(main())
