# RMD-604 canonical TODO reconciliation patch — 2026-10-08

The sole canonical checklist remains `docs/OFFLINE_YT_PLAYER_REMEDIATION_TODO_2026-09-17.md`. This file is not a second checklist; it records the exact RMD-604 block that should replace the unchecked RMD-604 block once a targeted edit or safe whole-file update is available.

## Preconditions already met

Current `master` head `a5d07a8cc31ae086716656ede9594a73fffff065` passed all six exact-head workflows:

- CI `37731756368`
- Android smoke `37731756467`
- Android FGS timeout `37731756345`
- Supply chain `37731756499`
- CI evidence `37731756451`
- Deterministic E2E fixture `37731756327`

Supporting evidence is `docs/RMD_604_LIFECYCLE_STATE_QUALIFICATION_2026-10-08.md`, which records the production lifecycle-state implementation and the earlier qualified evidence head `b78b821d0b22f67446edf04b4b42eb03d7b0e13e`.

## Replacement block for canonical TODO

Replace the current RMD-604 block in `docs/OFFLINE_YT_PLAYER_REMEDIATION_TODO_2026-09-17.md`:

```markdown
### RMD-604 — ViewModel architecture

- [ ] Add ViewModels for main feature screens or an equivalent lifecycle-aware state holder.
- [ ] Collect repository flows lifecycle-safely.
- [ ] Keep blocking FFI/network work off main thread.
- [ ] Restore relevant UI state across configuration/process recreation where appropriate.
```

with:

```markdown
### RMD-604 — ViewModel architecture

- [x] Add ViewModels for main feature screens or an equivalent lifecycle-aware state holder.
- [x] Collect repository flows lifecycle-safely.
- [x] Keep blocking FFI/network work off main thread.
- [x] Restore relevant UI state across configuration/process recreation where appropriate.

**Evidence (RMD-604):** `docs/RMD_604_LIFECYCLE_STATE_QUALIFICATION_2026-10-08.md` records the production lifecycle-state implementation and device qualification. `MainActivity` owns lifecycle-scoped repository-backed Library/Downloads state, opens UniFFI gateways on `offline-yt-production-bootstrap`, and wires repository observation through `AppStateRefresher`; `AppStateRefresher` runs repository reads on its own background executor, observes the active Library query, starts/stops with Activity lifecycle, suppresses stale stopped-epoch repository emissions, and is covered by `AppStateRefresherTest`. `LifecyclePublicationGate` prevents UI updates queued before stop/recreation from publishing into stale Activity state and is covered by `LifecyclePublicationGateTest`. `AppShell.kt` uses `rememberSaveableStateHolder` plus `rememberSaveable` for navigation, Library search, Add draft, and Settings section state, and `LifecycleStateRestorationComposeTest` is explicitly included in the API-29 Android smoke workflow to prove navigation plus saved-instance restoration on device. Exact master `b78b821d0b22f67446edf04b4b42eb03d7b0e13e` passed CI `37725728457`, Android smoke `37725728393`, Android FGS timeout `37725728417`, Supply chain `37725728379`, CI evidence `37725728384`, and Deterministic E2E fixture `37725728443`; current master `a5d07a8cc31ae086716656ede9594a73fffff065` retained the implementation and passed CI `37731756368`, Android smoke `37731756467`, Android FGS timeout `37731756345`, Supply chain `37731756499`, CI evidence `37731756451`, and Deterministic E2E fixture `37731756327`. This closes RMD-604 only; OS-level process-death/full offline E2E remains tracked under RMD-1200/RMD-1500.
```
