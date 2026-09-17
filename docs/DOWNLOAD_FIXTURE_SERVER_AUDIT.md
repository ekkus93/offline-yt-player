# OYP-506 Download Fixture Server Audit

OYP-506 requires deterministic local HTTP qualification for interruption, range resume, timeout, disconnect, incorrect content length, and retry behavior. The repository now covers the complete matrix without live external network dependencies.

## Deterministic fixture boundary

The download tests bind loopback-only ephemeral ports and serve controlled HTTP responses from in-process Rust test threads. `core/src/download.rs` contains the reusable `tiny_http` fixture used for successful transfer and validator-backed range resume. `core/tests/download_adversarial.rs`, `core/tests/download_disconnect_matrix.rs`, and `core/tests/download_fixture_failures.rs` use raw loopback HTTP fixtures for protocol faults and timing-sensitive interruption cases.

## Requirement matrix

- **Interruption:** `interrupted_validated_stream_retains_resumable_partial_without_promotion` and `cooperative_cancel_interrupts_a_slow_body_without_promoting_it` prove cancellation cannot promote incomplete content and that the configured retained partial survives for later continuation.
- **Range resume:** `resumes_existing_partial_only_with_matching_persisted_identity` proves an existing partial is appended only after validator identity and `Content-Range` agree with the persisted continuation state.
- **Timeout:** `request_timeout_is_bounded_and_retryable` proves a deliberately delayed fixture is bounded by request policy and classified as retryable network failure.
- **Disconnect:** `abrupt_disconnect_never_promotes_partial_content` and the disconnect matrix prove premature connection closure cannot produce a completed asset.
- **Incorrect content length:** `truncated_declared_body_never_completes` and `disconnect_with_incorrect_content_length_never_promotes_partial_content` exercise a declared body larger than bytes actually delivered and prove the final path is never promoted.
- **Retry:** `server_error_is_explicitly_retryable` qualifies retry classification at the HTTP boundary; `retry::tests::retries_retryable_failure_until_success`, `stops_after_bounded_attempt_count`, and `permanent_failure_is_not_retried` qualify the bounded retry orchestrator deterministically.

## Safety properties

All fixtures use `127.0.0.1` and deterministic payloads. No test depends on YouTube, DNS, Internet reachability, provider behavior, credentials, or wall-clock services. Failure-path assertions emphasize the durable invariant: incomplete or unverified bytes never appear at the final library path.

## Qualification evidence

PR #106 added complementary interruption coverage. Exact-head push CI `35214642093` and PR CI `35214900262` passed at `685ff8f1c9d7969871aefab97667977a425123c8`. After merge, exact-head master CI `35215157544` passed at `32457a5afa0d50a64d91770dccd8b277a66c5b6b`.

With that evidence, both OYP-506 checklist items are satisfied.
