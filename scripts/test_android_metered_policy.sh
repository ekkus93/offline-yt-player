#!/usr/bin/env bash
# Host-only regression for Android's nonzero-but-applied netpolicy command.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "$ROOT/scripts/android_metered_network_policy.sh"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
export MOCK_METERED_STATE_FILE="$TMP/policy.txt"
export MOCK_METERED_APPLY=1
printf 'AndroidWifi;none\n' > "$MOCK_METERED_STATE_FILE"
cat > "$TMP/adb" <<'MOCK_ADB'
#!/usr/bin/env bash
set -euo pipefail
[[ "$1" == shell && "$2" == cmd && "$3" == netpolicy ]]
if [[ "$4" == list && "$5" == wifi-networks ]]; then
    cat "$MOCK_METERED_STATE_FILE"
elif [[ "$4" == set && "$5" == metered-network ]]; then
    if [[ "$MOCK_METERED_APPLY" == 1 ]]; then
        printf '%s;%s\n' "$6" "$7" > "$MOCK_METERED_STATE_FILE"
    fi
    # AOSP may return -1 even when the policy was applied.
    exit 255
else
    exit 2
fi
MOCK_ADB
chmod +x "$TMP/adb"
export PATH="$TMP:$PATH"
android_wifi_metered_set AndroidWifi true "$TMP/command.log" "$TMP/observed.txt"
grep -Fxq 'AndroidWifi;true' "$TMP/observed.txt"
android_wifi_metered_set AndroidWifi false "$TMP/command.log" "$TMP/observed.txt"
grep -Fxq 'AndroidWifi;false' "$TMP/observed.txt"
android_wifi_metered_set AndroidWifi none "$TMP/command.log" "$TMP/observed.txt"
grep -Fxq 'AndroidWifi;none' "$TMP/observed.txt"
export MOCK_METERED_APPLY=0
if android_wifi_metered_set AndroidWifi true "$TMP/command.log" "$TMP/observed.txt"; then
    echo "FAIL: false positive when netpolicy did not apply" >&2
    exit 1
fi
grep -Fxq 'AndroidWifi;none' "$TMP/observed.txt"
echo "Android metered policy host regression passed"
