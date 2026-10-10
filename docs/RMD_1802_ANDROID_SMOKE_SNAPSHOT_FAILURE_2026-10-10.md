# RMD-1802 — Android smoke Compose snapshot failure (2026-10-10)

## Exact-head evidence

- SHA: `f07033c291ae284437b26834441e3cacd6baf745` on `master`.
- Failing Android smoke: https://github.com/ekkus93/offline-yt-player/actions/runs/38019697278; API-29 instrumentation job `114117733406`, test `Rmd1504ShareE2EInstrumentedTest.actionSendFlowsThroughAnalyzeSetupRealSchedulerWorkerLibraryAndBackStack`.
- Job log line 1101: `IllegalArgumentException: Detected multithreaded access to SnapshotStateObserver: previousThreadId=2), currentThread={id=323, name=DefaultDispatcher-worker-3}`. Stack: `SnapshotStateObserver.observeReads` → `LayoutNode.calculateSemanticsConfiguration` → `LayoutNode.invalidateSemantics` → `Applier.apply`.
- Failure artifact: https://github.com/ekkus93/offline-yt-player/actions/runs/38019697278/artifacts/11658172288 (expires 2026-10-13).
- Same-SHA passes: CI `38019697319`; FGS timeout `38019697252`; Supply chain `38019697291`; CI evidence `38019697304`; deterministic fixture `38019697365`; cold start `38019697261`; real-network E2E `38019697339`.
- Earlier successful RMD-1504 qualification at SHA `e89912f5e9b16eb83cdc7cd3651f04d77fb97a67`, smoke `37826114126`, does not waive the latest failure.

## Investigation / acceptance

`Rmd1504ShareE2EInstrumentedTest.kt` uses `createEmptyComposeRule` with an explicitly launched `ActivityScenario<MainActivity>`, drives real Compose semantics and the production scheduler/worker. The stack establishes cross-thread Compose semantics observation, but not whether the cause is an instrumentation scheduler race or production UI-thread violation. Inspect the full XML/stack and test timing; audit off-main state writers in `MainActivity.kt`, `AppShell.kt`, and `LibraryDownloads.kt`. `MainActivity` periodic state refresh uses `runOnUiThread` and `StateFlow`, while Activity-owned gateway/settings fields still use Compose `mutableStateOf` and the settings observer does not explicitly marshal to main. These are hypotheses, not confirmed root causes.

Fix the established cause with regression coverage; obtain a non-skipped passing Android smoke run on the exact new candidate including RMD-1504, Compose behavior, golden, and accessibility suites, and requalify other exact-head workflows. Keep RMD-1800/global checkboxes open until the complete matrix passes. Keep the external legal/service-policy release gate separate.

Both Ralph Bridge rerun calls were blocked by the execution safety layer on 2026-10-10; a dispatch request for profile `android-smoke` was rejected as invalid. No rerun was accepted.

## Exact-head recovery and remaining review

- Production settings observer fix: `00f37d1c49e87990fc4d52fe62c0d98900ca828d`.
- Smoke execution fix: `0488fa5eff0d4fea3c4651eab995a0cddbe02271` isolates `Rmd1802SettingsObserverInstrumentedTest` through `scripts/run_required_android_smoke.sh` so the required non-skipped test is actually executed.
- Exact-head API-29 smoke passed: https://github.com/ekkus93/offline-yt-player/actions/runs/38071487987 (job `114269626991`). Exact-head CI `38071487927`, FGS timeout `38071487896`, cold start `38071488055`, real-network E2E `38071488022`, deterministic fixture `38071487926`, supply chain `38071487969`, and CI evidence `38071488124` passed.
- The previous missing required-test result was an invocation/verification mismatch, now fixed. A passing smoke suite does not alone conclusively identify the root cause of the earlier Compose snapshot exception. Preserve full RMD-1802 code review and exact-head RMD-1803 closeout requirements.
