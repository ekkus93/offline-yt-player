# RMD-1802 — Host Wi-Fi metered E2E failure diagnostics (2026-10-13)

## Exact failure evidence

On exact master `fb905a98d0df61909ac2b98614e6f82ba5c4bb64`, Android real-network E2E run 38387651853 completed its first OS connectivity-loss/resume instrumentation successfully (`OK (1 test)`), then applied and reset the Wi-Fi metered-network probe. The second host-controlled Wi-Fi-only instrumentation process exited before writing `wifi-ready`.

The host script's `wait_signal` helper unconditionally printed `rmd1506-os-network.log` on failure, even when waiting for the second Wi-Fi-metered test. Consequently the CI log repeated the *passing first test* rather than exposing the second test's assertion or crash. The observed failure does not prove the root cause of the second test.

## Correction

`scripts/run_android_os_network_e2e.sh` now accepts an optional log path in `wait_signal`. All three Wi-Fi-metered phase markers pass the second instrumentation log. A future failure will show the correct assertion and avoid misdiagnosing a passed first phase as the failing second phase.

## Qualification

This is a CI observability correction only. Do not count it as Wi-Fi metered acceptance or RMD-1802/1803/1805 closeout. Re-run exact-head Android real-network E2E and investigate the second instrumentation output if it still fails.
