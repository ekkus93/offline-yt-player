"""Verify expensive Android qualification is scoped to immutable commit identity."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
WORKFLOWS = {
    ".github/workflows/android-smoke.yml": "android-smoke",
    ".github/workflows/android-fgs-timeout.yml": "android-fgs-timeout",
    ".github/workflows/android-cold-start.yml": "android-cold-start",
    ".github/workflows/android-real-network-e2e.yml": "android-real-network-e2e",
}

class AndroidWorkflowConcurrencyTest(unittest.TestCase):
    def test_each_android_lane_uses_exact_sha(self):
        for path, prefix in WORKFLOWS.items():
            with self.subTest(path=path):
                source = (ROOT / path).read_text(encoding="utf-8")
                self.assertIn("group: " + prefix + "-${{ github.sha }}", source)
                self.assertNotIn("group: " + prefix + "-${{ github.ref }}", source)
                self.assertIn("cancel-in-progress: true", source)

if __name__ == "__main__":
    unittest.main()
