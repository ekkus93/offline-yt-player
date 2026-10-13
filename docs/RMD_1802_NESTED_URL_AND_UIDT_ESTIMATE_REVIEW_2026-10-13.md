# RMD-1802 — Nested URL and UIDT estimate independent review (2026-10-13)

## Findings and impact

1. `SourceMetadataPolicy.redactDiagnosticUrls` parsed the first URL in an untrusted metadata string and retained `uri.rawPath`. Java URI permits another `https://user:secret@host` sequence inside that path, so the previous sanitizer leaked embedded URL credentials in title/channel/description/quality/diagnostic presentation and exports.
2. `AndroidDownloadExecutionScheduler.scheduleUserInitiatedJob` passed arbitrary non-null `estimatedDownloadBytes` to `JobInfo.Builder.setEstimatedNetworkBytes`. Android expects nonnegative byte estimates or its `NETWORK_BYTES_UNKNOWN` sentinel; invalid negative estimates can throw rather than returning a failed scheduling result.

## Remediation

- Reject entire URLs when the parsed path contains a nested HTTP(S) scheme or credential-like `@` / encoded `%40`, retaining the existing ordinary-URL redaction behavior. Host-JVM `SourceMetadataPolicyTest.nestedUrlCredentialsInPathNeverReachMetadataOrDiagnostics` tests raw and encoded embedded credential strings across presentation fields.
- Normalize null and all negative estimated byte counts to `JobInfo.NETWORK_BYTES_UNKNOWN` before passing the estimate to JobInfo. `DownloadExecutionSchedulerPolicyTest.invalidEstimatedBytesBecomeFrameworkUnknownInsteadOfCrashingUidtScheduling` exercises null, negative, zero and maximum values.

## Qualification and closure

These tests are fast host-JVM proof and do not require Android emulator execution. They are not evidence of successful exact-head CI until the newly committed SHA's CI workflow succeeds. Android smoke and the other required matrix lanes remain independent closeout requirements. Do not mark RMD-1802, RMD-1803, RMD-1805 or global proof checkboxes complete from implementation alone. No external service/legal approval is implied.
