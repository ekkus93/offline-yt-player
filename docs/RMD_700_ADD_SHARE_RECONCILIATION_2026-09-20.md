# RMD-700 Add/Share workflow reconciliation — 2026-09-20

This audit records production evidence and remaining gaps without auto-checking the detailed remediation TODO.

## RMD-701 — Paste

Production `AddScreen` reads clipboard text, rejects missing/blank text with a user-visible status, bounds accepted text to 4096 characters, writes it into the URL field, clears stale setup state, and requires Analyze before treating it as resolved media. `AddScreenPolicyTest` covers production-source policy but the detailed requirement for behavioral Compose/instrumentation coverage remains open until RMD-1401/1402 infrastructure executes it on Android.

Disposition: the first three RMD-701 behavioral requirements are implemented; Android behavioral qualification remains open.

## RMD-702 — Analyze

Production Add no longer fabricates preview metadata. It calls `AppSourceAnalysisGateway` off the main thread; `GeneratedUniffiSourceAnalysisGateway` owns the generated UniFFI source boundary and maps structured core/source diagnostics. Resolved title, duration, quality and estimated bytes feed `DownloadSetupState`. RMD-603 cancellation work also cancels/ignores superseded requests.

Disposition: production source resolution is wired. Shared URL-policy unification and richer typed presentation of unsupported/network/source-changed states remain tied to RMD-603/RMD-1301 and should stay fail-closed until those paths are behaviorally qualified.

## RMD-703 — Download Setup

Resolved production title, duration, quality label and optional estimated bytes are displayed. However the production Download callback still reports that scheduling is tracked by RMD-500/RMD-703 instead of creating/scheduling durable work. The current setup state also does not expose thumbnail/source identity or a selectable curated quality list, and selected setup options are not durably persisted.

Disposition: RMD-703 remains release-blocking implementation work.

## RMD-704 — Advanced Options

The screen remains presentation-only: audio/subtitle/container values are static/default controls and are not populated from the resolved source analysis or applied to durable setup state.

Disposition: RMD-704 remains open.

## RMD-705 — Android Share

`MainActivity` parses incoming `ACTION_SEND` text through `ShareInput`, starts the app on Add when a shared URL is accepted, and feeds that value into the same production Analyze UI. The remaining detailed requirements—consistent hardened URL contract, multiple/no-URL handling proof, and behavioral back-stack/instrumentation coverage—remain open.

## Next implementation priority

The first functional release blocker in this milestone is RMD-703: create a durable download job from the resolved source/setup selection and schedule it through the already-established durable orchestration/runtime boundary. RMD-704 must then carry real source-derived track/subtitle/container choices into that job. RMD-701/RMD-705 Android behavioral coverage should be closed with the RMD-1401 instrumentation lane rather than policy-only tests.
