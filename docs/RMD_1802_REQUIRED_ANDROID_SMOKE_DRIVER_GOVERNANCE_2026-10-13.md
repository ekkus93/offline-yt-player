# RMD-1802 — Required Android smoke driver regression coverage (2026-10-13)

## Independent review finding

The production Android smoke lane uses `scripts/run_required_android_smoke.sh` to run seven mandatory instrumentation classes in separate invocations, with exact-method execution checks for ACTION_SEND and settings observation. Its existing emulator-free regression in `tests/test_run_required_android_smoke.py` was stale: it asserted three invocations and generated mock XML methods named `runs` even for the two exact-method gates. The normal CI governance job ran `tests/test_assert_android_smoke_execution.py` but **did not execute the smoke driver regression**. Consequently, that driver contract was not protected by fast CI, and a future driver regression could silently escape host qualification.

## Correction and qualification

- Correct the fake Gradle XML to use the exact mandatory ACTION_SEND and settings-observer method names and assert seven distinct class invocations and seven retained XML reports.
- Keep the missing-class fail-closed regression; add a missing-required-method regression that fails even when its class has an executed but wrong method.
- Run `python3 -m unittest tests/test_run_required_android_smoke.py` in the fast `.github/workflows/ci.yml` remediation governance job. These tests have no Android SDK, emulator, or network requirement, so they run before costly Android qualification.
- Historical Android smoke `38448788233` on `46ffafe6a25dfaf971b152df23b546c8a65ea75d` failed `Rmd1802DownloadsControlThreadingInstrumentedTest.visibleDownloadsPauseInvokesControlOffMainAndReportsDurableUpdate` with an Android Compose `SlotTable` exception; do not assume this driver correction fixes that distinct intermittent Compose failure. Later implementation `8e90e545f64d78dc8baec9c5707e79eddbb04dc3` passed all eight CI workflows.

This change is an RMD-1802 quality finding, **not** completion of RMD-1802, final RMD-1803 candidate freeze, RMD-1805, or external YouTube/service-policy/legal approval. Record the new commit's exact-head CI and Android results before reconciling checkboxes.
