# RMD-1802 — Provider-resolved format-label presentation (2026-10-13)

## Finding

After a successful source analysis, `AppShell.kt` presented provider-controlled quality, audio, subtitle, and container labels directly in Download Setup, Advanced Options, and scheduling status. Labels from external metadata can include signed URLs or credential-bearing headers, and may be unbounded. Even though media identity and canonical source URL had already been redacted in the preview, these separate fields bypassed the shared presentation policy.

## Correction

Add `AddWorkflowPolicy.displayLabel` and `displaySummary` to redact headers and URL query/userinfo/fragment and bound labels when displayed. Apply at all format-label presentation points, including scheduling completion/status text. **Do not sanitize data-model keys or selection IDs:** the original strings still index their selected quality, subtitle and audio choices; sanitized labels are display-only. Limit long summary strings to eight choices with a truthful additional-count indication. This reduces excessive UI text without removing selectable options.

## Qualification

Host JVM regression `AddWorkflowPolicyTest.displayFormattingRedactsSignedUrlsAndBoundsUntrustedLabelsWithoutChangingMapping` verifies signed-token redaction and bounds. Packaged Compose regression `ProductionComposeBehaviorTest.downloadSetupAndAdvancedOptionsRedactProviderLabelsWithoutChangingSelectionIds` drives actual Add/Analyze, rendered setup, advanced options, and Download, verifying that credential markers do not appear while the original executable quality-choice ID is passed to the injected gateway. Existing source/gateway binding tests remain authoritative for provider ID conversion.

This is implementation and test source evidence only. Require the full exact-head standard CI matrix including Android smoke and the other seven required workflows, continued independent Rust/Android production audit and final checklist reconciliation before RMD-1802/1803/1805 or global closeout. Human YouTube/service-policy/legal approval remains a separate external gate.
