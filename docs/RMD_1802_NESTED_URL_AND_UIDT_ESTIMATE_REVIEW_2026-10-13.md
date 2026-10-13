# RMD-1802 — Nested URL and UIDT estimate independent review (2026-10-13)

## Findings and impact

1. `SourceMetadataPolicy.redactDiagnosticUrls` parsed the first URL in an untrusted metadata string and retained `uri.rawPath`. Java URI permits another `https://user:secret@host` sequence inside that path, so the previous sanitizer leaked embedded URL credentials in title/channel/description/quality/diagnostic presentation and exports.
2. `AndroidDownloadExecutionScheduler.scheduleUserInitiatedJob` passed arbitrary non-null `estimatedDownloadBytes` to `JobInfo.Builder.setEstimatedNetworkBytes`. Android expects nonnegative byte estimates or its `NETWORK_BYTES_UNKNOWN` sentinel; invalid negative estimates can throw rather than returning a failed scheduling result.

## Remediation

- Reject entire URLs when the parsed path contains a nested HTTP(S) scheme or credential-like `@` / encoded `%40`, retaining the existing ordinary-URL redaction behavior. Host-JVM `SourceMetadataPolicyTest.nestedUrlCredentialsInPathNeverReachMetadataOrDiagnostics` tests raw and encoded embedded credential strings across presentation fields.
- Normalize null and all negative estimated byte counts to `JobInfo.NETWORK_BYTES_UNKNOWN` before passing the estimate to JobInfo. `DownloadExecutionSchedulerPolicyTest.invalidEstimatedBytesBecomeFrameworkUnknownInsteadOfCrashingUidtScheduling` exercises null, negative, zero and maximum values.

## Qualification and closure

These tests are fast host-JVM proof and do not require Android emulator execution. They are not evidence of successful exact-head CI until the newly committed SHA's CI workflow succeeds. Android smoke and the other required matrix lanes remain independent closeout requirements. Do not mark RMD-1802, RMD-1803, RMD-1805 or global proof checkboxes complete from implementation alone. No external service/legal approval is implied.

## Rust core diagnostic parity addendum

The independent follow-up found the same credential-path risk in `core/src/security.rs::redact_url_token`: its existing raw nested-scheme check did not reject percent-encoded nested HTTP(S) URLs or a credential-like `user:secret@host` segment within an otherwise ordinary URL path. The Rust parser treats both as path data and would preserve those bytes in diagnostic output. The production redactor now fails closed for nested raw/encoded URL schemes and `@`/`%40` credential markers in the parsed path. `security::tests::encoded_nested_urls_and_path_credentials_fail_closed` covers both cases and preserves a benign ordinary URL path. This addition also requires fresh exact-head Rust/core and full CI qualification; the earlier Android commit cannot qualify it.

## Additional credential-bearing URL forms (2026-10-13; qualification pending)

A follow-up review found that the metadata/diagnostic URL matcher only recognized literal `https://` or `http://` prefixes. Protocol-relative URLs (such as `//user:token@cdn.example/media?sig=token`) and fully percent-encoded HTTP(S) URL strings could therefore pass through to title/channel/description/quality and diagnostic presentation with credential values intact. `SourceMetadataPolicy.urlPattern` now recognizes these forms. Protocol-relative URLs receive the existing URI userinfo/query redaction, and encoded HTTP(S) URL strings fail closed through the unsafe-path rule. Executable host-JVM `SourceMetadataPolicyTest.protocolRelativeAndEncodedUrlsNeverExposeCredentials` covers all presentation fields plus diagnostic output and preserves a benign protocol-relative path. This is implementation/regression evidence, not exact-head CI qualification. Keep RMD-1802/1803/1805/global closeout unchecked until a frozen head passes the required matrix and independent review is complete.

## Rust diagnostic URL-form parity (2026-10-13; exact-head CI pending)

`core/src/security.rs::redact_url_token` also recognized only literal HTTP(S) URL schemes, allowing signed protocol-relative URLs and fully encoded HTTP(S) URL strings to escape Rust diagnostics untouched. The matcher now recognizes both forms. Protocol-relative URLs are parsed against a temporary HTTPS base for credential/query stripping and rendered without that base; fully encoded HTTP(S) URL strings fail closed. Deterministic Rust test `security::tests::protocol_relative_and_encoded_signed_urls_never_leak_credentials` checks signed credential-bearing forms and an ordinary scheme-relative path. This remains unqualified until passing exact-head core/main and the complete required CI matrix. Engineering global closeout and human service/legal approval remain separate.

## Android raw metadata input-bounding follow-up (2026-10-13; exact-head qualification pending)

The production `SourceMetadataPolicy` previously applied whole-input `lineSequence().joinToString` and regular-expression URL/header sanitization **before** enforcing its small final display limits (128–4096 characters). Untrusted provider or FFI diagnostic strings could therefore trigger unbounded intermediate copying and scanning despite bounded UI output. All five public presentation functions now reject a raw input longer than 65,536 UTF-16 code units with their existing safe field fallback **before** scanning or copying. Ordinary-size values retain URL/header redaction and existing display bounds. `SourceMetadataPolicyTest.oversizedUntrustedTextFailsClosedBeforePresentationRedaction` checks overlong credential-bearing title/channel/description/quality/diagnostics plus normal-size signed URL behavior. This is an independent review correction with JVM regression coverage, **not** exact-head CI qualification or global closeout. RMD-1802/1803/1805 and external service/legal approval remain open.

## Rust untrusted diagnostic input resource bound (2026-10-13; exact-head qualification pending)

The core `redact_sensitive` boundary also previously allocated a full-size output buffer and scanned/split arbitrary-length untrusted provider/FFI diagnostics. Mirroring Android's input bound, `core/src/security.rs` now fails closed with `[REDACTED_OVERSIZE]` for messages exceeding 65,536 **UTF-8 bytes**, before tokenization or output allocation. It rejects the entire message rather than truncating through a signed URL or credential-bearing header. Deterministic regression `security::tests::oversized_untrusted_diagnostics_fail_closed_before_tokenization` covers an overlong signed URL and ordinary-size URL redaction. Full exact-head Rust and Android CI remains required, and RMD-1802/1803/1805 and the external approval gate remain open.
