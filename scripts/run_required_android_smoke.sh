#!/bin/sh
# Execute mandatory RMD-1401a tests in separate fresh instrumentation invocations.
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
    com.ekkus.offlineytplayer.downloads.Rmd1506ConnectivityE2EInstrumentedTest \
    com.ekkus.offlineytplayer.downloads.Rmd1507NotificationControlInstrumentedTest \
    com.ekkus.offlineytplayer.ui.Rmd1802DownloadsControlThreadingInstrumentedTest
do
    class_name=${required_class##*.}
    report_dir="app/build/reports/androidSmokeLogs/required-${class_name}-results"
    log="app/build/reports/androidSmokeLogs/required-${class_name}.log"

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
        *) required_method="" ;;
    esac
    if [ -n "$required_method" ]; then
        python3 scripts/assert_android_smoke_execution.py \
            --results-dir "$report_dir" --required-class "$required_class" \
            --required-test "$required_class#$required_method"
    else
        python3 scripts/assert_android_smoke_execution.py \
            --results-dir "$report_dir" --required-class "$required_class"
    fi
done
