# Android Analyze-button fix: sideload QA build (2026-10-24)

## Reason for this build

On a Samsung A54, the Add screen displayed a correctly formed
`https://youtu.be/AACwyjlk8ko` URL but permanently disabled Analyze after
the app restarted. A query-string variant with `?is=8sN66gt7NLpTjRGc`
showed the same symptom. The input policy already recognizes both.

## Implemented fix

- Analyze availability is independent of YouTube resolver startup so the
  user can see an actionable diagnostic when initialization fails.
- MainActivity publishes the resolver independently from the library and
  download startup paths.
- Native/UniFFI resolver initialization now probes a deliberately unsupported
  host without network I/O. A native failure is reported to Logcat and the UI.
- Unit tests cover both reported URL variants and Analyze readiness.
- GitHub Actions' API 29 Android smoke workflow requires the packaged
  UniFFI resolver check and the Add-screen regression tests; the smoke driver
  fails closed if any required method is not executed.

## QA acceptance

Use the new debug APK generated for this exact commit, rather than an older
successfully built APK. Verify that the Add screen enables Analyze after
entering a video URL on the A54. If native resolver initialization fails,
capture the sanitized in-app diagnostic and relevant `OfflineYTPlayer`
Logcat exception; do not assume a live YouTube provider URL is downloadable
just because the button becomes enabled.

This is an internal test/sideload artifact, not a release or external provider
policy approval.
