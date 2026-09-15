# Contributing

Changes should preserve the architecture and UX rules in `docs/OFFLINE_YT_PLAYER_SPEC.md`.

Before proposing a change, run the commands in `docs/BUILDING.md`. Rust code must format cleanly, pass Clippy with warnings denied, and pass tests. Android changes must pass lint, JVM tests, and debug assembly.

Keep provider-specific extraction code behind the source adapter boundary. Keep Android lifecycle, presentation, notification, and Media3 code out of the portable Rust domain layer. The Android application is portrait-only; do not add landscape resources or controls that require horizontal scrolling.
