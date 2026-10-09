"""Exercise the RMD-1401a smoke driver without an emulator or Gradle download."""

import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path


REPO = Path(__file__).resolve().parents[1]
DRIVER = REPO / "scripts/run_required_android_smoke.sh"
CHECKER = REPO / "scripts/assert_android_smoke_execution.py"


class RequiredSmokeDriverTests(unittest.TestCase):
    def run_driver(self, omit_class: str | None = None):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            scripts = root / "scripts"
            scripts.mkdir()
            shutil.copy2(DRIVER, scripts / DRIVER.name)
            shutil.copy2(CHECKER, scripts / CHECKER.name)
            (root / "gradlew").write_text(
                """#!/usr/bin/env python3
import pathlib
import sys
import xml.etree.ElementTree as ET
args = [arg for arg in sys.argv if arg.startswith('-Pandroid.testInstrumentationRunnerArguments.class=')]
assert len(args) == 1, sys.argv
name = args[0].split('=', 1)[1]
with pathlib.Path('gradle-calls.log').open('a') as out:
    out.write(name + '\\n')
root = pathlib.Path('app/build/outputs/androidTest-results/connected')
root.mkdir(parents=True, exist_ok=True)
if name != '__OMITTED__':
    suite = ET.Element('testsuite')
    ET.SubElement(suite, 'testcase', classname=name, name='runs')
    ET.ElementTree(suite).write(root / 'TEST-mock.xml', encoding='unicode')
""".replace("__OMITTED__", omit_class or "NO_CLASS_OMITTED"),
                encoding="utf-8",
            )
            (root / "gradlew").chmod(0o755)
            (root / "app/build/reports/androidSmokeLogs").mkdir(parents=True)
            result = subprocess.run(
                ["sh", "scripts/run_required_android_smoke.sh"],
                cwd=root,
                capture_output=True,
                text=True,
                check=False,
            )
            calls = (root / "gradle-calls.log").read_text().splitlines()
            kept = list((root / "app/build/reports/androidSmokeLogs").rglob("TEST-mock.xml"))
            return result, calls, kept

    def test_all_required_classes_run_individually_and_keep_evidence(self):
        result, calls, reports = self.run_driver()
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertEqual(3, len(calls))
        self.assertEqual(3, len(set(calls)))
        self.assertTrue(any("GeneratedUniffiCoreGatewaySmokeTest" in c for c in calls))
        self.assertTrue(any("Rmd1504ShareE2EInstrumentedTest" in c for c in calls))
        self.assertTrue(any("Rmd1506ConnectivityE2EInstrumentedTest" in c for c in calls))
        self.assertEqual(3, len(reports))

    def test_missing_executed_class_fails_instead_of_false_green(self):
        omitted = "com.ekkus.offlineytplayer.Rmd1504ShareE2EInstrumentedTest"
        result, calls, _ = self.run_driver(omit_class=omitted)
        self.assertNotEqual(0, result.returncode)
        self.assertIn("ANDROID_SMOKE_MISSING_REQUIRED " + omitted, result.stdout)
        self.assertEqual(2, len(calls))


if __name__ == "__main__":
    unittest.main()
