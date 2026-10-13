# RMD-1803 — Exact-Head Matrix and Golden Coverage Evidence (2026-10-12)

## Status and scope

This is an **interim, retrospective exact-head evidence audit**, not RMD-1800 engineering closeout. At inspected `master` SHA `03ae38b441cbf14108473efec85edb09b3decdd6`, all eight standard GitHub Actions workflows completed successfully. Subsequent commits must be tested on their own exact heads. The independent RMD-1802 production audit, final RMD-1803 candidate freeze, RMD-1804 merge/post-merge reconciliation, RMD-1805 definition of done, and external YouTube/service-policy/legal approval are **not** completed or approved by this report.

Normative checklists: `docs/OFFLINE_YT_PLAYER_REMEDIATION_TODO_2026-09-17.md` and `docs/ANDROID_QUALIFICATION_ACCELERATION_PLAN_2026-09-22.md`.

## Passing CI for inspected exact implementation SHA

| Workflow | Exact-SHA successful run |
| --- | --- |
| CI — Rust fmt, clippy, tests, Android lint/JVM/build, native/UniFFI packaging | [38316280118](https://github.com/ekkus93/offline-yt-player/actions/runs/38316280118) |
| Android smoke — packaged instrumentation, Compose behavior, goldens, layout/accessibility | [38316280138](https://github.com/ekkus93/offline-yt-player/actions/runs/38316280138) |
| Android FGS timeout — API-35 foreground-service / UIDT qualification | [38316280232](https://github.com/ekkus93/offline-yt-player/actions/runs/38316280232) |
| Supply chain — advisory/dependency/notice evidence | [38316280206](https://github.com/ekkus93/offline-yt-player/actions/runs/38316280206) |
| CI evidence | [38316280207](https://github.com/ekkus93/offline-yt-player/actions/runs/38316280207) |
| Deterministic E2E fixture | [38316280103](https://github.com/ekkus93/offline-yt-player/actions/runs/38316280103) |
| Android cold start | [38316280182](https://github.com/ekkus93/offline-yt-player/actions/runs/38316280182) |
| Android real-network E2E | [38316280067](https://github.com/ekkus93/offline-yt-player/actions/runs/38316280067) |

Ralph Bridge `get_ci_outcome` returned `state=passed`, `terminal=true` for this exact SHA with these eight successful runs.

### Historical intermittent Android smoke observation

The prior implementation `d388c32df7b2fdc7bb2906e8de551d56bcc55dcc` initially failed Android smoke [38310880129](https://github.com/ekkus93/offline-yt-player/actions/runs/38310880129) in `Rmd1504ShareE2EInstrumentedTest.actionSendFlowsThroughAnalyzeSetupRealSchedulerWorkerLibraryAndBackStack`: the fixture server was not contacted before its socket accept timeout. A same-run **attempt 2** rerun passed. This does **not** establish the cause of the intermittent failure. No success from an older SHA is substituted for the qualifying run on `03ae38b441cbf14108473efec85edb09b3decdd6`.

## Executable screenshot evidence versus declarative target inventory

The actual packaged `ProductionComposeGoldenTest.kt` has **12** distinct `@Test fun golden_...` bitmap raster assertions, each matched against pinned SHA-256 variant sets in `expectedHashes`:

1. `golden_library_empty`
2. `golden_library_populated`
3. `golden_add_invalid`
4. `golden_add_resolved`
5. `golden_download_setup_options`
6. `golden_downloads_active`
7. `golden_downloads_failure`
8. `golden_player`
9. `golden_settings_hub`
10. `golden_smallest_supported_portrait`
11. `golden_large_font_library`
12. `golden_large_font_settings`

The `.github/workflows/android-smoke.yml` lane asserts these 12 exact methods executed without skipping, using `scripts/assert_android_smoke_execution.py`. The 12 executed methods are meaningful reviewed, pinned raster tests.

By contrast, `GoldenCoveragePolicy.kt` enumerates **11 target surfaces × 2 named profiles = 22 intended scenarios**. These 22 objects are not 22 executable golden captures. The surface inventory includes `SettingsDownloads`, `SettingsPlayback`, `SettingsStorage`, `SettingsAppearance`, and `SettingsAbout`, none of which has a dedicated named `ProductionComposeGoldenTest` raster assertion. The remaining six surfaces have one or more state/profile fixtures but **not** a guaranteed pair of explicit bitmap captures for `CompactPortrait` and `LargePortrait`. In particular, `golden_smallest_supported_portrait` names a compact fixture but calls the same `setGoldenContent` as other tests without an explicit device-viewport override; the method name alone must not be cited as proof of a reduced physical viewport. `golden_large_font_library` and `golden_large_font_settings` set fontScale=1.30, not a universal large-portrait profile.

**Evidence classification:** The canonical RMD-1403 checklist requires specific screen-state captures, one smallest-supported portrait case, and representative large-font cases, which should be judged against those specific real tests and separately exercised device/layout validation—not inflated to claim the 22-scenario policy inventory is complete. This audit enumerates the difference; it does not claim missing 22-way bitmap coverage is qualified. If 22 executable target combinations are adopted as a closeout requirement, add/pin/run the actual tests and record exact-head results before marking them.

## Remaining closeout requirements

At inspected `master`, the canonical detailed TODO has 53 unchecked boxes: 8 global proof/approval controls and 45 in RMD-1800 final reconciliation, independent review, frozen exact-head closeout, post-merge verification and engineering definition of done. All implementation milestone subtasks preceding RMD-1800 are checked, but their earlier evidence is not automatically a completed new independent review.

- Continue Rust/Android production-path inspection for correctness, path/ownership safety, no-op callbacks, fabricated fallback data, and declarative-only evidence.
- Reconcile any new findings in the canonical TODO or a linked finding before affected closeout.
- Freeze the final candidate only after review findings are resolved; rerun the entire required matrix on its **new exact SHA** and record all run IDs.
- Preserve the detailed checklist and leave external service/legal release approval distinct from engineering completion.

No completion checkbox is changed by this audit.
