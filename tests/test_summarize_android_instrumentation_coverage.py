"""Android instrumented coverage requires real production UI execution."""
import unittest

from scripts.summarize_android_instrumentation_coverage import (
    CoverageError,
    verify_instrumented_ui_path,
)


class AndroidInstrumentationCoverageValidationTest(unittest.TestCase):
    def test_reports_separate_device_metric_and_known_ui_execution(self):
        xml = """<report><package name="com/ekkus/offlineytplayer/ui">
<sourcefile name="OfflineYTPlayerApp.kt">
<line nr="5" mi="0" ci="1" mb="0" cb="1"/>
<line nr="6" mi="1" ci="0" mb="1" cb="0"/></sourcefile>
</package><package name="com/ekkus/offlineytplayer/core">
<sourcefile name="GeneratedUniFFI.kt"><line nr="1" mi="0" ci="100"/></sourcefile>
</package></report>"""
        result = verify_instrumented_ui_path(xml)
        self.assertEqual(result["language"], "kotlin-android-instrumentation")
        self.assertEqual((result["line_covered"], result["line_total"]), (1, 2))
        self.assertEqual(result["verified_production_ui_files"],
                         [("OfflineYTPlayerApp.kt", 1)])

    def test_missing_instrumented_ui_lines_are_not_success(self):
        for xml in (
            "",
            "<report/>",
            '<report><package name="com/ekkus/offlineytplayer/ui">'
            '<sourcefile name="OfflineYTPlayerApp.kt">'
            '<line nr="5" mi="1" ci="0"/></sourcefile></package></report>',
            '<report><package name="com/ekkus/offlineytplayer/core">'
            '<sourcefile name="GeneratedUniFFI.kt">'
            '<line nr="5" mi="0" ci="1"/></sourcefile></package></report>',
            '<report><package name="com/ekkus/offlineytplayer/downloads">'
            '<sourcefile name="Worker.kt">'
            '<line nr="5" mi="0" ci="1"/></sourcefile></package></report>',
        ):
            with self.subTest(xml=xml):
                with self.assertRaises(CoverageError):
                    verify_instrumented_ui_path(xml)


if __name__ == "__main__":
    unittest.main()
