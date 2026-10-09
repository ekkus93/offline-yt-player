"""Regression tests for the Android instrumentation execution guard."""

import tempfile
import unittest
from pathlib import Path

from scripts.assert_android_smoke_execution import (
    executed_test_classes,
    missing_required_classes,
    executed_test_methods,
    missing_required_methods,
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

    def test_required_bitmap_methods_must_each_execute_non_skipped(self):
        with tempfile.TemporaryDirectory() as tmp:
            Path(tmp, "TEST-goldens.xml").write_text(
                '<testsuite><testcase classname="com.example.Goldens" name="library"/>'
                '<testcase classname="com.example.Goldens" name="settings"><skipped/></testcase>'
                '</testsuite>',
                encoding="utf-8",
            )
            self.assertEqual({("com.example.Goldens", "library")}, executed_test_methods(Path(tmp)))
            self.assertEqual(
                ["com.example.Goldens#settings"],
                missing_required_methods(Path(tmp), {("com.example.Goldens", "library"), ("com.example.Goldens", "settings")}),
            )

    def test_invalid_report_is_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            Path(tmp, "TEST-corrupt.xml").write_text("<testsuite", encoding="utf-8")
            with self.assertRaises(ValueError):
                executed_test_classes(Path(tmp))


if __name__ == "__main__":
    unittest.main()
