# RMD-1001 Operational Library Screen Reconciliation — 2026-10-07

## Scope

RMD-1001 requires the Library screen to render real repository items, search actual persisted records, implement advertised list/grid behavior, preserve the selected layout setting, and provide empty/loading/error states.

This note gathers current-master evidence only. Do not check RMD-1001 in the canonical remediation TODO until the documentation head containing this note has passed exact-head qualification on `master`.

## Current production evidence

### Real repository items and search

`docs/RMD_601_LIBRARY_REPOSITORY_RECONCILIATION_2026-10-07.md` records the production Library repository wiring:

- `MainActivity.bootstrapProductionUi()` opens the app-private SQLite-backed `GeneratedUniffiCoreGateway`.
- Initial Library state comes from `openedCore.listLibrary()` rather than a hard-coded empty or preview list.
- `AppStateRefresher` refreshes Library state off the UI thread and forwards the current Library search query to `gateway.listLibrary(query)`.
- `toLibraryScreenState(...)` maps persisted core metadata and local playback assets into `LibraryRowModel` values.

This satisfies the RMD-1001 requirements for rendering real repository items and searching persisted records; it does not close Library details/rename/remove actions, which remain separate under RMD-1003 through RMD-1005.

### List/grid layout and preserved setting

`docs/RMD_1105_APPEARANCE_SETTINGS_RECONCILIATION_2026-09-30.md` records durable `LibraryLayoutSetting` persistence. `AppSettingsSnapshot` and `SharedPreferencesAppSettingsStore` persist `LibraryLayoutSetting.List` and `LibraryLayoutSetting.Grid`; `SettingsPage` mutates the value through the typed settings store; `LibraryScreen` consumes the persisted layout setting; and `AppSettingsStoreInstrumentedTest` proves typed setting persistence across close/reopen.

This satisfies the RMD-1001 list/grid and selected-layout preservation requirements for the advertised Library layouts.

### Loading, empty, error, and populated states

The Library surface starts in `LibraryScreenState.Loading`; startup/core failures map to `LibraryScreenState.Failed(...)`; successful repository reads map to `LibraryScreenState.Ready(...)`; and empty/populated rendering is exercised by production Compose tests.

`docs/RMD_1402_BEHAVIORAL_COMPOSE_RECONCILIATION_2026-09-25.md` records production Compose behavior coverage for Library empty/populated behavior. `docs/RMD_1403_1405_UI_QUALIFICATION_RECONCILIATION_2026-09-25.md` records screenshot/golden, compact-layout, accessibility, and large-text qualification for the Library states.

## Exact-head qualification references already available

- RMD-601 repository evidence: exact master `f62aa7aa5d72d3dccd87b9d4e50f60ec62ce7754` passed CI `37675821415`, Android smoke `37675821448`, Android FGS timeout `37675821387`, Supply chain `37675821421`, CI evidence `37675821384`, and Deterministic E2E fixture `37675821443`.
- RMD-1105 layout persistence evidence: exact master `3508e0df82f514b84769d7ca234c571bf2302734` passed CI `36699824078`, Android smoke `36699823941`, Android FGS/UIDT `36699823965`, Supply chain `36699823904`, CI evidence `36699823973`, and Deterministic E2E fixture `36699824064`.
- RMD-1402 Library behavior evidence: exact master `c6a1b80d033285c2bb4ceb7209b589467ab454bf` passed CI `36181189252`, Android smoke `36181189235`, and Android FGS timeout `36181189239`.
- RMD-1403 through RMD-1405 UI qualification evidence is recorded in the canonical TODO and reconciliation docs.

## Checklist impact after this note qualifies

Once this documentation head has passed exact-head CI on `master`, the following RMD-1001 subtasks are eligible to check in the canonical TODO:

- Render real repository items.
- Search actual persisted records.
- Implement list/grid behavior if both remain advertised.
- Preserve selected layout setting.
- Provide empty/loading/error states.

This note does not close RMD-1002 canonical playback launch, RMD-1003 details, RMD-1004 rename, RMD-1005 remove, RMD-1006 Downloads UX, RMD-1500 E2E, RMD-1800 closeout, or the external policy/legal release gate.
