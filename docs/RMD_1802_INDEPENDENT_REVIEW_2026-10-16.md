# RMD-1802 independent production-path review — 2026-10-16

**Reviewed head:** `027e2568dd56ffd2f04dbd38c2c279e5849cad36`  
**Qualified implementation candidate:** `a0913ee6c3173b4bba4cf295cf57d6740078fec3`  
**Scope:** current Android production source and UI callbacks, core gateway wiring, Rust source/download/worker/storage paths, and test evidence used by the remediation checklist.

## Review searches and results

- Searched Android production source for empty `onClick` and callback lambdas. No empty callbacks were found. The visible controls route through state mutations, gateways, navigation, or the canonical `MediaController`; controls unavailable until the controller exists are disabled.
- Searched production Kotlin and Rust for `todo!`, `unimplemented!`, `TODO`, `FIXME`, and equivalent empty function bodies. No unfinished production stubs were found. A URL test string containing the text `pad={}` is not a stub.
- Searched production UI for fabricated ready-empty collection states and fixture metadata. Production app defaults report repository unavailability; successful collection state is populated by the generated core gateway. Empty collections in optional model fields are defaults, and `FakeCoreGateway` is explicitly test infrastructure.
- Searched JVM tests for source-text assertions against production Kotlin. Remaining source-file reads target Android manifest/resource declarations; those tests are static configuration guards and are not cited as behavioral evidence. The only `readText().contains(...)` checks on runtime data verify downloaded subtitle contents.
- Searched production declarations for capability-looking `Has*`, `Uses*`, `Supports*`, and constant Boolean flags. No dead capability flags remain. The matched `WifiOnlyDownloads` and `RememberPlaybackPosition` Booleans are persisted defaults consumed by the settings store/runtime.
- Reviewed the golden scenario inventory. `GoldenCoveragePolicy.RequiredScenarios` is explicitly documented as a target inventory, is not used to register or qualify screenshot tests, and is not cited as screenshot evidence. The executable golden suite and exact raster digests are separately verified by the Android smoke workflow.

## Findings

No new release-blocking implementation issue was found in the reviewed paths. The source-text-test audit remains the authoritative mapping for removed checks, retained static guards, and behavioral replacements in `RMD_1802_SOURCE_TEXT_TEST_AUDIT_2026-10-15.md`.

The review does not convert known qualification limits into passing evidence. In particular, the timeout test exercises the real API-35 `Service.onTimeout` callback without a busy native transfer, so stop ordering while a transfer is active remains unverified. Live YouTube qualification and the human service-policy/legal release decision remain separate gates. The RMD-1803 exact-head audit records the candidate matrix and its scope.

## Reproduction checks

- `rg -n 'onClick\\s*=\\s*\\{\\s*\\}|on[A-Z][A-Za-z]+\\s*=\\s*\\{\\s*\\}|=\\s*\\{\\s*\\}' app/src/main/java`
- `rg -n 'todo!\\(|unimplemented!\\(|TODO\\(|FIXME' app/src/main/java core/src`
- `rg -n 'source\\.contains|readText\\(\\).*contains|File\\(.*\\.kt.*\\)' app/src/test`
- `rg -n 'Has[A-Z]|Uses[A-Z]|Supports[A-Z]|Available[A-Z]|Required[A-Z]' app/src/main/java core/src`
- `./gradlew --no-daemon lintDebug testDebugUnitTest --console=plain` — passed on the local worktree.
- `python3 -m unittest discover -s tests -p 'test_*.py'` — 27 tests passed.
- Focused API-36 instrumentation checks for settings-observer startup, scheduler fallback, queue dispatch, RMD-1500 Add setup, and RMD-1507 notification controls passed. The full connected suite on API 36 still reports API-29-only and host-controlled network cases as environment/lane failures; it is not claimed as a full pass.

