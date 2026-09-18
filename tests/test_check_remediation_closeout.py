import importlib.util
import tempfile
import unittest
from pathlib import Path


SCRIPT = (
    Path(__file__).resolve().parents[1]
    / "scripts"
    / "check_remediation_closeout.py"
)
SPEC = importlib.util.spec_from_file_location("check_remediation_closeout", SCRIPT)
assert SPEC is not None and SPEC.loader is not None
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class CloseoutGuardTest(unittest.TestCase):
    def test_reports_unchecked_items_with_line_numbers(self) -> None:
        text = "# TODO\n- [x] done\n- [ ] first remaining\n  - [ ] nested remaining\n"
        self.assertEqual(
            MODULE.unchecked_items(text),
            [(3, "first remaining"), (4, "nested remaining")],
        )

    def test_ignores_checked_and_non_checklist_text(self) -> None:
        text = "- [x] done\nplain text\n- [X] also done\n"
        self.assertEqual(MODULE.unchecked_items(text), [])

    def test_parser_does_not_mutate_source_file(self) -> None:
        original = "- [ ] remains unresolved\n"
        with tempfile.TemporaryDirectory() as temp_dir:
            path = Path(temp_dir) / "todo.md"
            path.write_text(original, encoding="utf-8")
            self.assertEqual(MODULE.unchecked_items(path.read_text()), [(1, "remains unresolved")])
            self.assertEqual(path.read_text(encoding="utf-8"), original)


if __name__ == "__main__":
    unittest.main()
