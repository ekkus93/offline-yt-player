# RMD-101 Network capability reconciliation

This evidence note reconciles the implementation and qualification behind RMD-101 without replacing or compressing the detailed remediation TODO. The canonical checklist remains `docs/OFFLINE_YT_PLAYER_REMEDIATION_TODO_2026-09-17.md`; its RMD-101 checkboxes must only be marked complete when this evidence is incorporated there.

## Scope

RMD-101 requires Android network capability to be real, declared, tested at manifest level, and proven through a deterministic Android runtime fixture.

## Implementation evidence

- `app/src/main/AndroidManifest.xml` declares `android.permission.INTERNET` and retains only the implemented network/runtime-related permissions needed by current production paths, including `ACCESS_NETWORK_STATE`, foreground-service permissions, boot recovery, notifications, and UIDT jobs.
- `app/src/test/java/com/ekkus/offlineytplayer/ManifestPermissionTest.kt` asserts `android.permission.INTERNET` and `android.permission.ACCESS_NETWORK_STATE` are declared, and rejects broad storage and location permissions that are not needed by the implemented runtime paths.
- `app/src/androidTest/java/com/ekkus/offlineytplayer/NetworkCapabilityInstrumentedTest.kt` verifies the packaged app declares `INTERNET`, starts a deterministic loopback HTTP fixture, reads the fixture through the packaged app process, and proves the app can open a deterministic fixture network endpoint on emulator/device.
- PR #323 updated `.github/workflows/android-smoke.yml` so the small API-29 Android smoke lane runs `NetworkCapabilityInstrumentedTest` beside the basic runtime smoke and packaged Rust/UniFFI gateway smoke tests. This keeps Android qualification tiered while making RMD-101 runtime proof part of the fast smoke lane.

## Qualification evidence

- PR #323 exact head `83c158817e012df8b53d183ebb231b95548a4841` passed CI run `35819180161` and Android-smoke run `35819180151` on the pull request event.
- The same exact head also passed push CI run `35819168370` and push Android-smoke run `35819168389`.
- PR #323 merged as `ea39061a3f2864a360a2912d6a23785190b92a99`.
- Post-merge master verification passed CI run `35820668106` and Android-smoke run `35820668108` on exact merge SHA `ea39061a3f2864a360a2912d6a23785190b92a99`.

## Reconciliation result

RMD-101 now has implementation, manifest-level test coverage, deterministic Android runtime fixture coverage, exact-head PR CI, merged-master evidence, and post-merge master verification. The canonical remediation TODO may therefore reconcile the RMD-101 checkboxes as complete while preserving RMD-102 and later platform-compliance items as separate open work.
