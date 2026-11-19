"""Host-side guards against fabricated or malformed Rust LCOV totals."""
import unittest

from scripts.summarize_rust_coverage import (
    CoverageError,
    core_relative_path,
    markdown_summary,
    parse_lcov,
)


class RustCoreCoverageReportTest(unittest.TestCase):
    def test_production_only_denominator_and_covered_lines(self):
        fixture = """TN:
SF:/home/runner/work/offline-yt-player/core/src/download.rs
DA:5,1
DA:6,0
DA:12,3
BRDA:12,0,0,2
BRDA:12,0,1,-
end_of_record
SF:core/src/worker.rs
DA:1,0
DA:2,1
end_of_record
SF:core/src/persistence/tests.rs
DA:1,1
end_of_record
SF:target/uniffi/generated.rs
DA:1,1
end_of_record
SF:core/src/rmd_1500_e2e_tests.rs
DA:1,1
end_of_record
"""
        result = parse_lcov(fixture)
        self.assertEqual((result.lines_covered, result.lines_total), (3, 5))
        self.assertEqual((result.branches_covered, result.branches_total), (1, 2))
        self.assertEqual(len(result.files), 2)
        self.assertEqual(result.to_dict()["line_percentage"], 60)
        self.assertEqual(result.to_dict()["branch_percentage"], 50)
        text = markdown_summary(result, "a" * 40, "0.9.1")
        self.assertIn("3/5 (60.00%)", text)
        self.assertNotIn("persistence/tests.rs", text)

    def test_repeated_source_record_merges_by_line_not_duplicate(self):
        result = parse_lcov(
            "SF:core/src/foo.rs\nDA:1,0\nDA:2,1\nend_of_record\n"
            "SF:core/src/foo.rs\nDA:1,4\nDA:2,1\nend_of_record\n"
        )
        self.assertEqual((result.lines_covered, result.lines_total), (2, 2))
        self.assertIsNone(result.branches_total)

    def test_function_denominator_excludes_test_modules_and_reports_unexecuted(self):
        fixture = (
            "SF:core/src/youtube_source.rs\\n"
            "DA:1,1\\n"
            "FN:1,resolver\\nFNDA:0,resolver\\n"
            "FN:4,parser\\nFNDA:4,parser\\n"
            "end_of_record\\n"
            "SF:core/src/youtube_source.rs\\nDA:1,1\\nFNDA:6,parser\\nend_of_record\\n"
            "SF:core/src/youtube_source/tests.rs\\nDA:1,1\\nFNDA:1,test_case\\nend_of_record\\n"
        )
        result = parse_lcov(fixture)
        self.assertEqual((result.functions_covered, result.functions_total), (1, 2))
        self.assertEqual(result.to_dict()["function_percentage"], 50)
        self.assertEqual(
            result.uncovered_functions,
            (("core/src/youtube_source.rs", "resolver"),),
        )
        self.assertIn("Unexecuted production functions", markdown_summary(result, "", "x"))

    def test_malformed_fnda_fails_closed_and_absent_functions_remain_unmeasured(self):
        fixture = "SF:core/src/download.rs\\nDA:1,1\\nend_of_record\\n"
        self.assertIsNone(parse_lcov(fixture).functions_total)
        for extra in ("FNDA:x,read", "FNDA:-1,read", "FNDA:1", "FNDA:1,"):
            with self.subTest(extra=extra):
                with self.assertRaises(CoverageError):
                    parse_lcov("SF:core/src/download.rs\\nDA:1,1\\n" +
                               extra + "\\nend_of_record\\n")

    def test_unmeasured_branch_is_not_100_percent(self):
        result = parse_lcov("SF:core/src/ffi.rs\nDA:3,1\nend_of_record\n")
        self.assertIsNone(result.to_dict()["branch_percentage"])
        self.assertIn("unmeasured", markdown_summary(result, "", "x"))

    def test_generated_paths_and_test_modules_are_filtered(self):
        for path in (
            "core/src/foo/tests/bar.rs",
            "core/src/foo_tests.rs",
            "core/src/foo-tests.rs",
            "core/src/tests.rs",
            "core/src/../secret.rs",
            "/external/tool/src/x.rs",
        ):
            with self.subTest(path=path):
                self.assertIsNone(core_relative_path(path))
        self.assertEqual(
            core_relative_path(r"C:\source\repo\core\src\resume.rs"),
            "core/src/resume.rs",
        )

    def test_missing_denominator_fails_closed(self):
        for content in (
            "",
            "TN:\nend_of_record\n",
            "SF:core/src/foo/tests/bar.rs\nDA:1,1\nend_of_record\n",
            "SF:generated/bindings.rs\nDA:1,1\nend_of_record\n",
        ):
            with self.subTest(content=content):
                with self.assertRaises(CoverageError):
                    parse_lcov(content)

    def test_truncated_and_invalid_lcov_fails_closed(self):
        for content in (
            "SF:core/src/a.rs\nDA:1,3\n",
            "SF:core/src/a.rs\nDA:-1,2\nend_of_record\n",
            "SF:core/src/a.rs\nDA:1,-1\nend_of_record\n",
            "SF:core/src/a.rs\nDA:foo,2\nend_of_record\n",
            "SF:core/src/a.rs\nDA:2,1\nBRDA:2,1,3,-5\nend_of_record\n",
        ):
            with self.subTest(content=content):
                with self.assertRaises(CoverageError):
                    parse_lcov(content)


if __name__ == "__main__":
    unittest.main()
