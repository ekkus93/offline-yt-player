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
METERED_SSID=""
cleanup() {
    if [[ -n "$METERED_SSID" ]]; then
        adb shell cmd netpolicy set metered-network "$METERED_SSID" false >/dev/null 2>&1 || true
    fi
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

# Exercise a second real OS transition, this time keeping the Wi-Fi radio on
# and switching Android's network cost classification for the connected SSID.
# A device with no discoverable saved Wi-Fi SSID cannot qualify this additional
# case; retain an explicit artifact note rather than inventing success.
adb shell cmd netpolicy list wifi-networks > "$DIR/wifi-networks-before.txt" 2>&1 || true
# Android may report a default-unmetered Wi-Fi network as ;none,
# meaning no explicit metered override; ;false is not the only eligible case.
# Trim optional quotes as required by cmd netpolicy set metered-network.
cat "$DIR/wifi-networks-before.txt"
METERED_SSID=$(sed -n -E '/;(false|none)[[:space:]]*$/ { s/^[[:space:]]*//; s/;(false|none)[[:space:]]*$//; p; q; }' "$DIR/wifi-networks-before.txt" | tr -d '"')
if [[ -z "$METERED_SSID" ]]; then
    echo "WIFI_ONLY_METERED_UNAVAILABLE: no saved Wi-Fi SSID with false/none policy" | tee "$DIR/wifi-only-metered-result.txt"
    exit 0
fi
echo "WIFI_ONLY_METERED_SSID_FOUND: $METERED_SSID" | tee "$DIR/wifi-only-metered-result.txt"
# A ;none entry is already the default unmetered state. Android 10
# emulators can reject the redundant explicit false override with adb 255.
# Do not fail the host lane before attempting the supported metered transition.
METERED_INITIAL_STATE=$(sed -n -E '/;(false|none)[[:space:]]*$/ { s/^.*;(false|none)[[:space:]]*$/\1/; p; q; }' "$DIR/wifi-networks-before.txt")
if [[ "$METERED_INITIAL_STATE" == "false" ]]; then
    if ! adb shell cmd netpolicy set metered-network "$METERED_SSID" false > "$DIR/wifi-unmetered-precondition.log" 2>&1; then
        echo "WIFI_ONLY_METERED_UNAVAILABLE: emulator rejected unmetered precondition" | tee -a "$DIR/wifi-only-metered-result.txt"
        cat "$DIR/wifi-unmetered-precondition.log"
        exit 0
    fi
else
    echo "WIFI_ONLY_METERED_DEFAULT_UNMETERED: explicit false override unnecessary" | tee -a "$DIR/wifi-only-metered-result.txt"
fi
# Probe the actual emulator netpolicy control before starting the second worker.
# Some API-29 images list a saved SSID but reject metered overrides (adb
# exit 255). In that case the already-qualified OS network-loss lane remains
# successful, while metered acceptance is explicitly UNAVAILABLE, not qualified.
if ! adb shell cmd netpolicy set metered-network "$METERED_SSID" true > "$DIR/wifi-metered-control-probe.log" 2>&1; then
    # The isolated AOSP emulator may permit root adbd even though its shell
    # identity cannot modify network policy. Try once without weakening the
    # actual callback/worker qualification. Never do this on a user device.
    echo "WIFI_ONLY_METERED_SHELL_DENIED: probing ephemeral CI emulator adb-root access" | tee -a "$DIR/wifi-only-metered-result.txt"
    adb root > "$DIR/wifi-metered-adb-root.log" 2>&1 || true
    if ! timeout 20 adb wait-for-device > "$DIR/wifi-metered-adb-wait.log" 2>&1; then
        echo "WIFI_ONLY_METERED_ADB_ERROR: emulator went offline during root probe" | tee -a "$DIR/wifi-only-metered-result.txt"
        exit 1
    fi
    ROOT_UID=$(adb shell id -u 2>/dev/null | tr -d '\r' || true)
    if [[ "$ROOT_UID" != "0" ]] || ! adb shell cmd netpolicy set metered-network "$METERED_SSID" true > "$DIR/wifi-metered-root-probe.log" 2>&1; then
        echo "WIFI_ONLY_METERED_UNAVAILABLE: metered override rejected after optional CI emulator root probe" | tee -a "$DIR/wifi-only-metered-result.txt"
        cat "$DIR/wifi-metered-control-probe.log" "$DIR/wifi-metered-adb-root.log"
        if [[ -f "$DIR/wifi-metered-root-probe.log" ]]; then cat "$DIR/wifi-metered-root-probe.log"; fi
        exit 0
    fi
    echo "WIFI_ONLY_METERED_ADB_ROOT_SUPPORTED: metered control probe applied" | tee -a "$DIR/wifi-only-metered-result.txt"
fi
adb shell cmd netpolicy list wifi-networks > "$DIR/wifi-metered-control-state.txt" 2>&1 || true
cat "$DIR/wifi-metered-control-state.txt"
if ! grep -Fq "$METERED_SSID;true" "$DIR/wifi-metered-control-state.txt"; then
    echo "WIFI_ONLY_METERED_UNAVAILABLE: emulator did not apply a metered SSID policy" | tee -a "$DIR/wifi-only-metered-result.txt"
    exit 0
fi
if ! adb shell cmd netpolicy set metered-network "$METERED_SSID" false > "$DIR/wifi-metered-control-reset.log" 2>&1; then
    echo "WIFI_ONLY_METERED_UNAVAILABLE: emulator could not reset metered probe" | tee -a "$DIR/wifi-only-metered-result.txt"
    cat "$DIR/wifi-metered-control-reset.log"
    exit 0
fi
sleep 3
adb shell am instrument -w -e rmdHostWifiMetered true \
    -e class 'com.ekkus.offlineytplayer.downloads.Rmd1506ConnectivityE2EInstrumentedTest#hostDrivenWifiOnlyMeteredWifiPausesAndUnmeteredWifiResumes' \
    "$INSTRUMENTATION" > "$DIR/rmd1506-wifi-metered.log" 2>&1 &
INST_PID=$!
wait_signal wifi-ready
adb shell cmd netpolicy set metered-network "$METERED_SSID" true
adb shell cmd netpolicy list wifi-networks > "$DIR/wifi-networks-metered.txt"
wait_signal wifi-paused
adb shell cmd netpolicy set metered-network "$METERED_SSID" false
wait_signal wifi-complete
wait "$INST_PID"
INST_PID=""
cat "$DIR/rmd1506-wifi-metered.log"
grep -q 'OK (1 test)' "$DIR/rmd1506-wifi-metered.log" || {
    echo "Wi-Fi-only/metered acceptance must execute a non-skipped test"
    exit 1
}
echo "WIFI_ONLY_METERED_QUALIFIED" | tee -a "$DIR/wifi-only-metered-result.txt"
