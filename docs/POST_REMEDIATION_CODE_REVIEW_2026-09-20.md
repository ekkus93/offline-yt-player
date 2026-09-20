# Offline YT Player — Post-remediation code review

Reviewed against `master` at `696980733a0dcc68e7bbc1c39040ce138bc75795` for RMD-1802.

This review is intentionally fail-closed. It records remaining production-path gaps and does not claim engineering closeout.

## Release-blocking findings

### PRR-001 — Production Compose actions still contain no-op callbacks

`app/src/main/java/com/ekkus/offlineytplayer/ui/AppShell.kt` still contains empty `onClick = {}` callbacks for Paste and Download. `app/src/main/java/com/ekkus/offlineytplayer/ui/LibraryDownloads.kt` still contains empty callbacks for library Details/Remove and download Pause/Resume/Retry/Details/Cancel.

This violates RMD-G05 and leaves RMD-701/RMD-704/RMD-603/RMD-604/RMD-1100 user workflows incomplete. Existing policy/unit tests do not constitute production wiring.

Required repair: route every visible required action through an app-owned production controller/gateway, or remove the control only if the authoritative product requirements explicitly remove that action. Add behavioral Compose/instrumentation evidence.

### PRR-002 — Library and Downloads production screens still fabricate empty repository state

`LibraryScreen` assigns `emptyList<LibraryRowModel>()`; `DownloadsScreen` assigns `emptyList<DownloadRowModel>()`. These are hard-coded production empty states rather than durable repository results.

This violates RMD-G06 and leaves RMD-601/RMD-602/RMD-604 incomplete.

Required repair: introduce lifecycle-owned screen state backed by the production core gateway/repository, map persisted library/download records into presentation models, and cover non-empty/restart/filter behavior with behavioral tests.

### PRR-003 — MainActivity does not construct or inject the production core gateways

`MainActivity` calls `OfflineYTPlayerApp(initialSharedUrl = sharedUrl)` without constructing `GeneratedUniffiCoreGateway` or `GeneratedUniffiDownloadControlGateway`. The generated gateways exist, but the production UI cannot reach them through the current composition root.

This leaves RMD-203 production wiring incomplete even though gateway adapter code exists.

Required repair: add an application/activity-owned composition root with app-private database/media paths, lifecycle cleanup, off-main-thread state loading, and dependency injection into the Compose surface.

### PRR-004 — Add/Analyze remains preview/policy driven rather than production source resolution

`AddScreen` uses `DownloadSetupRoute.previewFor(url)` for analysis. The production path therefore does not demonstrate a real registered live source adapter resolving a supported URL and returning provider-neutral metadata/plan.

This leaves RMD-301 through RMD-304 and RMD-703 unresolved regardless of fixture/policy tests.

Required repair: expose production source analysis through the app-owned core gateway, keep deterministic fixtures for CI, and retain live-source qualification as an explicit bounded/manual gate where policy requires it.

### PRR-005 — FFI source surface is still fixture-specific

`core/src/ffi_source.rs` owns a `DirectFixtureSource`, so that FFI surface cannot itself serve as proof of production YouTube resolution. Fixture code is valid deterministic test infrastructure, but must not be cited as live production-provider evidence.

Required repair: keep fixture and production source services distinct and ensure the production registry/gateway selects the real adapter for supported production URLs.

## Review conclusion

RMD-1802 is **not complete**. The review found concrete release blockers in production UI wiring and repository/source integration. RMD-1803 exact-head final qualification must not begin until these findings are repaired and the detailed TODO is reconciled with implementation/test/SHA evidence.

Historical/policy-only tests must not be cited as behavioral proof for these findings. The external YouTube/service-policy/legal approval remains separate and unresolved.