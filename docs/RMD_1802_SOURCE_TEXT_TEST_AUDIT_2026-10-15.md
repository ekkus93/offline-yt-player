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
| `MediaSessionBehaviorQualificationTest` (4) | same class; audio usage in `sessionPlayerDeclaresMediaAudioUsage`; noisy receiver state in `sessionPlayerRegistersRuntimeAudioBecomingNoisyHandling` |
| `PlaybackSessionOwnershipTest` (2) | same class proves service ownership; `serviceDestroyReleasesThePlayerBeforeTheMediaSession` drives real service destruction, observes immediately after each release call, and asserts their order. It passed in the API 29 base smoke portion of run `38861956038`; the later golden-evidence verifier failure does not invalidate that instrumented test result. |
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
| `DownloadNotificationPolicyTest` (2) | `Rmd1507NotificationControlInstrumentedTest` asserts the real posted notification is ongoing, indeterminate, on the low-importance channel, and drives Pause/Resume/Cancel. It also checks `PendingIntent.isImmutable` on each posted action. |
| `DownloadForegroundServicePolicyTest` (3 source-text) | `Rmd1501AndroidRuntimeFixtureInstrumentedTest`, `Rmd1507NotificationControlInstrumentedTest`; manifest test kept |
| `DownloadRebootRecoveryPolicyTest.rebootReceiverDoesNotStartForegroundServiceOrTouchProtectedState` | `DownloadRebootReceiverInstrumentedTest`; manifest test kept |
| `DownloadForegroundTimeoutPolicyTest.foregroundServiceImplementsTimeoutBeforeStopping`, `DownloadForegroundServiceTimeoutPolicyTest` (2) | `DownloadForegroundTimeoutInstrumentedTest` covers the extracted helper sequence and persistence store; it does not instantiate the Service. `DownloadForegroundTimeoutAdbInstrumentedTest` currently checks manifest metadata and calls the persistence store directly. Actual `Service.onTimeout` ordering remains a **gap**. |
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

1. ~~`setHandleAudioBecomingNoisy(true)` on the session player.~~ The packaged
   `sessionPlayerRegistersRuntimeAudioBecomingNoisyHandling` test observes the service's configured
   ExoPlayer and verifies Media3's live `AudioBecomingNoisyManager` is enabled. The protected system
   broadcast itself cannot be sent by the test; the runtime registration state is the observable
   contract.
2. ~~Release order (`player.release()` before `session.release()`) in `PlaybackSessionService.onDestroy`.~~
   `PlaybackSessionBehaviorInstrumentedTest.serviceDestroyReleasesThePlayerBeforeTheMediaSession`
   drives real service destruction and records a null-by-default observer immediately after each
   real release call; it asserts that player release precedes session release. This test ran in the
   API 29 base smoke portion of run `38861956038`, before the separate golden-evidence export
   verifier failed later in the workflow.
3. ~~`FLAG_IMMUTABLE` on the download notification action PendingIntents.~~ The notification
   instrumentation checks the actual posted Pause/Resume/Cancel action PendingIntents.
4. Timeout ordering is not proven through the production `Service.onTimeout` callback. `timeoutSequencePersistsThenStopsNativeWorkThenStopsForeground` exercises the extracted helper but does not instantiate the Android service; the current API-35 workflow only calls the persistence store. Actual callback/device qualification remains open.
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

## 2026-10-16 timeout-evidence correction

The current API-35 workflow runs `scripts/run_android_fgs_uidt_qualification.sh`; its timeout test
does not start `DownloadForegroundService`, modify `device_config`, or observe `Service.onTimeout`.
It checks the manifest and invokes `DownloadForegroundTimeoutStore.persistTimeout` directly. The
separate helper-order test runs `DownloadForegroundTimeoutSequence` without instantiating the
Android Service. The prior RMD-104/API-35 reconciliation notes overstated this as a forced timeout;
those claims are marked historical, and the RMD-104 ADB/emulator subtask is reopened. Neither the
production timeout callback nor its native-stop ordering is currently qualified through Android.

## 2026-10-16 exact-head follow-up

Exact master `2db7042920c463175047bbae6fe4b4943930e497` completed six of eight required workflows,
while Android smoke and Android real-network E2E failed. Android smoke executed all 12 pinned golden
methods successfully, but the host verifier rejected every raster digest sidecar: Android MediaStore
published the requested `name.sha256` display name as `name.sha256.txt` because the item MIME type
was `text/plain`. The verifier now accepts either MediaStore's MIME-normalized spelling or the
app-private `.sha256` file. The `test_media_store_text_mime_extension_passes` governance test
reproduces the export spelling. The separate real-network failure timed out waiting for a completed
transfer on API 29; local reproduction was unavailable because the installed API 29 system image
cannot create a second emulator within the available disk space. The API 36 emulator follows UIDT
and is not equivalent to the failing API 29 foreground-service path.

The packaged runtime audio-becoming-noisy observer and posted notification PendingIntent immutability
checks described above have been added. Their source/test changes have not yet run in the API 29
smoke workflow or on an exact-head candidate. Continue to leave final review, freeze, merge, and
engineering-closeout items open.

## 2026-10-16 first candidate qualification findings

The first candidate `1b430ba02a8bfffcd0ed4c4bbb852f0675a60c35` passed the API 29 notification control
path until the new direct `PendingIntent.isImmutable()` assertion called an API-31 method on API 29.
The action PendingIntents are posted and exercised on API 29; direct immutability inspection is now
gated to API 31+, where Android exposes that method. A local API 36 run with notifications granted
passed the actual Pause/Resume/Cancel sequence.

The same candidate's API 29 real-network run passed airplane-mode loss/pause/resume, then failed the
follow-up Wi-Fi metered override: the independent observer reported `Metered`, but durable queue
state stayed `DOWNLOADING` at 8,015,872 of 8,388,608 bytes. On the next candidate, the metered
transition did settle the queue to `PAUSED`, but after restoring unmetered Wi-Fi the resumed attempt
timed out at 24,104,960 of 25,165,824 bytes. The Android test now captures the foreground service
observer's callback sequence, fixture server request/byte counts, and partial/final asset lengths on
both pause and completion timeouts, increases the fixture to 24 MiB, and gives the slow emulator up
to 180 seconds to finish. The candidate `0eee678482a8ca42ae592c6a30776419117b83a1` run
`38869494052` still failed. The service's metered pause and resumed retry are now observed, but the
full API-29 network-policy acceptance remains unresolved until a fresh run completes successfully.
