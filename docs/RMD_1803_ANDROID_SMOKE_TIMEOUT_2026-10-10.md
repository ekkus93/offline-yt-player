# RMD-1803 — Android smoke exact-head timeout (2026-10-10)

Exact master `eddbc488206d9edf04be0ea125b704a7781834ba` passed seven of eight push workflows: CI `38110751303`, Supply chain `38110751240`, CI evidence `38110751262`, Deterministic E2E fixture `38110751301`, Android FGS timeout `38110751255`, Android cold start `38110751236`, and Android real-network E2E `38110751209`.

Android smoke run `38110751274` (API-29 job `114385621720`) was **cancelled at the workflow job's 30-minute limit**, not passed. The log records completed required runtime, Share, settings, connectivity, notification, downloads-threading, and 12 golden tests; the job was cancelled while the final `ProductionComposeLayoutAccessibilityTest` invocation was running. These intermediate successes do not establish full smoke qualification.

The current workflow `.github/workflows/android-smoke.yml` sets `timeout-minutes: 30` while executing base instrumentation, required focused instrumentation, golden screenshots, and layout/accessibility serially on one emulator. The next engineering change should either raise this bound enough to complete the existing matrix (for example 45 minutes) or split independent tests into parallel exact-head lanes without losing required-test assertions or evidence uploads. Run the entire lane on a single frozen SHA and require success before checking RMD-1803.

A one-line workflow correction was attempted but the Ralph Bridge source write was rejected by tool safety checks. No CI workflow change or passing Android smoke qualification is claimed. RMD-1802/RMD-1803 and final closeout remain open.

Follow-up (exact master `7fd44970dd716d127e3d47beaea6c9764227a63c`): Android smoke `38113523294` **passed** at 2026-10-11T05:27:27Z, as did the seven other required exact-head push workflows. The prior 30-minute cancellation was intermittent rather than a reproducible test failure. Raise the smoke job timeout to 45 minutes to retain the entire required serial test matrix with headroom; this workflow change requires its own passing exact-head run before RMD-1803 can be signed off. No RMD-1803 or RMD-1800 checkbox is changed by this evidence note.
