#!/usr/bin/env bash
# RMD-1506 real OS network-transition E2E, deliberately outside normal smoke.
set -euo pipefail
mkdir -p app/build/reports/androidNetworkE2E
DIR=app/build/reports/androidNetworkE2E
./gradlew --no-daemon :app:assembleDebug :app:assembleDebugAndroidTest > "$DIR/build.log" 2>&1 || {
    tail -n 160 "$DIR/build.log"
    exit 1
}
APP_APK=$(find app/build/outputs/apk/debug -type f -name '*.apk' | head -n 1)
TEST_APK=$(find app/build/outputs/apk/androidTest/debug -type f -name '*.apk' | head -n 1)
adb install -r "$APP_APK"
adb install -r "$TEST_APK"
INSTRUMENTATION=$(adb shell pm list instrumentation | tr -d '\r' | sed -n 's/^instrumentation:\([^ ]*\) (target=com\.ekkus\.offlineytplayer)$/\1/p' | head -n 1)
test -n "$INSTRUMENTATION"
SIGNAL_DIR=/sdcard/Android/data/com.ekkus.offlineytplayer/files/rmd1506-host
LOG="$DIR/rmd1506-os-network.log"
restore_network() {
    adb shell settings put global airplane_mode_on 0 >/dev/null 2>&1 || true
    adb shell am broadcast -a android.intent.action.AIRPLANE_MODE --ez state false >/dev/null 2>&1 || true
    adb shell svc wifi enable >/dev/null 2>&1 || true
    adb shell svc data enable >/dev/null 2>&1 || true
}
INST_PID=""
cleanup() {
    restore_network
    if [[ -n "$INST_PID" ]]; then
        kill "$INST_PID" 2>/dev/null || true
    fi
}
trap cleanup EXIT
restore_network
sleep 4
adb shell am instrument -w -e rmdHostNetworkTransitions true \
    -e class 'com.ekkus.offlineytplayer.downloads.Rmd1506ConnectivityE2EInstrumentedTest#hostDrivenOsConnectivityLossPausesAndRestoresForegroundTransfer' \
    "$INSTRUMENTATION" > "$LOG" 2>&1 &
INST_PID=$!
wait_signal() {
    local marker="$1" i
    for ((i=0;i<180;i++)); do
        if adb shell "test -f '$SIGNAL_DIR/$marker'" >/dev/null 2>&1; then return 0; fi
        if ! kill -0 "$INST_PID" 2>/dev/null; then
            echo "Instrumentation ended before $marker marker"
            cat "$LOG"
            return 1
        fi
        sleep 0.5
    done
    echo "Timed out waiting for actual Android $marker transition"
    cat "$LOG"
    return 1
}
wait_signal ready
adb shell svc wifi disable
adb shell svc data disable
adb shell settings put global airplane_mode_on 1
adb shell am broadcast -a android.intent.action.AIRPLANE_MODE --ez state true >/dev/null 2>&1 || true
wait_signal paused
restore_network
wait_signal complete
wait "$INST_PID"
INST_PID=""
cat "$LOG"
grep -q 'OK (1 test)' "$LOG" || {
    echo "The OS-network qualification must run one non-skipped instrumented test."
    exit 1
}
