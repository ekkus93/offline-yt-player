#!/usr/bin/env sh
# Text-only Gradle bootstrap wrapper. It honors the pinned distribution in
# gradle/wrapper/gradle-wrapper.properties and verifies the published SHA-256.
set -eu

APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
PROPS="$APP_HOME/gradle/wrapper/gradle-wrapper.properties"
GRADLE_VERSION=9.6.0
GRADLE_SHA256=bbaeb2fef8710818cf0e261201dab964c572f92b942812df0c3620d62a529a01
GRADLE_USER_HOME=${GRADLE_USER_HOME:-"$HOME/.gradle"}
DIST_ROOT="$GRADLE_USER_HOME/wrapper/dists/offline-yt-player-$GRADLE_VERSION"
DIST_DIR="$DIST_ROOT/gradle-$GRADLE_VERSION"
ZIP="$DIST_ROOT/gradle-$GRADLE_VERSION-bin.zip"

if [ ! -f "$PROPS" ]; then
    echo "Missing $PROPS" >&2
    exit 2
fi

if [ ! -x "$DIST_DIR/bin/gradle" ]; then
    mkdir -p "$DIST_ROOT"
    if [ ! -f "$ZIP" ]; then
        URL="https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip"
        if command -v curl >/dev/null 2>&1; then
            curl --fail --location --retry 3 --connect-timeout 15 "$URL" --output "$ZIP"
        elif command -v wget >/dev/null 2>&1; then
            wget --tries=3 --timeout=15 --output-document="$ZIP" "$URL"
        else
            echo "curl or wget is required to bootstrap Gradle" >&2
            exit 2
        fi
    fi
    printf '%s  %s\n' "$GRADLE_SHA256" "$ZIP" | sha256sum --check --status || {
        rm -f "$ZIP"
        echo "Gradle distribution checksum mismatch" >&2
        exit 3
    }
    rm -rf "$DIST_DIR"
    unzip -q "$ZIP" -d "$DIST_ROOT"
fi

exec "$DIST_DIR/bin/gradle" -p "$APP_HOME" "$@"
