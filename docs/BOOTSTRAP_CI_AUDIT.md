# Bootstrap and baseline CI audit

This document qualifies OYP-001 through OYP-003 against the current repository state.

## OYP-001 — Repository structure

The repository has a Rust workspace at the root (`Cargo.toml`) with the portable `core/` crate and the pinned `tools/uniffi-bindgen/` utility, plus the Android `app/` Gradle module and `docs/` documentation boundary. Repository policy/support files include `.editorconfig`, `.gitignore`, a pending license decision represented by `LicenseRef-OYP-Pending`, and README bootstrap/build/test notes.

A clean checkout is documented in `README.md`. CI independently builds/tests the Rust workspace and lints/tests/builds the Android application, so the two required skeletons are continuously qualified.

## OYP-002 — Toolchain pins

The repository declares and CI uses Rust 1.98.1, JDK 17, Android platform 37.0/build-tools 37.0.0, and Android NDK 30.0.16248370. Android dependency/plugin versions are centralized in `gradle/libs.versions.toml`, including AGP 9.4.0, Kotlin 2.4.20, Compose BOM 2026.08.00, Activity Compose 1.13.0, and Media3 1.11.0. The checked-in Gradle wrapper is authoritative. README documents `minSdk 26`, `targetSdk 36`, and `compileSdk 37` and repeats the supported local toolchain values used by CI.

## OYP-003 — Baseline CI

`.github/workflows/ci.yml` runs on pushes to `master` and `ralph/**` and on pull requests to `master`. It contains:

- Rust formatting, Clippy with warnings denied, and workspace tests.
- Android lint, JVM unit tests, and debug assembly.
- A separate UniFFI/Kotlin-generation and representative Android ABI cross-build job.
- An exact-commit guard in every job that prints `GITHUB_SHA` and verifies `git rev-parse HEAD` equals it.
- Cargo and Gradle caches keyed from dependency/build metadata. The jobs still execute all correctness checks after cache restore, so correctness is not cache-dependent.

## Qualification rule

OYP-001 through OYP-003 may be reconciled in `docs/OFFLINE_YT_PLAYER_TODO.md` after this audit commit itself passes exact-head CI. The exact qualifying run ID and SHA should be recorded during TODO reconciliation rather than predicted here.
