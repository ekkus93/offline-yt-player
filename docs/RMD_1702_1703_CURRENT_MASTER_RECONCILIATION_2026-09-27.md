# RMD-1702 / RMD-1703 Current-Master Reconciliation — 2026-09-27

## Scope

This document records current-master evidence for the canonical remediation TODO items:

- `RMD-1702 — Build and FFI docs`
- `RMD-1703 — Background execution docs`

It is supporting evidence only. The canonical completion state remains `docs/OFFLINE_YT_PLAYER_REMEDIATION_TODO_2026-09-17.md`.

## Candidate SHA

- Current master candidate: `c5fece100ca56c4cf4b30af8a306b02882e57594`
- Commit message: `docs: refresh Android build and background execution docs`

## Exact-head CI evidence

The following workflows passed on exact head `c5fece100ca56c4cf4b30af8a306b02882e57594`:

- CI: run `36337279640`
- Android smoke: run `36337279624`
- Android FGS timeout: run `36337279627`
- Supply chain: run `36337279632`

## RMD-1702 evidence

`docs/ANDROID_RUST_FFI_BUILD.md` documents:

- supported Android ABI build flow for `arm64-v8a` / `aarch64-linux-android` and `x86_64` / `x86_64-linux-android`;
- Gradle JNI packaging via generated Rust JNI library directories;
- reproducible UniFFI Kotlin generation and Gradle source-set integration;
- emulator/device setup prerequisites for local runtime validation;
- common native-loading and integration failure modes, including missing native libraries, ABI mismatch, stale generated bindings, and main-thread FFI misuse.

## RMD-1703 evidence

`docs/ANDROID_BACKGROUND_EXECUTION.md` documents:

- API 34+ user-initiated-data-transfer job scheduling path;
- API 26-33 foreground-service fallback path;
- notification permission, UIDT notification, and foreground-service notification behavior;
- boot, process-death, and startup reconciliation boundaries;
- Android 15+ `dataSync` foreground-service timeout and forbidden boot-start restrictions;
- the remaining RMD-1500, RMD-1601, and RMD-1803 qualification ownership.

## Reconciliation intent

RMD-1702 and RMD-1703 are eligible to be marked complete in the canonical remediation TODO once this supporting evidence and the exact-head workflow runs above are cited there. No external YouTube/service-policy/legal approval is implied by this document.
