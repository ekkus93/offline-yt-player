"""Read-only preflight regression for exact-head Android screenshot golden source."""

from pathlib import Path
import unittest

from scripts.register_download_setup_golden_variant import (
    REVIEWED_HASHES,
    TARGET,
    verify_pinned_golden_source,
)


class GoldenSourcePreflightTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.source = TARGET.read_text(encoding="utf-8")

    def test_committed_source_contains_reviewed_variants_and_barrier(self):
        verify_pinned_golden_source(self.source)

    def test_missing_hash_fails_closed(self):
        modified = self.source.replace(REVIEWED_HASHES[0], "0" * 64, 1)
        with self.assertRaisesRegex(ValueError, "must be checked in"):
            verify_pinned_golden_source(modified)

    def test_missing_compose_idle_barrier_fails_closed(self):
        modified = self.source.replace(
            '        compose.onNodeWithText("Quality choices").assertIsDisplayed()\n'
            '        compose.waitForIdle()\n',
            '        compose.onNodeWithText("Quality choices").assertIsDisplayed()\n',
            1,
        )
        with self.assertRaisesRegex(ValueError, "idle barrier"):
            verify_pinned_golden_source(modified)

    def test_preflight_does_not_write_source(self):
        original = TARGET.read_bytes()
        verify_pinned_golden_source(self.source)
        self.assertEqual(TARGET.read_bytes(), original)


if __name__ == "__main__":
    unittest.main()
