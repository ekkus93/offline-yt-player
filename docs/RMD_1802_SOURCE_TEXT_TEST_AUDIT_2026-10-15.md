# RMD-1802 source-text test audit (2026-10-15)

Scope: the RMD-1802 item "Search for policy-only tests being cited as behavioral proof."
Status: **implementation/test evidence only.** No CI matrix has run on these commits, the
independent review is not finished, and RMD-1802/1803/1805 and the global rules stay open.

## Finding

About 35 JVM test files under `app/src/test` read a production Kotlin source file (or another test
file) as text and asserted `source.contains("...")`. They pass whatever the code does at runtime:
renaming nothing but changing behavior still passes, and correct behavior with a refactored
spelling fails. Several were cited in reconciliation documents as evidence of runtime behavior
(playback ownership, settings consumption, notification controls, timeout handling).

## Method

Every source-text assertion was classified:

- **Static manifest/resource guard (kept).** The Android manifest and `res/` directories are
  declarative configuration that the platform reads as data; reading them is a legitimate static
  check. They are *not* behavioral proof and must not be cited as such.
- **Redundant (deleted).** The same claim is already exercised by an executable test.
- **Replaced (deleted + new behavioral test).** No executable coverage existed.
- **Gap (deleted, recorded below).** The claim cannot be exercised cheaply or at all; it is now an
  explicit review-only claim rather than a fake test.

## Deleted tests and what covers the claim now

