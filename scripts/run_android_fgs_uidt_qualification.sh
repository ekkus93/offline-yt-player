#!/usr/bin/env bash
# Exact-head API-35 FGS/UIDT instrumentation. Only retry a Gradle invocation
# when its XML proves the emulator was not recognized as a compatible device.
# Never turn an executed failing assertion into a passing CI result.
set -euo pipefail

wait_for_api35() {
  local attempt sdk boot package activity
  for attempt in $(seq 1 60); do
    sdk="$(timeout 8s adb shell getprop ro.build.version.sdk 2>/dev/null | tr -d '\r' || true)"
    boot="$(timeout 8s adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)"
    package="$(timeout 8s adb shell service check package 2>/dev/null || true)"
    activity="$(timeout 8s adb shell service check activity 2>/dev/null || true)"
    if [[ "$sdk" == "35" && "$boot" == "1" &&
          "$package" == *"Service package: found"* &&
          "$activity" == *"Service activity: found"* ]]; then
      # The Gradle Android test runner fetches properties through ddmlib.
      # Require repeated successful ADB property reads, not just boot_completed.
      sleep 3
      if [[ "$(timeout 8s adb shell getprop ro.build.version.sdk 2>/dev/null | tr -d '\r' || true)" == "35" ]]; then
        return 0
      fi
    fi
    echo "API 35 emulator not yet stable (attempt $attempt/60, sdk=$sdk, boot=$boot)"
    sleep 5
  done
  echo "API 35 emulator failed readiness checks" >&2
  return 1
}

run_class() {
  local klass="$1" attempt
  for attempt in 1 2; do
    wait_for_api35
    rm -rf app/build/outputs/androidTest-results/connected
    if ./gradlew --no-daemon connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=$klass"; then
      return 0
    fi
    # A device that became unresponsive between ADB preflight and Gradle
    # enumerates as "Unknown API Level" with zero compatible devices.
    # This is infrastructure failure, not an Android assertion failure.
    if grep -R -Fq "No compatible devices connected" app/build/outputs/androidTest-results/connected 2>/dev/null; then
      if [[ "$attempt" -lt 2 ]]; then
        echo "Retrying only emulator/ddmlib no-compatible-device failure"
        sleep 10
        continue
      fi
    fi
    echo "Instrumentation failed for $klass (no further retry)" >&2
    return 1
  done
}

run_class com.ekkus.offlineytplayer.downloads.DownloadForegroundTimeoutAdbInstrumentedTest
run_class com.ekkus.offlineytplayer.downloads.DownloadUidtExecutionInstrumentedTest
python3 scripts/assert_android_smoke_execution.py \
  --results-dir app/build/outputs/androidTest-results/connected \
  --required-class com.ekkus.offlineytplayer.downloads.DownloadUidtExecutionInstrumentedTest \
  --required-test com.ekkus.offlineytplayer.downloads.DownloadUidtExecutionInstrumentedTest#api35JobSchedulerStopHaltsNativeAndManualRetryCompletes
