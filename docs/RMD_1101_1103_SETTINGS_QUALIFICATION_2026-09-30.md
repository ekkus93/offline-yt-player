# RMD-1101 / RMD-1103 settings qualification — 2026-09-30

## Result

RMD-1101 and RMD-1103 are implementation-complete and qualified. RMD-1102 remains open because the persisted concurrency preference is not yet proven to constrain the Android runtime worker execution path.

## RMD-1101 durable settings evidence

`app/src/main/java/com/ekkus/offlineytplayer/settings/AppSettingsStore.kt` provides the documented `SharedPreferencesAppSettingsStore`, typed `AppSettingsSnapshot`, bounded typed mutations, schema/default handling, and observable updates. Production `MainActivity` opens the store before Compose bootstrap, observes it, supplies the current snapshot to production UI, and routes settings mutations back through the durable store.

`app/src/androidTest/java/com/ekkus/offlineytplayer/settings/AppSettingsStoreInstrumentedTest.kt` exercises the real Android SharedPreferences-backed implementation. It verifies defaults/schema behavior, observer delivery, typed update persistence across close/reopen, concurrency clamping, and invalid persisted enum fallback.

## RMD-1103 playback settings evidence

The same durable store persists `rememberPlaybackPosition` and `playbackSpeed`. Production `PortraitPlayerScreen` consumes both settings on the canonical MediaController path: it restores position only when resume behavior is enabled, applies the configured playback speed to the connected controller, persists bounded position updates while enabled, and writes speed changes back through `onUpdateSettings`.

This is runtime consumption rather than decorative settings state.

## Exact-head qualification

The instrumentation proof and playback reconciliation are both present on exact master `fe7aa296c1de33c3b253aa42effe29002324f66f`, which passed the complete configured six-workflow matrix:

- CI: `36668722456`
- Android smoke: `36668722418`
- Android FGS timeout: `36668722459`
- Supply chain: `36668722470`
- CI evidence: `36668722616`
- Deterministic E2E fixture: `36668722511`

All six runs completed successfully. The same implementation is retained by later exact master `a1890039b32a26e50fdfc7dfbda71ffc11d14a2c`, whose six configured workflows also completed successfully (`36681877063`, `36681877128`, `36681877100`, `36681876995`, `36681877046`, `36681877066`).

This evidence is sufficient to reconcile the RMD-1101 and RMD-1103 checkboxes in the canonical remediation TODO. It does not close RMD-1102.