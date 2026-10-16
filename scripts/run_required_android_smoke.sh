#!/bin/sh
# Execute mandatory behavioral tests in separate fresh instrumentation invocations.
# The Compose ACTION_SEND/settings tests have also intermittently hit
# SnapshotStateObserver cross-thread state in the bulk suite; isolate their
# ActivityScenario lifecycle and require executable-method evidence.
# Each fresh XML report must prove a non-skipped test from that exact class.
set -eu

for required_class in \
    com.ekkus.offlineytplayer.coregateway.GeneratedUniffiCoreGatewaySmokeTest \
    com.ekkus.offlineytplayer.Rmd1504ShareE2EInstrumentedTest \
    com.ekkus.offlineytplayer.Rmd1802SettingsObserverInstrumentedTest \
    com.ekkus.offlineytplayer.downloads.DownloadConnectivityObserverInstrumentedTest \
    com.ekkus.offlineytplayer.downloads.Rmd1506ConnectivityE2EInstrumentedTest#activeTransferPausesForConnectivityAndWifiOnlyResumesOnlyOnUnmeteredNetwork \
    com.ekkus.offlineytplayer.downloads.Rmd1506ConnectivityE2EInstrumentedTest#secondScheduleIntentDispatchesWorkWithAlreadyRegisteredConnectivityObserver \
    com.ekkus.offlineytplayer.downloads.Rmd1506ConnectivityE2EInstrumentedTest#secondScheduleDuringActiveTransferDispatchesAfterFirstWorkerWithoutNewNetworkCallback \
    com.ekkus.offlineytplayer.downloads.Rmd1507NotificationControlInstrumentedTest \
    com.ekkus.offlineytplayer.ui.MainActivityStartupOrderingInstrumentedTest \
    com.ekkus.offlineytplayer.ui.Rmd1802DownloadsControlThreadingInstrumentedTest#throwingLibraryDetailsGatewayShowsUnavailableWithoutCrashingComposition \
    com.ekkus.offlineytplayer.ui.Rmd1802DownloadsControlThreadingInstrumentedTest#visibleDownloadsPauseInvokesControlOffMainAndReportsDurableUpdate \
    com.ekkus.offlineytplayer.ui.StorageSettingsRecoveryInstrumentedTest#initialScanFailureCanRetryWithoutLeavingSettings \
    com.ekkus.offlineytplayer.ui.StorageSettingsRecoveryInstrumentedTest#cleanupRefreshFailureInvalidatesStaleSummaryAndCanRetry \
    com.ekkus.offlineytplayer.ui.DuplicateProviderOptionsInstrumentedTest#duplicateAudioAndSubtitleLabelsEnqueueSelectedOriginalIds \
    com.ekkus.offlineytplayer.ui.DuplicateProviderOptionsInstrumentedTest#duplicateQualityLabelsEnqueueTheSelectedOriginalChoiceId \
    com.ekkus.offlineytplayer.ui.DownloadSetupSingleFlightInstrumentedTest#enqueueCannotBeSubmittedTwiceWhileFirstRequestIsPending
do
    class_ref=${required_class%%#*}
    class_name=${class_ref##*.}
    case "$required_class" in
        *"#"*) explicit_method=${required_class#*#}; report_tag="${class_name}-${explicit_method}" ;;
        *) explicit_method=""; report_tag="$class_name" ;;
    esac
    report_dir="app/build/reports/androidSmokeLogs/required-${report_tag}-results"
    log="app/build/reports/androidSmokeLogs/required-${report_tag}.log"

    # Remove prior reports before executing so a cached/stale XML cannot pass the gate.
    rm -rf app/build/outputs/androidTest-results/connected
    if ! ./gradlew --no-daemon connectedDebugAndroidTest \
        "-Pandroid.testInstrumentationRunnerArguments.class=$required_class" > "$log" 2>&1
    then
        cat "$log"
        python3 scripts/summarize_android_test_failures.py || true
        exit 1
    fi

    mkdir -p "$report_dir"
    cp -R app/build/outputs/androidTest-results/connected/. "$report_dir"/
    case "$required_class" in
        com.ekkus.offlineytplayer.Rmd1504ShareE2EInstrumentedTest)
            required_method="actionSendFlowsThroughAnalyzeSetupRealSchedulerWorkerLibraryAndBackStack" ;;
        com.ekkus.offlineytplayer.Rmd1802SettingsObserverInstrumentedTest)
            required_method="backgroundStoreUpdatePublishesAppearanceToComposeOnMainThread" ;;
        com.ekkus.offlineytplayer.ui.StorageSettingsRecoveryInstrumentedTest)
            required_method="initialScanFailureCanRetryWithoutLeavingSettings" ;;
        *) required_method="" ;;
    esac
    if [ -n "$explicit_method" ]; then required_method=$explicit_method; fi
    if [ "$class_ref" = "com.ekkus.offlineytplayer.ui.MainActivityStartupOrderingInstrumentedTest" ]; then
        python3 scripts/assert_android_smoke_execution.py \
            --results-dir "$report_dir" --required-class "$class_ref" \
            --required-test "$class_ref#reconcileRunsBeforeEveryLibraryAndDownloadsRead" \
            --required-test "$class_ref#failedReconcileResultYieldsNoLibraryOrDownloadsReads" \
            --required-test "$class_ref#throwingReconcileYieldsNoLibraryOrDownloadsReads"
    elif [ -n "$required_method" ]; then
        python3 scripts/assert_android_smoke_execution.py \
            --results-dir "$report_dir" --required-class "$class_ref" \
            --required-test "$class_ref#$required_method"
    else
        python3 scripts/assert_android_smoke_execution.py \
            --results-dir "$report_dir" --required-class "$class_ref"
    fi
done