| Deleted | Covered by |
| --- | --- |
| `PlaybackControllerIntegrationTest` (3) | `PlaybackSessionBehaviorInstrumentedTest`: speed/seek controls reach the shared session player, two controllers share one player |
| `MediaSessionBehaviorQualificationTest` (4) | same class; audio usage in `sessionPlayerDeclaresMediaAudioUsage`; noisy handling is a **gap** |
| `PlaybackSessionOwnershipTest` (2) | same class (service-owned player); release order on destroy is a **gap** |
| `PlaybackSessionPolicyTest.serviceDelegatesAudioFocusAndNoisyHandlingToMedia3Player` | `sessionPlayerDeclaresMediaAudioUsage`; noisy handling is a **gap** |
| `SubtitlePlaybackControlIntegrationTest` (2) | `LocalPlaybackPolicyTest.subtitleSelectionCyclesEveryTrackThenOffThenFirstTrack`; `subtitleControlCyclesTracksThenOffOnTheSessionTrackSelection` (real Compose + session track selection); `Rmd1503SubtitleOfflineInstrumentedTest` |
| `AudioPlaybackControlIntegrationTest` (2) | `LocalPlaybackPolicyTest.audioSelectionCyclesPersistedTracksAndWrapsWithoutOff`; `audioControlCyclesPersistedTracksAndWrapsOnTheSessionTrackSelection` |
| `SplitAudioPlaybackSessionIntegrationTest` (2) | `splitAudioPathSurvivesTheMediaIdTransportedByControllersAndRejectsRemoteIds`; `Rmd1502SplitAvOfflineInstrumentedTest` |
| `SplitAvPlaybackSourcePolicyTest` (2) | `LocalPlaybackPolicyTest` remote/duplicate-path rejection tests; `Rmd1502SplitAvOfflineInstrumentedTest`. The second test only checked that other tests' names exist. |
| `PlaybackPositionPersistenceIntegrationTest` (3) | `enabledResumeStartsAtStoredPositionAndDisposalPersistsTheControllerPosition`, `disabledResumeStartsAtZeroAndNeverWritesPosition`. The third test only checked other tests' names. |
| `PlaybackPositionProductionPathTest` (2) | the two tests above; `GeneratedUniffiCoreGatewaySmokeTest.packagedPlaybackPositionGatewayCallsUnsignedGeneratedKotlinBinding` for the real UniFFI call |
| `OfflinePlaybackQualificationTest` source-text method | the two behavioral tests in the same class are kept; `Rmd1501*` instrumented tests |
| `AppSettingsStoreContractTest` (4) | `AppSettingsStoreInstrumentedTest` (typed snapshot, observation, enum fallback, clamping) |
| `PlaybackSettingsRuntimeConsumptionTest` (3) | `defaultPlaybackSpeedSettingIsAppliedToTheSessionPlayer`, `speedControlEmitsTheNextDefaultSpeedSettingMutation`, the resume tests, and `SettingsPagesRuntimeInstrumentedTest.playbackSettingsExposeOnlyRuntimeBackedControlsAndPersist` |
| `AppearanceSettingsRuntimeConsumptionTest` (1) | `SettingsPagesRuntimeInstrumentedTest.appearanceSettingsCycleThemeAndLibraryLayoutAndPersist`, `Rmd1802SettingsObserverInstrumentedTest`, `CollectionScreensInstrumentedTest` layout tests |
| `DownloadSettingsRuntimeConsumptionTest` (3) | `SettingsPagesRuntimeInstrumentedTest.downloadSettingsControlsPersistAndRenderTheStoredValues`, `CollectionScreensInstrumentedTest.addScreenSummarizesTheStoredDownloadSettings`, `AddWorkflowPolicyTest`, `Rmd1802SettingsObserverInstrumentedTest` |
| `LibraryOperationalScreenTest` source-text methods (3) | `CollectionScreensInstrumentedTest`: list/grid toggle and search filtering |
| `DownloadsOperationalScreenTest.downloadRowsShowProgressStateErrorSpeedAndEtaFields` | `CollectionScreensInstrumentedTest.downloadRowsRenderStateProgressSpeedEtaAndError` |
| `DownloadNotificationPolicyTest` (2) | `Rmd1507NotificationControlInstrumentedTest` now asserts the real posted notification is ongoing, indeterminate, on the low-importance channel, and drives Pause/Resume/Cancel. `FLAG_IMMUTABLE` on the action PendingIntents is a **gap** (no runtime accessor). |
| `DownloadForegroundServicePolicyTest` (3 source-text) | `Rmd1501AndroidRuntimeFixtureInstrumentedTest`, `Rmd1507NotificationControlInstrumentedTest`; manifest test kept |
| `DownloadRebootRecoveryPolicyTest.rebootReceiverDoesNotStartForegroundServiceOrTouchProtectedState` | `DownloadRebootReceiverInstrumentedTest`; manifest test kept |
| `DownloadForegroundTimeoutPolicyTest.foregroundServiceImplementsTimeoutBeforeStopping`, `DownloadForegroundServiceTimeoutPolicyTest` (2) | `DownloadForegroundTimeoutInstrumentedTest`, `DownloadForegroundTimeoutAdbInstrumentedTest`. Persist/native-stop/`stopForeground`/`stopSelf` ordering is covered by `DownloadForegroundTimeoutInstrumentedTest.timeoutSequencePersistsThenStopsNativeWorkThenStopsForeground` via the `DownloadForegroundTimeoutSequence` seam that `onTimeout` delegates to (the test does not instantiate the Service). |
| `DownloadExecutionSchedulerPolicyTest.uidtJobCarriesOnlyDurableQueueIdentityInExtras` | `DownloadSchedulerExtrasInstrumentedTest` (real `PersistableBundle`) |
| `DownloadExecutionSchedulerPolicyTest.invalidJobExtrasReturnAlreadyFinishedWithoutDoubleSettlement` | **gap** (see below) |
| `DownloadNotificationPermissionPolicyTest.mainActivityRequestsPostNotificationsPermission` | `DownloadNotificationPermissionInstrumentedTest`, `NotificationPermissionStateInstrumentationTest` |
| `DownloadConnectivityInstrumentationContractTest`, `NotificationPermissionInstrumentationContractTest`, `NetworkCapabilityInstrumentationContractTest` | Pure meta tests that read other test files as text. The instrumented tests themselves are the evidence; `scripts/assert_android_smoke_execution.py` already requires a non-skipped run per mandatory class. |
| `LibraryPlaybackGatewayBoundaryTest` (2) | `LibraryIdentityMappingTest`, `MainActivityLibraryMappingTest`, `Rmd1501*` pipeline tests |
| `AppCoreGatewayPolicyTest` (2 source-text of 5) | the unsigned-conversion behavior tests in the same class are kept |
| `ShareIntentPolicyTest` (2 of 3) | `Rmd1504ShareE2EInstrumentedTest`, `Rmd1500AppPipelineFixtureTest`; manifest test kept |
| `StartupReconciliationProductionPathTest` | **gap** (see below) |

## Production changes made to enable real tests

- `LocalPlaybackPolicy.nextSubtitleSelection` / `nextAudioSelection` were private functions inside
  `PlaybackScreen.kt`; they are now `LocalPlaybackPolicy` members so JVM tests exercise the real
  cycling logic.
- `PortraitPlayerScreen` takes an injectable `positionGatewayFactory` (default: the generated UniFFI
  gateway, unchanged behavior) so tests can observe exactly what the screen persists.

