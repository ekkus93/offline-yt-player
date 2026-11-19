"""Verify Kover JVM XML reports cannot fabricate Kotlin application coverage."""
import unittest

from scripts.summarize_kotlin_coverage import (
    CoverageError,
    parse_kover_xml,
    production_package,
    production_sourcefile,
    summary_markdown,
)


class KotlinJvmCoverageSummaryTest(unittest.TestCase):
    def test_sums_only_app_sources_and_excludes_generated_bindings(self):
        content = """<report name="app">
<package name="com/ekkus/offlineytplayer/downloads">
<sourcefile name="DownloadState.kt">
<line nr="1" mi="0" ci="2" mb="0" cb="1" />
<line nr="2" mi="3" ci="0" mb="1" cb="0" />
</sourcefile>
<sourcefile name="DownloadStateTest.kt"><line nr="1" mi="0" ci="5"/></sourcefile>
</package>
<package name="com/ekkus/offlineytplayer/core">
<sourcefile name="GeneratedBindings.kt"><line nr="1" mi="0" ci="100"/></sourcefile>
</package>
<package name="com/ekkus/offlineytplayer/ui">
<sourcefile name="UiState.kt"><line nr="2" mi="0" ci="1"/></sourcefile>
<sourcefile name="R.java"><line nr="1" mi="0" ci="1"/></sourcefile>
</package></report>"""
        result = parse_kover_xml(content)
        self.assertEqual((result.line_covered, result.line_total), (2, 3))
        self.assertEqual((result.branch_covered, result.branch_total), (1, 2))
        self.assertEqual(result.as_dict()["line_percentage"], 66.67)
        self.assertEqual(len(result.packages), 2)
        self.assertIn("2/3 (66.67%)", summary_markdown(result, "a" * 40, "0.9.11"))

    def test_root_package_included_generated_core_excluded(self):
        self.assertTrue(production_package("com/ekkus/offlineytplayer"))
        self.assertTrue(production_package("com/ekkus/offlineytplayer/coregateway"))
        self.assertFalse(production_package("com/ekkus/offlineytplayer/core"))
        self.assertFalse(production_package("com/ekkus/offlineytplayer/core/uniffi"))
        self.assertFalse(production_package("com/other"))
        self.assertTrue(production_sourcefile("MainActivity.kt"))
        self.assertFalse(production_sourcefile("MainActivityTest.kt"))
        self.assertFalse(production_sourcefile("BuildConfig.java"))

    def test_missing_report_and_empty_denominator_fail_closed(self):
        for content in (
            "",
            "<foo/>",
            "<report/>",
            '<report><package name="com/ekkus/offlineytplayer/core">'
            '<sourcefile name="Gen.kt"><line nr="1" mi="0" ci="10"/>'
            "</sourcefile></package></report>",
            "<report><package>",
        ):
            with self.subTest(content=content):
                with self.assertRaises(CoverageError):
                    parse_kover_xml(content)

    def test_unmeasured_branches_are_not_invented(self):
        result = parse_kover_xml(
            '<report><package name="com/ekkus/offlineytplayer/ui">'
            '<sourcefile name="State.kt"><line nr="1" mi="1" ci="0"/>'
            "</sourcefile></package></report>"
        )
        self.assertIsNone(result.as_dict()["branch_percentage"])
        self.assertIn("Branch coverage: **unmeasured**", summary_markdown(result, "", ""))

    def test_corrupt_source_metrics_are_rejected(self):
        for old, new in (('ci="0"', 'ci="-1"'), ('mi="1"', 'mi="-5"'), ('nr="1"', 'nr="invalid"')):
            raw = '<line nr="1" mi="1" ci="0" />'.replace(old, new)
            content = (
                '<report><package name="com/ekkus/offlineytplayer">'
                '<sourcefile name="MainActivity.kt">'
                + raw
                + '</sourcefile></package></report>'
            )
            with self.subTest(new=new):
                with self.assertRaises(CoverageError):
                    parse_kover_xml(content)


if __name__ == "__main__":
    unittest.main()
