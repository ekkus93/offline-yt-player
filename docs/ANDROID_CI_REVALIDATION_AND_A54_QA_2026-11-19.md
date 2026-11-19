# Android CI revalidation and A54 sideload handoff (2026-11-19)

## Exact-head CI qualification

The last engineering qualification after the November 18 CI remediation is
**`355a955dd5bfcc1eff15f1dead4923c2fe0ea56b`** on `master`. Ralph Bridge reported **eight of eight
GitHub Actions workflows passed**, with no skipped workflow accepted as a pass.

| Workflow | Outcome | Run |
| --- | --- | --- |
| CI | success | [44410812188](https://github.com/ekkus93/offline-yt-player/actions/runs/44410812188) |
| Android smoke | success | [44410812148](https://github.com/ekkus93/offline-yt-player/actions/runs/44410812148) |
| Android FGS timeout | success | [44410812106](https://github.com/ekkus93/offline-yt-player/actions/runs/44410812106) |
| Supply chain | success | [44410812076](https://github.com/ekkus93/offline-yt-player/actions/runs/44410812076) |
| CI evidence | success | [44410812065](https://github.com/ekkus93/offline-yt-player/actions/runs/44410812065) |
| Deterministic E2E fixture | success | [44410812000](https://github.com/ekkus93/offline-yt-player/actions/runs/44410812000) |
| Android cold start | success | [44410812150](https://github.com/ekkus93/offline-yt-player/actions/runs/44410812150) |
| Android real network E2E | success | [44410812056](https://github.com/ekkus93/offline-yt-player/actions/runs/44410812056) |

The Android API 29 smoke log explicitly recorded execution and required-method
checks for `GeneratedUniffiSourceGatewaySmokeTest` and both
`AddResolverAvailabilityInstrumentedTest` cases:
`validYouTubeUrlCanShowUnavailableResolverError` and
`userUrlParametersAreCanonicalizedBeforeAnalysis`. The smoke workflow passed
after the Kotlin assertion was corrected to permit two visible occurrences of
the same resolver-startup diagnostic. Twelve screenshot/golden variants were
also verified in the prior diagnostic run.

The security upgrade uses `rusqlite 0.41.1` and `libsqlite3-sys 0.39.1`;
the sole maintenance advisory exception is documented in
`docs/SUPPLY_CHAIN_EXCEPTION_BASIC_TOML_2026-11-18.md`. It does **not**
authorize an external release.

## Temporary debug APK for hardware QA

This documentation-only follow-up commit uses the existing
`[request-debug-apk]` commit marker. The `CI` workflow's
`Android lint unit build` job must produce
`offline-yt-player-debug-<exact-commit-sha>` if its checks pass.
The artifact retention period is **three days**, so users may need a fresh
requested build later.

This follow-up commit has **its own SHA** and must obtain its own exact-head
CI result. Passing validation on the preceding SHA does not automatically
qualify the APK artifact.

## Remaining Samsung Galaxy A54 verification

1. Install the debug APK produced from this exact commit, checking Android
   signing-key compatibility and preserving local app data if necessary.
2. In **Settings > About > Source revision**, confirm the revision matches the
   artifact's GitHub commit SHA.
3. Reproduce the original report both after a clean startup and after
   stopping/restarting the app: enter
   `https://youtu.be/AACwyjlk8ko` and
   `https://youtu.be/AACwyjlk8ko?is=8sN66gt7NLpTjRGc`.
4. Verify **Analyze** can be selected. If the resolver cannot initialize,
   verify the UI shows its actionable startup diagnostic rather than leaving
   Analyze silently disabled.
5. When source-service terms and connectivity permit, exercise the full
   Analyze → select format → queue/download → Library → offline playback
   path; capture sanitized diagnostic output for any failure.
6. Record device-specific behavior (Android version, battery management,
   downloads/background execution). Emulator success does not imply
   Samsung-specific acceptance.

The app remains an internal engineering build pending physical-device
acceptance and the separate human legal/provider-policy/licensing review.