## Remaining static guards (kept, not behavioral proof)

`ManifestPermissionTest`, `PortraitShellTest`, `MediaSessionPolicyTest`, `AndroidShellPolicyTest`
(manifest and `res/` directory names), and the single manifest-declaration method in each of
`PlaybackSessionPolicyTest`, `ShareIntentPolicyTest`, `DownloadForegroundServicePolicyTest`,
`DownloadRebootRecoveryPolicyTest`, `DownloadForegroundTimeoutPolicyTest`,
`DownloadForegroundServiceTimeoutPolicyTest` and `DownloadExecutionSchedulerPolicyTest`.
These check declarative platform configuration. They must not be cited as runtime evidence.

## Claims that are now unverified (review-only)

These were previously "covered" only by source-text assertions, which proved nothing. They are
recorded here instead of being replaced by new fake tests:

1. `setHandleAudioBecomingNoisy(true)` on the session player. `ACTION_AUDIO_BECOMING_NOISY` is a
   protected system broadcast that an app cannot send, and there is no getter.
2. Release order (`player.release()` before `session.release()`) in `PlaybackSessionService.onDestroy`.
   Partially covered: `PlaybackSessionBehaviorInstrumentedTest.serviceDestroyReleasesThePlayerBeforeTheMediaSession`
   drives the real service teardown (controller release plus `stopService`) and checks the order of
   a null-by-default observer that `onDestroy` notifies after each real release call. It proves
   `onDestroy` runs and the notification order, not that Media3 observed the releases in that order.
   Not yet executed on a device or emulator.
3. `FLAG_IMMUTABLE` on the download notification action PendingIntents.
4. (Closed) Timeout ordering is now covered; see the table above. Not run on a device yet.
5. `DownloadUserInitiatedJobService.onStartJob` returning `false` without `jobFinished` for a blank
   queue id. `JobParameters` cannot be constructed outside the framework.
6. ~~`MainActivity` running startup reconciliation before the first Library/Downloads reads.~~ Now
   covered by `MainActivityStartupOrderingInstrumentedTest` (fake core gateway via the debug-only
   `MainActivityDependencyOverrides.installCoreForInstrumentation` seam; asserts reconcile is first and
   that a failed or throwing reconcile yields no reads). Not yet run on a device; requires CI evidence.

## Local-environment findings (not product changes)

- An API 36 emulator only posts the download notification once `POST_NOTIFICATIONS` is granted;
  CI runs API 29, where no grant is needed. Run `Rmd1507...` locally with the permission granted
  (`adb install -g`).
- The packaged native core must be built for both ABIs before `connectedDebugAndroidTest` can run
  native-dependent classes: `rustup target add aarch64-linux-android x86_64-linux-android`, build
  `offline-yt-core` for each, and give the emulator a data partition large enough for the debug
  `.so` files (about 70 MB each).
- A failed APK install makes `connectedDebugAndroidTest` print `BUILD SUCCESSFUL` with no tests
  run. Always inspect the result XML.
- Failures reproduced on an unmodified tree (stashed changes) on the API 36 x86_64 emulator, so not
  caused by this change: `PlaybackSessionBehaviorInstrumentedTest.composeSeekControlsReachTheCanonicalSessionPosition`
  (the failure message shows the item loaded and READY with the position already near 20 s, so the
  test's expected 9-12 s window after `+10s` is missed; this looks like a start-up timing race),
  `PlaybackSessionBehaviorInstrumentedTest.systemMediaButtonsPauseResumeAndSeekTheCanonicalSession`
  (cause not investigated), and four `ProductionComposeBehaviorTest` methods
  (`downloadSetupAndAdvancedOptionsRedactProviderLabelsWithoutChangingSelectionIds`,
  `downloadsAndLibraryUnavailableDiagnosticsAreRedactedInRenderedUi`,
  `libraryAndDownloadRowsRedactPersistedProviderMetadataInRealCompose`,
  `player_entry_exposes_transport_and_track_controls`; the last needed the native core, the middle
  two fail with a double `setContent`).
- `MainActivityQueryRecreationInstrumentedTest.realActivityRecreationRestoresLibrarySearchDraft`
  timed out in a whole-`ui`-package run. It was **not** re-run in isolation or on an unmodified tree,
  so its status is unknown.
- None of these are claimed fixed or waived here; CI requalification is still required.

## Not claimed

No exact-head CI evidence exists for these commits. This audit does not complete the RMD-1802
independent review, RMD-1803/1804/1805, or the external legal/service gate.
