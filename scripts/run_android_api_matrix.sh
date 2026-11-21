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
    echo "API_COMPATIBILITY_RUNNING api=$actual_api class=$klass permission=$mode"
    if [[ "$mode" == standard ]]; then
      # Gradle owns APK installation and emits exact-method JUnit XML for normal tests.
      rm -rf app/build/outputs/androidTest-results/connected
      if ! ./gradlew --no-daemon :app:connectedDebugAndroidTest \
          "-Pandroid.testInstrumentationRunnerArguments.class=$klass" > "$dir/$name-$mode.log" 2>&1; then
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
          args+=(--required-test "$klass#timeoutSequencePersistsThenStopsNativeWorkThenStopsForeground" --required-test "$klass#duplicateTimeoutAndTeardownCallsStopNativeOnceAndRecreatedOwnerIsIndependent") ;;
        NotificationPermissionStateInstrumentationTest)
          args+=(--required-test "$klass#grantedAndDeniedOutcomesArePersistedWithoutInventingAnInitialState" --required-test "$klass#platformNotificationPermissionPhaseKeepsNativeDurableQueue") ;;
      esac
      python3 scripts/assert_android_smoke_execution.py "${args[@]}"
    else
      # connectedDebugAndroidTest reinstalls the app on every invocation and
      # resets the runtime POST_NOTIFICATIONS grant. The APKs were already
      # installed by the three successful standard invocations above. Toggle
      # the real package permission *after* the last install and use the
      # existing instrumentation component without reinstalling anything.
      if [[ "$mode" == denied ]]; then
        # AGP connectedDebugAndroidTest uninstalls its instrumentation APK after
        # every normal class. Install the already-built APKs ONCE, before the
        # first permission phase, then switch permission only via pm commands.
        app_apk="$(find app/build/outputs/apk/debug -type f -name '*.apk' | head -n 1)"
        test_apk="$(find app/build/outputs/apk/androidTest/debug -type f -name '*.apk' | head -n 1)"
        test -f "$app_apk" && test -f "$test_apk" || {
          echo "API_COMPATIBILITY_BUILT_APK_MISSING api=$actual_api"
          exit 1
        }
        adb install -r "$app_apk"
        adb install -r "$test_apk"
      fi
      runner="$(adb shell pm list instrumentation | tr -d '\r' |
        sed -n 's/^instrumentation:\([^ ]*\) (target=com\.ekkus\.offlineytplayer)$/\1/p' | head -n 1)"
      if [[ -z "$runner" ]]; then
        echo "API_COMPATIBILITY_INSTRUMENTATION_MISSING api=$actual_api" >&2
        exit 1
      fi
      if [[ "$mode" == denied ]]; then
        adb shell pm revoke com.ekkus.offlineytplayer android.permission.POST_NOTIFICATIONS
      else
        adb shell pm grant com.ekkus.offlineytplayer android.permission.POST_NOTIFICATIONS
      fi
      log="$dir/$name-$mode.log"
      adb shell am instrument -w -r -e class "$klass" \
        -e expectedNotificationPermission "$mode" "$runner" > "$log" 2>&1 || {
        tail -n 130 "$log"
        exit 1
      }
      # Exact method and terminal instrumented-run evidence are mandatory:
      # do not accept success merely because adb launched a test process.
      for test_name in grantedAndDeniedOutcomesArePersistedWithoutInventingAnInitialState platformNotificationPermissionPhaseKeepsNativeDurableQueue; do
        grep -Fq "INSTRUMENTATION_STATUS: test=$test_name" "$log" || {
          echo "API_COMPATIBILITY_MISSING_TEST api=$actual_api permission=$mode test=$test_name"
          tail -n 130 "$log"
          exit 1
        }
      done
      grep -Fq 'INSTRUMENTATION_CODE: -1' "$log" || {
        echo "API_COMPATIBILITY_PERMISSION_RUN_FAILED api=$actual_api permission=$mode"
        tail -n 130 "$log"
        exit 1
      }
      if grep -Eq 'FAILURES!!!|INSTRUMENTATION_FAILED|INSTRUMENTATION_STATUS_CODE: -2|INSTRUMENTATION_CODE: 0' "$log"; then
        echo "API_COMPATIBILITY_PERMISSION_FAILURE api=$actual_api permission=$mode"
        tail -n 130 "$log"
        exit 1
      fi
    fi
    printf 'class=%s permission=%s\n' "$name" "$mode" >> "$dir/executed.txt"
  done
done
echo "API_COMPATIBILITY_REQUIRED_CLASSES_OK api=$actual_api count=${#classes[@]}"
