"""Prevent CI workflow artifact uploads from consuming storage on non-tag refs."""

from pathlib import Path
import re
import unittest


WORKFLOW_DIR = Path(__file__).resolve().parents[1] / ".github" / "workflows"
UPLOAD = re.compile(r"^\s+uses:\s*actions/upload-artifact@")
STEP = re.compile(r"^      - (?:name|uses|run):")
IF = re.compile(r"^        if:\s*(.+)")
TAG_GUARD = re.compile(r"""github\.ref_type\s*==\s*['"]tag['"]""")


class ActionsArtifactTagPolicyTest(unittest.TestCase):
    def test_every_upload_step_requires_tag_ref(self):
        workflows = sorted(WORKFLOW_DIR.glob("*.yml"))
        workflows += sorted(WORKFLOW_DIR.glob("*.yaml"))
        self.assertTrue(workflows, "No GitHub Actions workflows found")
        uploads = 0
        for workflow in workflows:
            lines = workflow.read_text(encoding="utf-8").splitlines()
            for index, line in enumerate(lines):
                if not UPLOAD.match(line):
                    continue
                uploads += 1
                start = next(
                    (i for i in range(index, -1, -1) if STEP.match(lines[i])),
                    None,
                )
                with self.subTest(workflow=workflow.name, line=index + 1):
                    self.assertIsNotNone(start, "Upload action is not inside a step")
                    if start is None:
                        continue
                    conditions = [
                        match.group(1)
                        for item in lines[start:index]
                        if (match := IF.match(item))
                    ]
                    self.assertEqual(
                        len(conditions), 1, "Upload step requires one explicit if"
                    )
                    if conditions:
                        # The owner may explicitly request a short-lived sideload
                        # build on master. Keep this exception restricted to one
                        # named CI step and one exact commit-message trigger.
                        requested_debug_apk = (
                            workflow.name == "ci.yml"
                            and lines[start].strip()
                            == "- name: Upload requested sideload APK"
                        )
                        if requested_debug_apk:
                            self.assertEqual(
                                conditions[0],
                                "${{ github.event_name == 'push' && github.ref == 'refs/heads/master' && contains(github.event.head_commit.message, '[request-debug-apk]') }}",
                            )
                        else:
                            self.assertRegex(
                                conditions[0], TAG_GUARD,
                                "Actions artifacts may only upload from tagged refs",
                            )
        self.assertGreater(uploads, 0, "No Actions artifact upload steps were checked")


if __name__ == "__main__":
    unittest.main()
