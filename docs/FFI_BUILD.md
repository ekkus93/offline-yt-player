# Rust/Kotlin FFI build and qualification

Offline YT Player uses UniFFI 0.31 for the portable Rust/Kotlin boundary. The Rust crate is built as both an ordinary Rust library and a `cdylib`.

## Generate Kotlin bindings on a development host

```bash
cargo build -p offline-yt-core
cargo run -p uniffi-bindgen -- \
  generate target/debug/liboffline_yt_core.so \
  --language kotlin \
  --out-dir target/uniffi-kotlin
```

The generated package is `com.ekkus.offlineytplayer.core`; Android-specific UniFFI generation is enabled in `core/uniffi.toml`.

## Representative Android ABI build

CI qualifies the portable core for `arm64-v8a` (`aarch64-linux-android`) against Android NDK r30 (`30.0.16248370`) and the application minimum API 26. The equivalent Linux-host build is:

```bash
sdkmanager "ndk;30.0.16248370"
rustup target add aarch64-linux-android
export ANDROID_NDK_HOME="$ANDROID_SDK_ROOT/ndk/30.0.16248370"
export CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android26-clang"
export CC_aarch64_linux_android="$CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER"
export AR_aarch64_linux_android="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-ar"
cargo build -p offline-yt-core --target aarch64-linux-android
```

The resulting library is `target/aarch64-linux-android/debug/liboffline_yt_core.so`.

This milestone proves binding generation and a representative Android ABI build. Packaging generated Kotlin and native libraries into release APK/AAB variants remains part of the later full Android/FFI integration gate.
