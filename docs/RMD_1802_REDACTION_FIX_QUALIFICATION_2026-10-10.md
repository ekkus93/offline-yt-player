# RMD-1802 exact-head fix qualification — 2026-10-10

The fix in `core/src/security.rs` at `fc165c545f1180ba4fb99d37af56f17bf052270c` adds fail-closed handling for malformed and nested URLs, with two regression tests.

All eight workflows passed on that SHA: CI 38107448847; Android smoke 38107448870; Android FGS timeout 38107448885; Supply chain 38107448896; CI evidence 38107448908; deterministic E2E fixture 38107448877; Android real-network E2E 38107448890; Android cold start 38107448842.

This qualifies the specific fix only. The independent review and engineering closeout remain open.
