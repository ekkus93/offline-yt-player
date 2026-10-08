# RMD-604 lifecycle-state qualification evidence — 2026-10-08

The canonical checklist remains `docs/OFFLINE_YT_PLAYER_REMEDIATION_TODO_2026-09-17.md`.

## Production implementation

- `MainActivity` owns lifecycle-scoped repository-backed Library/Downloads state and initializes UniFFI on a background executor.
- `AppStateRefresher` observes repository state off the UI thread and suppresses results from stopped lifecycle epochs.
- `LifecyclePublicationGate` rejects UI-thread updates queued before Activity stop or recreation.
- `AppShell.kt` uses `rememberSaveableStateHolder` for destination state and `rememberSaveable` for navigation, Library search, Add draft, and Settings section.

## Qualification

`AppStateRefresherTest` and `LifecyclePublicationGateTest` cover lifecycle transitions and stale result suppression. `LifecycleStateRestorationComposeTest` is explicitly included in the API-29 smoke instrumentation class list and exercises navigation plus saved-instance restoration. This is not a substitute for OS-level process-death E2E, which remains tracked under RMD-1200/RMD-1500.

Exact qualified master `b78b821d0b22f67446edf04b4b42eb03d7b0e13e` passed all six workflows: CI `37725728457`, Android smoke `37725728393`, Android FGS timeout `37725728417`, Supply chain `37725728379`, CI evidence `37725728384`, and Deterministic E2E fixture `37725728443`.

The smoke workflow also enforces successful golden preparation and the helper matches both inline and multiline Download Setup hash declarations. No unreviewed golden hash was accepted.

## Reconciliation

This is a supporting evidence note, not a second checklist. The four RMD-604 checkboxes in the canonical TODO must be reconciled from this exact-head evidence.
