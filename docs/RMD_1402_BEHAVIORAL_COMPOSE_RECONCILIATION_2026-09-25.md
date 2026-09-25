# RMD-1402 Behavioral Compose Reconciliation — 2026-09-25

This note records the merged implementation and qualification evidence for RMD-1402, **Replace policy-only screen qualification with behavioral Compose tests**. It does not weaken the canonical TODO: `docs/OFFLINE_YT_PLAYER_REMEDIATION_TODO_2026-09-17.md` remains the source of truth for checkbox state.

## Covered RMD-1402 checklist items

- Library empty/populated behavior.
- Add input/paste/analyze behavior.
- Download Setup choices/actions.
- Downloads state/actions.
- Player controls.
- Settings persistence/interaction.
- Share navigation/back stack.

## Implementation evidence

All coverage is in `app/src/androidTest/java/com/ekkus/offlineytplayer/ui/ProductionComposeBehaviorTest.kt`.

### Library, downloads, and share/back-stack behavior

Merged production test coverage in PR #371 verifies:

- Library empty and populated UI behavior.
- Downloads action routing through the download-control gateway boundary.
- Shared input opening the Add flow and preserving navigation back to Library/Add.

Merged SHA: `bc724c5b216704095b73fdec7211d6e05fe9c3a0`.

Post-merge exact-head evidence on `bc724c5b216704095b73fdec7211d6e05fe9c3a0`:

- CI `36173880191` — success.
- Android smoke `36173880426` — success.
- Android FGS timeout `36173880427` — success.

### Add, paste, analyze, setup, options, and download action behavior

Merged production test coverage in PR #372 verifies:

- Paste reads bounded clipboard text into the Add URL field and reports status.
- Analyze calls the source-analysis gateway boundary and displays resolved fixture metadata.
- Download Setup displays real title/duration/quality/estimated-size state from the gateway result.
- Advanced options display actual available quality/subtitle/container choices.
- Download schedules through the download-control gateway boundary rather than a no-op callback.

Qualified PR head: `a603c9a52455d1a9c29b8789b20e9c42a19cdfa3`.

Exact-head PR/push evidence on `a603c9a52455d1a9c29b8789b20e9c42a19cdfa3`:

- PR CI `36176367044` — success.
- PR Android smoke `36176367043` — success.
- PR Android FGS timeout `36176367059` — success.
- Push CI `36176363105` — success.
- Push Android smoke `36176363235` — success.
- Push Android FGS timeout `36176363179` — success.

Merged SHA: `9f6b3b13a2c3743cb3a2c7ad0d6ff765f2bccae3`.

Post-merge exact-head evidence on `9f6b3b13a2c3743cb3a2c7ad0d6ff765f2bccae3`:

- CI `36178067854` — success.
- Android smoke `36178067800` — success.
- Android FGS timeout `36178067799` — success.

### Player controls and settings interaction behavior

Merged production test coverage in PR #373 verifies:

- Library play navigation enters the player screen from a completed library row with local video/audio asset paths.
- Player transport controls are rendered: back, seek backward, play/pause, seek forward, speed control, subtitle control, and audio control.
- Direct player rendering exposes subtitle and multi-audio track labels from `LocalPlaybackAsset` track metadata.
- Settings UI interactions mutate the runtime settings snapshot: default quality, concurrency, playback speed, appearance theme, and library layout.

Qualified PR head: `a3ebe03d8de0ab0a7f45611d468b9f626aac5dce`.

Exact-head PR/push evidence on `a3ebe03d8de0ab0a7f45611d468b9f626aac5dce`:

- PR CI `36179030902` — success.
- PR Android smoke `36179030511` — success.
- PR Android FGS timeout `36179030581` — success.
- Push CI `36178943950` — success.
- Push Android smoke `36178943930` — success.
- Push Android FGS timeout `36178943991` — success.

Merged SHA: `c6a1b80d033285c2bb4ceb7209b589467ab454bf`.

Post-merge exact-head evidence on `c6a1b80d033285c2bb4ceb7209b589467ab454bf`:

- CI `36181189252` — success.
- Android smoke `36181189235` — success.
- Android FGS timeout `36181189239` — success.

## Reconciliation conclusion

RMD-1402 is implemented and qualified by production Compose instrumentation tests, exact-head PR/push gates, and post-merge master gates. The canonical TODO can be updated to mark the seven RMD-1402 checklist items complete with this evidence.
