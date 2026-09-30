# RMD-1100 Durable Settings Canonical Reconciliation — 2026-09-30

## Purpose

This note records the exact-head qualification basis for reconciling RMD-1101 through RMD-1106 in `docs/OFFLINE_YT_PLAYER_REMEDIATION_TODO_2026-09-17.md`. The canonical TODO remains the completion authority; this note does not replace it.

## Qualified exact head

Exact master `ad2474d169508f085cfbc5e7d147fcbb8d31ef46` passed the complete configured six-workflow matrix:

- CI `36705782171`
- Android smoke `36705782260`
- Android FGS timeout / API-35 UIDT qualification `36705782190`
- Supply chain `36705782245`
- CI evidence `36705782364`
- Deterministic E2E fixture `36705782156`

All six runs completed successfully on that exact SHA.

## RMD-1101 — settings persistence

Ready for canonical reconciliation. Production uses the typed `AppSettingsStore` / `SharedPreferencesAppSettingsStore` durable store. The store exposes typed snapshots and observation, records a schema version/default strategy, clamps bounded values, and has device-side persistence proof in `AppSettingsStoreInstrumentedTest`, including close/reopen persistence, observer delivery, defaults, schema behavior, concurrency clamping, and malformed enum fallback. Detailed implementation evidence is recorded in `docs/RMD_1101_SETTINGS_PERSISTENCE_RECONCILIATION_2026-09-29.md` and `docs/RMD_1101_1103_SETTINGS_QUALIFICATION_2026-09-30.md`.

## RMD-1102 — download settings

Ready for canonical reconciliation. Default quality, Wi-Fi/network preference, bounded concurrency, and subtitle default are persisted by the typed store. Wi-Fi preference is consumed by `SchedulingDownloadControlGateway`; configured concurrency is consumed by `DownloadWorkerExecutor`, crosses the Android -> generated UniFFI -> Rust worker boundary, and is bounded by the core authoritative concurrency policy; quality selection is fail-closed against resolved production choices; subtitle preference remains a persisted default without fabricating tracks; no decorative retry-count control is retained. Detailed evidence is recorded in `docs/RMD_1102_RUNTIME_CONCURRENCY_IMPLEMENTATION_2026-09-30.md` and `docs/RMD_1102_RUNTIME_SETTINGS_QUALIFICATION_2026-09-30.md`.

## RMD-1103 — playback settings

Ready for canonical reconciliation. Playback speed and remember-position behavior are persisted settings consumed by the canonical Media3 controller/session path in `PlaybackScreen`; speed is applied to the controller and position restoration/persistence is enabled or suppressed according to the durable setting. No decorative playback setting is being claimed as runtime behavior. Detailed evidence is recorded in `docs/RMD_1103_PLAYBACK_SETTINGS_RECONCILIATION_2026-09-29.md` and `docs/RMD_1101_1103_SETTINGS_QUALIFICATION_2026-09-30.md`.

## RMD-1104 — storage settings

Ready for canonical reconciliation. Production storage settings calculate managed-media usage, present useful managed-storage breakdown, provide safe cleanup with explicit destructive confirmation, and have temporary-storage behavioral tests. Detailed evidence is recorded in the RMD-1104 reconciliation material present on exact master `ad2474d169508f085cfbc5e7d147fcbb8d31ef46`.

## RMD-1105 — appearance settings

Ready for canonical reconciliation. System/Light/Dark and Library layout preferences are durably persisted through the typed settings store and consumed by production Compose immediately; the Library layout preference survives recreation through durable settings rather than preview-only state. Detailed evidence is recorded in the RMD-1105 reconciliation material present on exact master `ad2474d169508f085cfbc5e7d147fcbb8d31ef46`.

## RMD-1106 — About

Ready for canonical reconciliation. Production About information uses build/version/source-revision metadata rather than a hard-coded `0.1.0`, and renders the supported license/privacy/legal/support/diagnostic information. Detailed evidence is recorded in the RMD-1106 reconciliation material present on exact master `ad2474d169508f085cfbc5e7d147fcbb8d31ef46`.

## Canonical TODO action

Once a subsequent exact master containing this note is green, RMD-1101 through RMD-1106 may be checked in the canonical TODO with a concise evidence paragraph referencing this note, the detailed per-section reconciliation documents, exact implementation/qualification SHAs, and the six successful exact-head run IDs above. This must be a canonical TODO edit; no separate checklist supersedes the canonical file.
