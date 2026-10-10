#!/usr/bin/env bash
# Sourceable host helper: Android's NetworkPolicyManagerShellCommand can
# return -1 after successfully applying set metered-network, yielding adb 255.
# Qualification must inspect the actual persisted SSID override instead.
android_wifi_metered_set() {
    local ssid="$1" state="$2" command_log="$3" state_log="$4" i
    # Do not let the exit status alone decide whether the override worked.
    adb shell cmd netpolicy set metered-network "$ssid" "$state" > "$command_log" 2>&1 || true
    for ((i=0; i<20; i++)); do
        adb shell cmd netpolicy list wifi-networks > "$state_log" 2>&1 || true
        if grep -Fxq -- "$ssid;$state" "$state_log"; then
            return 0
        fi
        sleep 0.25
    done
    return 1
}
