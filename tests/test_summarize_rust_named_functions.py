"""Source-mapped Rust LCOV function declarations must not fabricate coverage."""
import tempfile
import unittest
from pathlib import Path

from scripts.summarize_rust_named_functions import (
    markdown_summary, report_named_functions,
)


class RustNamedSourceFunctionReportTest(unittest.TestCase):
    def test_matches_real_source_declarations_and_excludes_inline_tests(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            file = root / "core/src/worker.rs"
            file.parent.mkdir(parents=True)
            file.write_text(
                "pub fn start() {}\n"
                "pub(crate) fn stop() {}\n"
                "#[cfg(test)]\n"
                "mod tests {\n"
                "    fn only_test() {}\n"
                "}\n",
                encoding="utf-8",
            )
            result = report_named_functions(
                "SF:core/src/worker.rs\n"
                "FN:1,_RNv15work5start\nFNDA:0,_RNv15work5start\n"
                "FN:2,_RNv15work4stop\nFNDA:9,_RNv15work4stop\n"
                "FN:5,_RNv4test9only_test\nFNDA:100,_RNv4test9only_test\n"
                "FN:1,_RNCNv15work5start0B2_\nFNDA:5,_RNCNv15work5start0B2_\n"
                "end_of_record\n",
                root,
            )
            self.assertEqual(
                [(m.name, m.hits) for m in result.matched],
                [("start", 0), ("stop", 9)],
            )
            self.assertEqual(result.raw_symbols, 4)
            self.assertEqual(result.excluded_or_unmatched, 2)
            self.assertIn("partial", markdown_summary(result, "some-sha"))
            self.assertIn("unmeasured", markdown_summary(result, "some-sha"))
            self.assertEqual(result.to_dict()["matched_declarations"], 2)

    def test_no_locations_or_source_stays_unmeasured_not_100_percent(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            result = report_named_functions(
                "SF:core/src/does_not_exist.rs\n"
                "FNDA:2,_RNv17does_not_exist3run\nend_of_record\n",
                root,
            )
            self.assertEqual(result.matched, ())
            self.assertEqual(result.raw_symbols, 1)
            self.assertEqual(
                result.unreadable_files, ("core/src/does_not_exist.rs",)
            )
            self.assertNotIn("100%", markdown_summary(result, "sha"))

    def test_deduplicates_monomorphized_function_declaration(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            file = root / "core/src/worker.rs"
            file.parent.mkdir(parents=True)
            file.write_text("pub fn transfer<T>() {}\n", encoding="utf-8")
            result = report_named_functions(
                "SF:core/src/worker.rs\n"
                "FN:1,_RNvF3abc8transfer\nFNDA:0,_RNvF3abc8transfer\n"
                "FN:1,_RNvF4def8transfer\nFNDA:10,_RNvF4def8transfer\n"
                "end_of_record\n",
                root,
            )
            self.assertEqual(len(result.matched), 1)
            self.assertEqual(result.matched[0].hits, 10)

    def test_test_only_files_are_excluded_and_do_not_inflate_denominator(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for file in ("core/src/foo/tests.rs", "core/src/rmd_777_tests.rs"):
                path = root / file
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text("fn test() {}\n", encoding="utf-8")
            report = report_named_functions(
                "SF:core/src/foo/tests.rs\nFN:1,test\nFNDA:10,test\nend_of_record\n"
                "SF:core/src/rmd_777_tests.rs\nFN:1,test\nFNDA:10,test\nend_of_record\n",
                root,
            )
            self.assertEqual(report.raw_symbols, 0)
            self.assertEqual(report.matched, ())


if __name__ == "__main__":
    unittest.main()
