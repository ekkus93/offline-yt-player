# RMD-1802 — Remove detached Library/Share policy-only seams and fabricated setup preview (2026-10-13)

## Production-path review

The production Library screen in `ui/LibraryDownloads.kt` filters real `LibraryRowModel` records via `matchesLibraryQuery`, with `MainActivity` requesting/reloading the Rust-backed Library repository. `ui/LibraryPolicy.kt::LibraryScreenPolicy.visibleItems` / `emptyStateShowsAdd` operated only on separate `LibraryItemSummary` fixtures; its `LibraryScreenPolicyTest` verified only that disconnected helper. No production call was found in the Library, Add, main activity, player, core gateway or relevant UI paths inspected.

Similarly, `ShareToDownloadPolicy.initialRoute` and the `ShareToDownloadRoute` class in `ShareToDownload.kt` were only exercised by host `ShareToDownloadPolicyTest`, which asserted that a synthetic `DownloadSetup` route preserved its own `backStackDestination`. The actual Android ACTION_SEND path is `MainActivity.ShareInput.parse` → `OfflineYTPlayerApp(initialSharedUrl)` → Add/Analyze → production `SourceAnalysisUseCase`/UniFFI, with stateful Compose navigation. The detached host route was not evidence of a working Download Setup/Share flow.

Finally, `ui/DownloadSetupModels.kt::DownloadSetupRoute.previewFor` constructed a `DownloadSetupState` with the hard-coded title "Shared video", inferred quality, and `readyForDownload = true` merely from any syntactically valid HTTP(S) URL. The actual production Download Setup state is supplied only by successful source analysis; this unused helper was misleading fallback/fabricated-data code. Remove that entire object while retaining `DownloadSetupState` (used by production) and `SetupThumbnailPolicy` (used by the bounded production thumbnail renderer). No productive need for an unused `java.net.URI` import remains.

## Correction and non-equivalent proof

Removed exactly four detached source/test files: `ui/LibraryPolicy.kt`, `ui/LibraryScreenPolicyTest.kt`, `ShareToDownload.kt`, `ShareToDownloadPolicyTest.kt`. Removed only the unused fabricated `DownloadSetupRoute.previewFor` and its import from `ui/DownloadSetupModels.kt`, preserving actual source-analysis and Compose routing.

Meaningful qualification remains the packaged `ProductionComposeBehaviorTest` (real screen selection/filter/Add→Setup controls), `Rmd1504ShareE2EInstrumentedTest.actionSendFlowsThroughAnalyzeSetupRealSchedulerWorkerLibraryAndBackStack`, `Rmd1500AppPipelineFixtureTest`, Rust fixture/network workers, `ProductionComposeGoldenTest`, and Android layout/accessibility checks. Do **not** substitute the deleted host assertions for that runtime evidence.

## Evidence and limitations

Last fully qualified exact implementation before these deletions was `072b6d7443ee6c6ff4ac805b99184ae4bb1624ac`, with all eight workflows passed: CI `38459518573`, Android smoke `38459518608` attempt 2, FGS/UIDT `38459518734`, supply chain `38459519027`, CI evidence `38459518720`, deterministic fixture E2E `38459518739`, cold start `38459518966`, real-network E2E `38459518541`. These passes do not qualify later source removals or this audit; fresh exact-head CI required. This scoped finding does not complete repository-wide RMD-1802 review, RMD-1803 candidate freeze, RMD-1804 post-merge verification, RMD-1805 or the global proof rules. External human YouTube/service-policy/legal approval remains a separate and unapproved release gate.
