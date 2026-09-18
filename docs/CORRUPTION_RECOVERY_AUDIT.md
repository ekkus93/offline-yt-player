# OYP-1804 Corruption and recovery audit

OYP-1804 is qualified by a deterministic recovery policy and unit tests.

- Missing assets are reconciled by removing stale references rather than pretending playback can succeed.
- Corrupt databases are quarantined for explicit recovery rather than silently overwritten.
- Corrupt media is marked unavailable so the library can remain usable.
- Incomplete migrations select rollback/recovery rather than continuing with ambiguous schema state.
- Incomplete transfers select resume-or-discard handling.
- Startup reconciliation enumerates every required recovery condition.
- The policy explicitly forbids silent destructive recovery of user data.

The policy is intentionally a platform-neutral decision seam; concrete filesystem/database adapters execute these decisions and must preserve the same non-destructive contract.
