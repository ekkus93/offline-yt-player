"""Guard all named Compose PNG/raster hash evidence pairs."""
import contextlib
import io
import tempfile
import unittest
from pathlib import Path
from scripts.verify_android_golden_exports import NAMES, PNG_SIGNATURE, verify


class GoldenEvidenceVerifierTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.directory = Path(self.temp.name)
        self.addCleanup(self.temp.cleanup)
        for name in NAMES:
            (self.directory / f"{name}.png").write_bytes(PNG_SIGNATURE + b"sample-image")
            (self.directory / f"{name}.sha256").write_text(f"{name}={'a'*64}\n")

    def run_verifier(self):
        with contextlib.redirect_stderr(io.StringIO()):
            return verify(self.directory)

    def test_complete_bundle_passes_and_writes_manifest(self):
        self.assertEqual(0, self.run_verifier())
        content = (self.directory / "golden-raster-sha256.txt").read_text()
        self.assertEqual(len(NAMES), len(content.splitlines()))

    def test_media_store_text_mime_extension_passes(self):
        for name in NAMES:
            (self.directory / f"{name}.sha256").rename(
                self.directory / f"{name}.sha256.txt"
            )
        self.assertEqual(0, self.run_verifier())
        content = (self.directory / "golden-raster-sha256.txt").read_text()
        self.assertEqual(len(NAMES), len(content.splitlines()))

    def test_missing_png_fails(self):
        (self.directory / f"{NAMES[0]}.png").unlink()
        self.assertEqual(1, self.run_verifier())

    def test_missing_sidecar_fails(self):
        (self.directory / f"{NAMES[0]}.sha256").unlink()
        self.assertEqual(1, self.run_verifier())

    def test_malformed_sidecar_fails(self):
        (self.directory / f"{NAMES[0]}.sha256").write_text("wrong=" + "a" * 64)
        self.assertEqual(1, self.run_verifier())

    def test_truncated_png_fails(self):
        (self.directory / f"{NAMES[0]}.png").write_bytes(b"not a PNG")
        self.assertEqual(1, self.run_verifier())
