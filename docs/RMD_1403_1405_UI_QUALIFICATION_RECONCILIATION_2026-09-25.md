# RMD-1403 through RMD-1405 UI Qualification Reconciliation — 2026-09-25

This note records the evidence for the Android UI qualification work merged by PR #376. The canonical completion checklist remains `docs/OFFLINE_YT_PLAYER_REMEDIATION_TODO_2026-09-17.md`; this document exists to preserve detailed evidence before the canonical checklist is reconciled.

## Qualified implementation

PR #376 merged as exact master commit `6d21f1db5e5a5444793aeb1ddf7eb62a3ed100bd` and added deterministic screenshot/golden qualification plus compact-layout and accessibility instrumentation gates.

Implementation paths:

- `app/src/androidTest/java/com/ekkus/offlineytplayer/ui/ProductionComposeGoldenTest.kt`
- `app/src/androidTest/java/com/ekkus/offlineytplayer/ui/ProductionComposeLayoutAccessibilityTest.kt`
- `.github/workflows/android-smoke.yml`
- `app/src/main/java/com/ekkus/offlineytplayer/ui/AppShell.kt`

Exact-head qualification for PR #376 head `e76ec6491a32a4a6800422bc474e16b4f00cf1cc` passed:

- Push CI: `36218369711`
- Push Android smoke: `36218369756`
- Push Android FGS timeout: `36218369751`
- PR CI: `36218371977`
- PR Android smoke: `36218371983`
- PR Android FGS timeout: `36218371976`

Post-merge qualification for merge commit `6d21f1db5e5a5444793aeb1ddf7eb62a3ed100bd` passed:

- Master CI: `36219448765`
- Master Android smoke: `36219448785`
- Master Android FGS timeout: `36219448766`

The later current master `564b4adc3abccaf45a1914f69111961207da16ff` contains this work plus the RMD-500 partial reconciliation note merged by PR #378. That later master remains the baseline for subsequent TODO reconciliation.

## RMD-1403 — Deterministic screenshot/golden tests

`ProductionComposeGoldenTest` establishes actual image/golden infrastructure by rendering production Compose surfaces, capturing each root with `captureToImage()`, encoding PNG evidence, persisting a raster SHA-256 manifest, and failing when a captured raster is outside the bounded approved hash set.

Covered golden scenarios:

- Library empty: `golden_library_empty`
- Library populated: `golden_library_populated`
- Add invalid URL/error state: `golden_add_invalid`
- Add resolved source state: `golden_add_resolved`
- Download Setup/options: `golden_download_setup_options`
- Downloads active state: `golden_downloads_active`
- Downloads failure state: `golden_downloads_failure`
- Player: `golden_player`
- Settings hub: `golden_settings_hub`
- Smallest-supported portrait case: `golden_smallest_supported_portrait`
- Representative large-font cases: `golden_large_font_library` and `golden_large_font_settings`

The Android smoke workflow executes `ProductionComposeGoldenTest` explicitly, pulls `/sdcard/Download/offline-yt-player-goldens` into `app/build/reports/androidGoldens`, prints the golden raster manifest, and uploads bounded instrumentation/golden evidence artifacts. This makes unintended golden drift a normal Android smoke failure with reviewable PNG/hash artifacts.

## RMD-1404 — No-hidden-controls behavioral gate

`ProductionComposeLayoutAccessibilityTest` replaces policy-only acceptance with device-side Compose assertions against production UI surfaces.

Covered compact-layout behavior:

- App shell exposes all primary destinations by content description.
- Library keeps Search, Grid, and Add video controls inside the root bounds without horizontal overflow.
- Add and resolved Download Setup keep Paste, Analyze, Options, and Download reachable.
- Downloads keeps filters and row actions reachable.
- Settings categories remain reachable and preserve visual top-to-bottom traversal order.
- Player keeps Back, seek, play/pause, speed, subtitles, and audio controls reachable.
- Long Library content uses designed vertical collection scrolling and keeps the target item inside root bounds after scrolling.

The assertions use actual Compose semantics and root bounds rather than an all-boolean policy object, with bounded vertical scrolling only in the designed collection case.

## RMD-1405 — Accessibility qualification

`ProductionComposeLayoutAccessibilityTest` also qualifies the accessibility subset that can be automated reliably in Compose instrumentation.

Covered accessibility behavior:

- Navigation/actionable controls expose TalkBack-facing content descriptions.
- Settings traversal follows a logical visual order.
- Representative actionable controls satisfy the 48dp minimum touch-target expectation.
- Failed download state is expressed textually with state, percentage, size, speed, ETA, error text, and Retry action rather than color alone.
- Large text at `fontScale = 1.30f` keeps Library and Settings primary actions visible.

Manual TalkBack-only behavior that cannot be fully automated remains covered by the documented Compose semantics checks here and by final RMD-1800 closeout discipline rather than by a fake policy-only proof.

## RMD-1601 impact

The screenshot/golden lane is now part of `.github/workflows/android-smoke.yml`, so the RMD-1601 `Screenshot/golden tests` checkbox has implementation and CI evidence. The deterministic fixture E2E lane remains open under RMD-1500 and RMD-1601.

## Remaining boundaries

This reconciliation does not close RMD-1500 deterministic fixture E2E, RMD-1602 supply-chain/advisory checks, RMD-1603 CI evidence quality, documentation closeout, or any final RMD-1800 engineering definition-of-done items. It only supports reconciling RMD-1403, RMD-1404, RMD-1405, and the screenshot/golden subtask of RMD-1601 after the canonical TODO is updated and merged through exact-head CI.
