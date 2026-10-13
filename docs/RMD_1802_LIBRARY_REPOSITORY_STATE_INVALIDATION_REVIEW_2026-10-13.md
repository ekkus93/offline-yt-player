# RMD-1802 — Library repository-state invalidation review (2026-10-13)

Status: **open; implementation and exact-head qualification required**.

## Finding

The production `LibraryScreen` in `app/src/main/java/com/ekkus/offlineytplayer/ui/LibraryDownloads.kt` stores expanded `selectedDetails`, `renameRow`, `pendingRemove`, and a request generation independently of its `LibraryScreenState` input. A transition from Ready to Loading/Unavailable/Failed or a Ready refresh that removes a row does not invalidate those states. A delayed `AppLibraryDetailsGateway.getDetails` completion can also publish a Details panel for a row that no longer exists in the current repository state.

This can present stale private metadata as authoritative and retain mutation controls for a missing item.

## Required remediation

- Invalidate pending detail generations when repository readiness or row identity membership changes.
- Drop orphaned Details/Rename/Remove panels and gate their rendering on the current Ready row IDs.
- Add packaged Compose regression holding a Details gateway response across Ready → Unavailable → a different Ready identity, asserting that stale details and mutation controls never appear.
- Run the full eight-workflow exact-head matrix, then reconcile the canonical TODO. Do not check RMD-1802/RMD-1803/RMD-1805 based on this finding alone.
