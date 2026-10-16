# RMD-1803 Exact-Head Closeout Audit — 2026-10-16

## Qualified implementation candidate

Implementation candidate: `68757edb1e017b23698af76b6f36b664d94e317a`.

All required workflows below completed successfully against that exact commit:

| Qualification | Run | Result |
| --- | ---: | --- |
| CI: Rust, Android JVM/lint/build, ABI/UniFFI, remediation governance | [38891921967](https://github.com/ekkus93/offline-yt-player/actions/runs/38891921967) | Passed |
| CI evidence | [38891922027](https://github.com/ekkus93/offline-yt-player/actions/runs/38891922027) | Passed |
| Deterministic E2E fixture | [38891921921](https://github.com/ekkus93/offline-yt-player/actions/runs/38891921921) | Passed |
| Supply chain | [38891921913](https://github.com/ekkus93/offline-yt-player/actions/runs/38891921913) | Passed |
| Android cold start, API 29 | [38891921917](https://github.com/ekkus93/offline-yt-player/actions/runs/38891921917) | Passed |
| Android foreground-service timeout and UIDT, API 35 | [38891921923](https://github.com/ekkus93/offline-yt-player/actions/runs/38891921923) | Passed |
| Android instrumentation smoke, API 29 | [38891921944](https://github.com/ekkus93/offline-yt-player/actions/runs/38891921944) | Passed |
| Android OS network transitions, API 29 | [38892844599](https://github.com/ekkus93/offline-yt-player/actions/runs/38892844599) | Passed |

The smoke workflow verified each required isolated instrumentation invocation, all 12 pinned screenshot scenarios, and `ANDROID_GOLDEN_EVIDENCE_OK`. The Share E2E method passed and asserted scheduler success, a real fixture transfer request, durable Library completion, and Share back-stack behavior. The OS-network run emitted `WIFI_ONLY_METERED_QUALIFIED` after metered Wi-Fi paused work and restored unmetered Wi-Fi resumed it.

## Remaining gates

The exact documentation head `a0913ee6c3173b4bba4cf295cf57d6740078fec3` has now passed the complete required matrix below. This closes the RMD-1803 qualification subtasks for that candidate; it does not close RMD-1801, RMD-1802, RMD-1804, or RMD-1805. The candidate still needs an independent Rust/Android production-path review, and the detailed TODO reconciliation remains open. PR #392 is still a draft and has not been merged. The service-policy/legal release gate remains separate and unapproved.

## Final documentation-head matrix

Candidate SHA: `a0913ee6c3173b4bba4cf295cf57d6740078fec3`.

Every required workflow completed successfully against that exact SHA:

| Qualification | Run | Result |
| --- | ---: | --- |
| CI: Rust fmt/clippy/tests, Android lint/JVM/build, UniFFI/ABI, governance | [38895603501](https://github.com/ekkus93/offline-yt-player/actions/runs/38895603501) | Passed |
| CI evidence | [38895603546](https://github.com/ekkus93/offline-yt-player/actions/runs/38895603546) | Passed |
| Deterministic fixture E2E | [38895603565](https://github.com/ekkus93/offline-yt-player/actions/runs/38895603565) | Passed |
| Supply chain/advisory/license review | [38895603483](https://github.com/ekkus93/offline-yt-player/actions/runs/38895603483) | Passed |
| Android cold start/process-death, API 29 | [38895603520](https://github.com/ekkus93/offline-yt-player/actions/runs/38895603520) | Passed |
| Android FGS timeout callback and UIDT, API 35 | [38895603528](https://github.com/ekkus93/offline-yt-player/actions/runs/38895603528) | Passed |
| Android smoke, required instrumentation, accessibility/layout, 12 golden rasters and digest exports, API 29 | [38895603522](https://github.com/ekkus93/offline-yt-player/actions/runs/38895603522) | Passed; emitted `ANDROID_GOLDEN_EVIDENCE_OK: 12 PNG/raster-digest pairs` |
| Android real-network OS connectivity transitions, API 29 | [38895608605](https://github.com/ekkus93/offline-yt-player/actions/runs/38895608605) | Passed; emitted `WIFI_ONLY_METERED_QUALIFIED` |

The CI run confirms Rust formatting, clippy, all-feature tests, Android lint/JVM/build, generated Kotlin binding consistency, APK native-library packaging for both supported ABIs, and governance checks. The smoke workflow executed the required Android instrumentation methods and verified golden evidence exports. The real-network workflow qualified airplane-mode pause/resume and metered-Wi-Fi pause/unmetered-Wi-Fi resume. This matrix qualifies only the exact candidate SHA shown above; the subsequent audit-only edit does not change production implementation.

The final candidate matrix above qualifies exact implementation SHA `a0913ee6c3173b4bba4cf295cf57d6740078fec3`; the detailed TODO records those results. Subsequent closeout edits and the smoke-timeout adjustment are documentation/workflow-only and do not change the qualified production implementation. Current reconciliation and remaining status are recorded in the detailed TODO; the live YouTube source qualification and separate human service-policy/legal release gate remain open.

## Smoke timeout workflow follow-up

PR #393 changed only `.github/workflows/android-smoke.yml`, increasing the software-emulator API-29 smoke job timeout from 45 to 60 minutes after the first post-merge run reached the 45-minute ceiling following its 12 golden executions. The exact follow-up head `5aee270a2a2b028bda2a3b4315b9c167bdd612b8` passed CI `38941213729`, CI evidence `38941213875`, deterministic fixture E2E `38941213716`, Supply chain `38941213710`, API-29 cold start `38941213876`, API-35 FGS timeout/UIDT `38941204916` (successful rerun after a transient dependency download error), and API-29 smoke `38941214225`. Its manually dispatched API-29 real-network qualification `38945684361` also passed. The push-triggered duplicate runs passed as well, including smoke `38941204877`.

The timeout follow-up merged as master `cd624c4c411c476157fbab42a0cdf7c390c6657a`. Its final master matrix, including a successful rerun after a transient Maven download failure, is recorded in [`RMD_1804_POST_MERGE_QUALIFICATION_2026-10-16.md`](RMD_1804_POST_MERGE_QUALIFICATION_2026-10-16.md). The timeout-only change does not alter the qualified production implementation. The opt-in live YouTube source test remains unrun and is not implied by deterministic fixture or CI success.
