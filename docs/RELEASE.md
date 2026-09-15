# Release engineering

Offline YT Player uses semantic `MAJOR.MINOR.PATCH` version names for release builds. Development builds use the same base version with a `-dev` suffix. Android `versionCode` is a monotonically increasing integer supplied by the release workflow.

## Reproducible metadata

The Android build accepts three environment inputs:

- `OYP_VERSION_NAME` — semantic version name; defaults to `0.1.0-dev`.
- `OYP_VERSION_CODE` — monotonically increasing Android version code; defaults to `1`.
- `GITHUB_SHA` — exact source revision embedded in `BuildConfig.SOURCE_REVISION`; CI supplies this automatically. Local builds deliberately report `local` when no revision is supplied rather than guessing from a mutable working tree.

`BuildMetadata` exposes the version and source revision for diagnostics/About UI and defines the canonical artifact basename `offline-yt-player-<version>-<12-char-revision>.apk`.

## Candidate build

From a clean checkout of the exact candidate SHA, set `OYP_VERSION_NAME` and `OYP_VERSION_CODE`, then run:

```sh
./gradlew --no-daemon :app:assembleRelease
```

A release candidate is not qualified merely because it assembles. The exact candidate SHA must pass the repository CI matrix and the resulting artifact must be renamed using the canonical `BuildMetadata` naming contract. Public distribution remains subject to the source-service policy/legal release gate documented by the project.
