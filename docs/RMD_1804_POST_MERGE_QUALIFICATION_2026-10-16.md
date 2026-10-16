# RMD-1804 Post-Merge Qualification — 2026-10-16

## Qualified implementation and merges

Implementation candidate `a0913ee6c3173b4bba4cf295cf57d6740078fec3` passed the complete required matrix recorded in [`RMD_1803_EXACT_HEAD_CLOSEOUT_AUDIT_2026-10-16.md`](RMD_1803_EXACT_HEAD_CLOSEOUT_AUDIT_2026-10-16.md). Independent remediation-review and evidence updates merged through PR #392 as merge commit `305c823dd8d14055cf87337fc954f45f21d4f0f2`.

The first post-merge API-29 smoke job on `305c823` was cancelled at its 45-minute job timeout after all required instrumentation classes and all 12 golden raster tests had executed, but before the final script checks returned (run `38935271516`). The API-29 smoke ceiling was raised to 60 minutes in PR #393. That workflow-only change passed the full exact-head PR matrix on `5aee270a2a2b028bda2a3b4315b9c167bdd612b8`, including its manually dispatched API-29 real-network run `38945684361`, then merged as `cd624c4c411c476157fbab42a0cdf7c390c6657a`.

## Final master matrix

All required workflows passed on exact master SHA `cd624c4c411c476157fbab42a0cdf7c390c6657a`:

| Qualification | Run | Result |
| --- | ---: | --- |
| CI: Rust, Android lint/JVM/build, UniFFI and ABI, governance | [38947580606](https://github.com/ekkus93/offline-yt-player/actions/runs/38947580606) | Passed |
| CI evidence manifest | [38947580602](https://github.com/ekkus93/offline-yt-player/actions/runs/38947580602) | Passed |
| Deterministic fixture E2E | [38947580644](https://github.com/ekkus93/offline-yt-player/actions/runs/38947580644) | Passed |
| Supply-chain/advisory/license review | [38947580591](https://github.com/ekkus93/offline-yt-player/actions/runs/38947580591) | Passed |
| API-29 cold-start/process-death | [38947580637](https://github.com/ekkus93/offline-yt-player/actions/runs/38947580637) | Passed |
| API-35 foreground-service timeout and UIDT | [38947580588](https://github.com/ekkus93/offline-yt-player/actions/runs/38947580588) | Passed |
| API-29 instrumentation, accessibility/layout, and 12 golden rasters | [38947580592](https://github.com/ekkus93/offline-yt-player/actions/runs/38947580592) | Passed on rerun; 60-minute timeout |
| API-29 OS network-loss and metered-Wi-Fi transitions | [38947580607](https://github.com/ekkus93/offline-yt-player/actions/runs/38947580607) | Passed |

The first attempt of the final master smoke run could not resolve multiple Maven artifacts; rerunning the same workflow on the same SHA passed. This was a dependency-repository failure before instrumentation began, not a test assertion failure. The earlier 45-minute cancellation and the timeout repair are both retained above rather than presented as passing attempts.

## Exact-head smoke-timeout follow-up matrix

PR #393 head `5aee270a2a2b028bda2a3b4315b9c167bdd612b8` passed the required workflow matrix: CI `38941213729`, CI evidence `38941213875`, deterministic fixture E2E `38941213716`, supply chain `38941213710`, API-29 cold start `38941213876`, API-35 FGS timeout/UIDT `38941204916` (successful rerun after an initial transient Maven resolution failure), and API-29 smoke `38941214225`. The exact-head manual API-29 real-network E2E `38945684361` passed. The push-triggered duplicates also passed, including the API-29 smoke `38941204877`.

## Scope that remains open

This record qualifies the implementation, Android platform behavior, deterministic provider fixtures, and exact-head CI. It does not claim that the opt-in live YouTube source test has run or that the human service-policy/legal release gate is approved. The live test is blocked pending a user-authorized public video ID and confirmation that the test is permitted; see RMD-304 and [`YOUTUBE_LIVE_QUALIFICATION.md`](YOUTUBE_LIVE_QUALIFICATION.md). The human release gate remains separately open under [`YOUTUBE_POLICY_RELEASE_GATE.md`](YOUTUBE_POLICY_RELEASE_GATE.md).
