# RMD-1802 — Split A/V filesystem-identity review (2026-10-11)

## Exact-head qualification evidence

The exact `master` commit `8126b6b07865873af4302b158a63794bd7cfd7b0` passed the eight required workflows:

| Gate | Run | Conclusion |
| --- | ---: | --- |
| CI (Rust, JVM, UniFFI/package checks) | 38163946028 | success |
| Android smoke | 38163946035 | success |
| Android cold start | 38163946020 | success |
| Android FGS timeout | 38163945997 | success |
| Android real-network E2E | 38163945944 | success |
| Deterministic fixture E2E | 38163945953 | success |
| Supply chain | 38163946030 | success |
| CI evidence | 38163946040 | success |

This qualifies that exact source head, not later changes or the unfinished independent review. Do not close RMD-1802, RMD-1803, RMD-1805, or global checklist items based on this record alone.

## New independent test-proof finding

`app/src/androidTest/java/com/ekkus/offlineytplayer/downloads/Rmd1502SplitAvOfflineInstrumentedTest.kt` uses `isSameLocalFile` to compare a MediaSession file URI to the persisted local video. The canonical-file comparison is sound for aliases that resolve to the same file. Its fallback, however, accepts any `/data/` URI with the same package-relative string suffix, without proving the two files are the same filesystem object. A different file with a matching suffix could satisfy the test assertion. This is a **test-evidence weakness**, not evidence of an observed production file-selection bug.

Required correction: remove the suffix-only fallback. Keep the `file:` scheme requirement; compare canonical file identity and, if needed for Android's `/data/data` versus `/data/user/0` aliases, compare `android.system.Os.stat` device/inode identities. Reject paths that cannot be resolved/stat'ed. Run the API-29 cold-start lane plus the full exact-head matrix after the correction. Preserve the split A/V descriptor assertions and actual selected audio/video track checks.

## Execution status

An attempted exact-HEAD source edit to replace the suffix fallback with canonical-or-device/inode comparison was rejected by the tool safety gate before committing. No source mutation was accepted. The canonical remediation checklist remains unchanged and all final closeout boxes remain open. This finding must be resolved or explicitly adjudicated before claiming the independent code review complete.
