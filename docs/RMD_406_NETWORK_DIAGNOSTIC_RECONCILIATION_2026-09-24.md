# RMD-406 — Network diagnostic redaction reconciliation

RMD-406 is implemented on current `master` (`d6e0243fac72da0e78a0ac847771b9c12937425a`). This note records implementation and qualification evidence without changing the canonical checklist state before this evidence commit itself is qualified and merged.

## Requirement mapping

- **Replace raw `reqwest::Error` user-facing text:** `core/src/download.rs::map_reqwest_error` maps timeout, connect, and other pre-response request failures to fixed structured `CoreError` messages instead of formatting the raw `reqwest::Error`. `remote_body_read_error` likewise emits fixed network/integrity messages for response-body failures.
- **Strip/redact URLs, query strings, signed parameters, tokens, and sensitive filesystem details:** network-facing error mapping does not echo request URLs or raw reqwest diagnostics. `core/src/security.rs::redact_sensitive` additionally redacts credential-bearing URL material, query strings/fragments, Authorization/Proxy-Authorization, Cookie/Set-Cookie, and API-key header values for diagnostic text that is deliberately sanitized.
- **Synthetic signed URL/secret tests:** `core/src/download.rs::tests::malformed_response_does_not_reflect_signed_url_or_tokens` injects signed-query/token markers into a malformed-response fixture and asserts that neither the fixed message nor query names leak. `core/tests/network_diagnostic_redaction.rs::download_connect_failure_does_not_reflect_signed_url_or_secret_markers` injects signed-query, token, cookie, and bearer markers through the production transfer path.
- **Logs/FFI diagnostics contain no injected markers:** the integration regression converts the production `CoreError` into `FfiError` and asserts both the rendered core diagnostic and FFI message contain neither the URL/query material nor any injected secret marker. `security::tests::sensitive_values_are_redacted` independently covers sanitization of credential URLs and sensitive headers.

## Historical implementation qualification

The RMD-406 implementation was qualified on exact head `2ef0571a5011a3cec74e9b9cbbab462e69d20d33` by push CI run `35418789777` and pull-request CI run `35419070245`, both successful. The same production error mapping, sanitization helper, malformed-response regression, and FFI redaction integration test are present on current master.

## Current-master qualification

The immediately preceding master `0ea81f8336a77ed233fb1153de727dd937da54bd` passed CI `35949623488`, Android smoke `35949623385`, and Android FGS-timeout `35949623359`. Current master `d6e0243fac72da0e78a0ac847771b9c12937425a` is undergoing post-merge qualification after the RMD-405 evidence merge. RMD-406 itself is deterministic Rust/core/FFI diagnostic behavior, so the Android qualification acceleration plan correctly keeps its primary proof in Rust tests rather than requiring emulator-only proof.

## Canonical TODO reconciliation rule

The four RMD-406 checkboxes in `docs/OFFLINE_YT_PLAYER_REMEDIATION_TODO_2026-09-17.md` may be checked only after this evidence note is qualified on its exact head and merged to `master`; the canonical TODO should then cite this note plus the exact merged SHA and CI runs.
