"""Unit tests for play_publish.py, against a fake Play client. Run: python3 -m unittest."""

import unittest

from play_publish import PlayError, console_fix_for, publish

MUST_NOT_SET = (
    "Changes are sent for review automatically. The query parameter changesNotSentForReview must not be set."
)
MUST_SET = (
    "Changes cannot be sent for review automatically. Please set the query parameter "
    "changesNotSentForReview to true. Once all changes are ready to be reviewed, send them for review "
    "manually."
)


class FakePlay:
    """Accepts a commit only with [accepted_flag]; optionally discards the edit on a failed commit."""

    def __init__(self, accepted_flag, discard_edit_on_failure=False, draft_app=False, other_error=None):
        self.accepted_flag = accepted_flag
        self.discard_edit_on_failure = discard_edit_on_failure
        self.draft_app = draft_app
        self.other_error = other_error
        self.calls = []
        self.edits = 0
        self.live_edits = set()
        self.committed = []

    def insert_edit(self):
        self.edits += 1
        edit = f"e{self.edits}"
        self.live_edits.add(edit)
        self.calls.append(("insert", edit))
        return edit

    def upload_bundle(self, edit, aab):
        self.calls.append(("upload", edit))
        return 20001700

    def update_track(self, edit, track, version_code, status):
        self.calls.append(("track", edit, status))
        if self.draft_app and status != "draft":
            raise PlayError(400, "Only releases with status draft may be created on draft app.")

    def commit(self, edit, flag):
        self.calls.append(("commit", edit, flag))
        if edit not in self.live_edits:
            raise PlayError(404, f"Edit {edit} not found.")
        if self.other_error:
            raise PlayError(403, self.other_error)
        if flag != self.accepted_flag:
            if self.discard_edit_on_failure:
                self.live_edits.discard(edit)
            raise PlayError(400, MUST_SET if self.accepted_flag else MUST_NOT_SET)
        self.committed.append((edit, flag))


class PublishTest(unittest.TestCase):
    def test_commits_without_the_flag_when_play_sends_changes_for_review_automatically(self):
        play = FakePlay(accepted_flag=False)

        self.assertEqual(20001700, publish(play, "app.aab", "internal"))
        self.assertEqual([("e1", False)], play.committed)

    def test_retries_with_the_flag_when_play_requires_it(self):
        play = FakePlay(accepted_flag=True)

        publish(play, "app.aab", "internal")

        self.assertEqual([("e1", True)], play.committed)
        self.assertEqual(1, sum(1 for c in play.calls if c[0] == "upload"), "bundle uploaded once")

    def test_retries_without_the_flag_when_play_refuses_it(self):
        # Runs 90 and 91: the flag was hardcoded to true and Play refused it.
        play = FakePlay(accepted_flag=False)

        publish(play, "app.aab", "internal", review_flag=True)

        self.assertEqual([("e1", False)], play.committed)

    def test_starts_a_new_edit_when_the_failed_commit_discarded_it(self):
        play = FakePlay(accepted_flag=True, discard_edit_on_failure=True)

        publish(play, "app.aab", "internal")

        self.assertEqual([("e2", True)], play.committed)

    def test_creates_a_draft_release_while_the_app_is_a_draft(self):
        play = FakePlay(accepted_flag=False, draft_app=True)

        publish(play, "app.aab", "internal")

        self.assertIn(("track", "e1", "draft"), play.calls)
        self.assertEqual([("e1", False)], play.committed)

    def test_other_errors_are_not_retried(self):
        play = FakePlay(accepted_flag=False, other_error="The caller does not have permission")

        with self.assertRaises(PlayError):
            publish(play, "app.aab", "internal")
        self.assertEqual(1, sum(1 for c in play.calls if c[0] == "commit"))

    def test_console_only_errors_get_an_explanation(self):
        # Runs 87 to 89.
        message = (
            "Your release could not be rolled out because the following APKs would use sensitive "
            "permissions that have not been declared: 20001300."
        )
        self.assertIn("Sensitive app permissions", console_fix_for(message))
        self.assertIsNone(console_fix_for(MUST_NOT_SET))


if __name__ == "__main__":
    unittest.main()
