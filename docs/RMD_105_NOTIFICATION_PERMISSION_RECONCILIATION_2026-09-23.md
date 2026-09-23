# RMD-105 Notification permission reconciliation

This evidence note reconciles the implementation and qualification behind RMD-105 without replacing or compressing the detailed remediation TODO. The canonical checklist remains `docs/OFFLINE_YT_PLAYER_REMEDIATION_TODO_2026-09-17.md`; its RMD-105 checkboxes must only be marked complete when this evidence is incorporated there after the evidence PR itself is qualified and merged.

## Scope

RMD-105 requires Android 13+ notification-permission behavior, a defined denied-permission mode, protection against queue-state corruption or false reporting when notifications are denied, and instrumentation coverage where feasible.

## Implementation evidence

- `app/src/main/AndroidManifest.xml` declares `android.permission.POST_NOTIFICATIONS` for Android 13+ notification runtime permission handling.
- `app/src/main/java/com/ekkus/offlineytplayer/MainActivity.kt` uses `ActivityResultContracts.RequestPermission` for `Manifest.permission.POST_NOTIFICATIONS` and records grant/denial outcomes with `DownloadNotificationPermissionStateStore.recordGrantState`.
- `app/src/main/java/com/ekkus/offlineytplayer/downloads/DownloadNotificationPermission.kt` defines `DownloadNotificationPermissionPolicy.requiresRuntimePermission` for SDK 33+, maps denied permission to `DownloadNotificationPermissionBehavior.QueueStateOnly`, and explicitly preserves the invariant that denial does not mutate durable queue state.
- `DownloadNotificationPermissionStateStore` persists granted/denied/unknown permission state in app-private storage, enabling production UI/runtime code to render in-app queue state instead of falsely reporting queued work through denied notifications.
- `docs/ANDROID_BACKGROUND_EXECUTION.md` documents that notification denial must not mutate durable queue state or silently report work as complete, and that in-app queue state remains authoritative when notification permission is denied.

## Qualification evidence

- `app/src/test/java/com/ekkus/offlineytplayer/downloads/DownloadNotificationPermissionPolicyTest.kt` verifies SDK gating, denied-permission queue-state behavior, and the `MainActivity` permission-request wiring.
- `app/src/test/java/com/ekkus/offlineytplayer/downloads/NotificationPermissionInstrumentationContractTest.kt` keeps the instrumentation expectation visible in the JVM test tier.
- `app/src/androidTest/java/com/ekkus/offlineytplayer/downloads/DownloadNotificationPermissionInstrumentedTest.kt` verifies packaged-app granted, denied, and cleared permission-state persistence, including denied behavior that leaves the app in `QueueStateOnly` mode.
- `app/src/androidTest/java/com/ekkus/offlineytplayer/downloads/NotificationPermissionStateInstrumentationTest.kt` verifies the packaged app persists grant and denial outcomes without inventing an initial state.
- This PR expands `.github/workflows/android-smoke.yml` so the small API-29 smoke lane runs both packaged-app notification-permission state tests. API 29 cannot exercise the platform runtime permission dialog, but it does verify the app-owned persistence and denied-mode behavior on a real installed APK as the feasible fast-lane instrumentation tier. Android 13+ dialog-level permission behavior remains part of broader RMD-1400/RMD-1500 Android qualification.

## Reconciliation result

RMD-105 has manifest permission wiring, production request/record behavior, denied-permission semantics, queue-state preservation invariants, JVM policy coverage, packaged-app instrumentation for persisted granted/denied state, and small-smoke-lane execution for the feasible non-dialog runtime behavior. The canonical remediation TODO may therefore reconcile RMD-105 after this evidence PR is itself qualified and merged, while preserving broader Android behavioral and E2E qualification under RMD-1400/RMD-1500.
