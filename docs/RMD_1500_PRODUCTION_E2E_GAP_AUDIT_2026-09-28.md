# RMD-1500 Production E2E Gap Audit — 2026-09-28

Canonical checklist: `docs/OFFLINE_YT_PLAYER_REMEDIATION_TODO_2026-09-17.md`.
Required execution guidance: `docs/ANDROID_QUALIFICATION_ACCELERATION_PLAN_2026-09-22.md`.
Audited master SHA: `a1983e2501b0f6342a1fb552f7c556cc9c59dd91`.

This note records current-master RMD-1500 evidence gaps. It is intentionally not a closeout note and does not mark any RMD-1500 checkbox complete.

## Current evidence

The audited master has passed the existing exact-head fast/smoke/timeout/supply-chain/evidence lanes inherited from `cdd897f5c6e9848db67942059aebe56e7d553f69`:

- CI run `36381587023`
- Android smoke run `36381587101`
- Android FGS timeout run `36381587004`
- Supply chain run `36381586906`
- CI evidence run `36381587009`

Those runs prove the existing CI, Android smoke, foreground-service timeout, supply-chain, and evidence lanes. They do not close RMD-1500 because the canonical checklist requires deterministic runtime E2E proof of the production app pipeline.

## Stale E2E branches inspected

`ralph/oyp-1901-e2e-fixture` adds only a deterministic fixture policy object, a policy test, and a short audit document. Its flow list mentions resolve, quality selection, download, restart, resume, verification, offline transition, and local playback, but it does not drive the production app pipeline, schedule through the real runtime abstraction, execute the core worker, cold-start the app offline, or prove MediaSession-owned local playback. It is therefore not acceptable RMD-1501 evidence.

`ralph/oyp-1902-share-e2e` similarly adds only a Share E2E policy object, a policy test, and a short audit document. It does not send an Android `ACTION_SEND` intent through the production Activity and then drive the real analysis, setup, scheduling, download, and Library observation path. It is therefore not acceptable RMD-1504 evidence.

## Required RMD-1500 runtime proof

The acceleration plan requires deterministic fixture E2E to exercise the same production sequence:

`Add/Share → Analyze → Download Setup → Scheduler → Core worker → Library → Playback`

The next implementation slice should add a deterministic Android runtime E2E harness that uses the existing production Compose/app-owned gateway seams while backing external providers with deterministic fixture implementations. The test should prove, at minimum:

1. Clean app state.
2. Fixture URL entry through the production Add or Share surface.
3. Analysis through the production source-analysis gateway boundary.
4. Download Setup rendering of resolved title, duration, source identity, curated quality/options, size when known, and thumbnail/subtitle metadata when applicable.
5. Download scheduling through the production download-control/scheduler abstraction.
6. Core worker transfer of deterministic fixture assets and durable Library promotion.
7. Library observation of the completed item.
8. Cold-start with network unavailable or intentionally unused.
9. Playback through the canonical local playback path without any network request for completed media.

RMD-1502 through RMD-1507 need equivalent runtime evidence for split A/V, subtitles, Share ingress, storage failure, connectivity pause/resume, and notification controls, or a precise canonical TODO note documenting an infrastructure limitation for the affected subtask.

## Canonical TODO reconciliation intent

Do not mark any RMD-1500 checkbox complete from this audit. The stale policy-only branches should not be merged as E2E proof. RMD-1500 should stay open until deterministic runtime E2E tests exist, pass on an exact candidate SHA, and are reconciled with exact implementation paths and run IDs.
