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
  echo "API_COMPATIBILITY_RUNNING api=$actual_api class=$klass"
  # Remove stale XML before each invocation so a previous class cannot
  # accidentally satisfy this invocation's required-class/method guard.
  rm -rf app/build/outputs/androidTest-results/connected
  if ! ./gradlew --no-daemon :app:connectedDebugAndroidTest \
      "-Pandroid.testInstrumentationRunnerArguments.class=$klass" \
      > "$dir/$name.log" 2>&1; then
    tail -n 130 "$dir/$name.log" || true
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
      args+=(--required-test "$klass#grantedAndDeniedOutcomesArePersistedWithoutInventingAnInitialState") ;;
  esac
  python3 scripts/assert_android_smoke_execution.py "${args[@]}"
  printf 'class=%s\n' "$name" >> "$dir/executed.txt"
done
echo "API_COMPATIBILITY_REQUIRED_CLASSES_OK api=$actual_api count=${#classes[@]}"
