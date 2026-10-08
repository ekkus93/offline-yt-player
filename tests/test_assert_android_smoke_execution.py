"""Regression tests for the Android instrumentation execution guard."""

import tempfile
import unittest
from pathlib import Path

from scripts.assert_android_smoke_execution import (
    executed_test_classes,
    missing_required_classes,
)


class AndroidSmokeExecutionGuardTests(unittest.TestCase):
    def test_real_testcase_counts_as_executed(self):
        with tempfile.TemporaryDirectory() as tmp:
            Path(tmp, "TEST-gateway.xml").write_text(
                '<testsuite name="suite"><testcase '
                'classname="com.example.Gateway" name="roundTrip"/></testsuite>',
                encoding="utf-8",
            )
            self.assertEqual({"com.example.Gateway"}, executed_test_classes(Path(tmp)))
            self.assertEqual([], missing_required_classes(Path(tmp), {"com.example.Gateway"}))

    def test_skipped_and_missing_classes_do_not_count(self):
        with tempfile.TemporaryDirectory() as tmp:
            Path(tmp, "TEST-skipped.xml").write_text(
                '<testsuite><testcase classname="com.example.Gateway" '
                'name="roundTrip"><skipped/></testcase></testsuite>',
                encoding="utf-8",
            )
            self.assertEqual(
                ["com.example.Gateway", "com.example.Missing"],
                missing_required_classes(Path(tmp), {"com.example.Gateway", "com.example.Missing"}),
            )

    def test_empty_report_directory_is_not_success(self):
        with tempfile.TemporaryDirectory() as tmp:
            self.assertEqual(
                ["com.example.Gateway"],
                missing_required_classes(Path(tmp), {"com.example.Gateway"}),
            )

    def test_invalid_report_is_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            Path(tmp, "TEST-corrupt.xml").write_text("<testsuite", encoding="utf-8")
            with self.assertRaises(ValueError):
                executed_test_classes(Path(tmp))


if __name__ == "__main__":
    unittest.main()
