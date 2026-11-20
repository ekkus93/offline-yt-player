#!/usr/bin/env bash
# Separate API-compatibility instrumentation invocations to ensure every
# required class executes; Android Gradle Plugin only used the first class
# from a comma-delimited -Pandroid.testInstrumentationRunnerArguments.class.
set -euo pipefail
: "${EXPECTED_ANDROID_API:?expected Android API version must be configured}"
actual_api="$(adb shell getprop ro.build.version.sdk | tr -d '\r\n')"
if [[ "$actual_api" != "$EXPECTED_ANDROID_API" ]]; then
  echo "API_COMPATIBILITY_WRONG_DEVICE expected=$EXPECTED_ANDROID_API actual=$actual_api" >&2
  exit 1
fi
dir=app/build/reports/apiCompatibility
mkdir -p "$dir"
printf 'api=%s\n' "$actual_api" > "$dir/executed.txt"

classes=(
  "com.ekkus.offlineytplayer.AndroidRuntimeSmokeTest"
  "com.ekkus.offlineytplayer.coregateway.GeneratedUniffiSourceGatewaySmokeTest"
  "com.ekkus.offlineytplayer.downloads.DownloadForegroundTimeoutInstrumentedTest"
  "com.ekkus.offlineytplayer.downloads.NotificationPermissionStateInstrumentationTest"
)
for klass in "${classes[@]}"; do
  name="${klass##*.}"
  modes=(standard)
  # Android 13+ permission revocation can kill the app process. Change it
  # between separate instrumentation invocations, never from the test thread.
  if (( actual_api >= 33 )) && [[ "$name" == NotificationPermissionStateInstrumentationTest ]]; then
    modes=(denied granted)
  fi
  for mode in "${modes[@]}"; do
    args_gradle=()
    if [[ "$mode" != standard ]]; then
      if [[ "$mode" == denied ]]; then
        adb shell pm revoke com.ekkus.offlineytplayer android.permission.POST_NOTIFICATIONS
      else
        adb shell pm grant com.ekkus.offlineytplayer android.permission.POST_NOTIFICATIONS
      fi
      args_gradle+=("-Pandroid.testInstrumentationRunnerArguments.expectedNotificationPermission=$mode")
    fi
    echo "API_COMPATIBILITY_RUNNING api=$actual_api class=$klass permission=$mode"
    # A prior invocation cannot satisfy this phase's required method XML.
    rm -rf app/build/outputs/androidTest-results/connected
    if ! ./gradlew --no-daemon :app:connectedDebugAndroidTest \
        "-Pandroid.testInstrumentationRunnerArguments.class=$klass" \
        "${args_gradle[@]}" > "$dir/$name-$mode.log" 2>&1; then
      tail -n 130 "$dir/$name-$mode.log" || true
      python3 scripts/summarize_android_test_failures.py || true
      exit 1
    fi
    args=(--results-dir app/build/outputs/androidTest-results/connected --required-class "$klass")
    case "$name" in
      AndroidRuntimeSmokeTest)
        args+=(--required-test "$klass#targetPackageIsInstalled" --required-test "$klass#runtimeSchedulingInputsAndRestrictedUidtFailureMatchActualApiTier" --required-test "$klass#bootBeforeUnlockCannotStartDataSyncOrOpenCredentialProtectedFiles") ;;
      GeneratedUniffiSourceGatewaySmokeTest)
        args+=(--required-test "$klass#repeatedNativeResolverOpenCloseAndPreCanceledRequestsDoNotLeakState") ;;
      DownloadForegroundTimeoutInstrumentedTest)
        args+=(--required-test "$klass#timeoutSequencePersistsThenStopsNativeWorkThenStopsForeground") ;;
      NotificationPermissionStateInstrumentationTest)
        args+=(--required-test "$klass#grantedAndDeniedOutcomesArePersistedWithoutInventingAnInitialState" --required-test "$klass#platformNotificationPermissionPhaseKeepsNativeDurableQueue") ;;
    esac
    python3 scripts/assert_android_smoke_execution.py "${args[@]}"
    printf 'class=%s permission=%s\n' "$name" "$mode" >> "$dir/executed.txt"
  done
done
echo "API_COMPATIBILITY_REQUIRED_CLASSES_OK api=$actual_api count=${#classes[@]}"
