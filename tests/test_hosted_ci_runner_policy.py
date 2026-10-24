"""Keep public-repository CI on GitHub-hosted Ubuntu runners.

Local/self-hosted machines are for explicitly dispatched laboratory work,
not mandatory checks on pushes to master.
"""

from pathlib import Path
import re
import unittest


WORKFLOWS = Path(__file__).resolve().parents[1] / ".github" / "workflows"
RUNS_ON = re.compile(r"^\s+runs-on:\s*(.+)$", re.MULTILINE)


class HostedCIRunnerPolicyTest(unittest.TestCase):
    def test_all_workflow_jobs_use_github_hosted_ubuntu(self):
        files = sorted(WORKFLOWS.glob("*.yml")) + sorted(WORKFLOWS.glob("*.yaml"))
        self.assertTrue(files, "Expected GitHub Actions workflow definitions")
        count = 0
        for file in files:
            values = RUNS_ON.findall(file.read_text(encoding="utf-8"))
            with self.subTest(workflow=file.name):
                self.assertTrue(values, "Expected at least one declared runner")
            for value in values:
                count += 1
                with self.subTest(workflow=file.name, runner=value):
                    self.assertEqual(value.strip(), "ubuntu-latest")
        self.assertGreater(count, 0)


if __name__ == "__main__":
    unittest.main()
