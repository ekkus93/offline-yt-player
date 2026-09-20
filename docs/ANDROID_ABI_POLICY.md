# Android ABI policy

Offline YT Player v1 supports **arm64-v8a** Android devices. The corresponding Rust target is `aarch64-linux-android`.

`app/build.gradle.kts` is the source of truth for the Android ABI filter and packages the Rust `cdylib` from `target/aarch64-linux-android/debug/liboffline_yt_core.so` as `lib/arm64-v8a/liboffline_yt_core.so`.

CI builds that exact Rust target and fails before APK assembly if the native library is missing. It then inspects the assembled APK and fails unless `lib/arm64-v8a/liboffline_yt_core.so` is present. Adding another supported ABI therefore requires adding its Rust target/build step, packaging mapping, and APK-presence assertion in the same change.
