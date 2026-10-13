# RMD-1802 Add-Screen Policy-Only Evidence Correction — 2026-10-12

## Finding

The former `app/src/main/java/com/ekkus/offlineytplayer/ui/AddScreenPolicy.kt` used an all-boolean, hand-authored model for UI controls (`urlFieldVisible`, `pasteActionVisible`, `analyzeActionVisible`, `supportedSourceHintVisible`, `primaryScreenScrollable`). Its `primaryControlsFit()` merely combined those declarations; it did not inspect rendered Compose semantics, measured bounds, a real viewport, or Android device interaction. The companion `AddScreenPolicyTest.kt` searched `AppShell.kt` for literal source strings; those tests cannot by themselves prove operational Paste/Analyze/Setup actions or discover layout regressions. The current Add/Analyze flow does not use the all-boolean policy.

## Production-path evidence that remains

The actual Add/Analyze/Download Setup implementation is `app/src/main/java/com/ekkus/offlineytplayer/ui/AppShell.kt`, driven by `SourceAnalysisUseCase`, `AddWorkflowPolicy` and the real `AppSourceAnalysisGateway`/`AppDownloadControlGateway` boundaries. Behavioral evidence is in `ProductionComposeBehaviorTest.kt` (Paste, Analyze, resolved Setup, Options and concrete gateway scheduling), `ProductionComposeLayoutAccessibilityTest.kt` (reachable compact and large-text primary actions), `AddWorkflowPolicyTest.kt` (bounded clipboard and resolved-quality selection), and `SourceAnalysisUseCaseTest.kt` (production error/supersession semantics). The Android smoke workflow executes packaged behavioral/geometry/semantics instrumentation. The separate `ProductionComposeGoldenTest` executes 12 SHA-256-pinned rasters; `GoldenCoveragePolicy` is a 22-target inventory, not 22 executable goldens.

## Corrective change and qualification

Commit `5f154004c82f8a8a464d38a3f0a75a12b6d16c78` **deletes** the unused `AddScreenPolicy.kt` and the source-string-only `AddScreenPolicyTest.kt` directly on `master` (no new branch or PR). This deliberately does not alter any production Compose control callback.

The deletion must pass its own exact-head Rust/Android/JVM/UniFFI plus Android smoke, goldens and full matrix; its existence in version control is not sufficient acceptance. Complete RMD-1802 still requires an independent review of all Rust/Android paths and explicit searches for no-op callbacks, fabricated production data and dead capability flags. Closeout RMD-1803/1805 and the separate human YouTube/service/legal gate remain OPEN. Reconcile every newly discovered defect before checking any final boxes.
