# RMD-1802 — Untrusted filename sanitization allocation bound (2026-10-13)

## Independent finding

`core/src/security.rs::sanitize_filename` previously normalized an entire untrusted filename into a new `String`, trimmed it into another allocation, and only then truncated to 180 Unicode scalar values. An oversized provider/import filename could therefore force allocations proportional to the input length despite the presentation/output bound. This is a resource-exhaustion risk; no claim is made that a live provider emits such input.

## Production correction and regression

The sanitizer now trims the borrowed input before mapping, takes at most 180 characters, and allocates only the bounded output. Because control whitespace is mapped to `_` by the original implementation, the borrowed-input trim deliberately excludes control characters, preserving the original normalization behavior. `core/src/security.rs::tests::filename_sanitization_bounds_untrusted_input_without_changing_trim_semantics` exercises million-character input, million-character leading trim material, Unicode/ASCII trim boundaries, control-whitespace mapping, and exact 180-character output. Existing `filename_is_safe` remains.

## Qualification status

The preceding title-sanitization implementation `8e90e545f64d78dc8baec9c5707e79eddbb04dc3` passed eight exact-head workflows: CI `38452273714`, Android smoke `38452273507`, Android FGS timeout `38452273522`, Supply chain `38452273554`, CI evidence `38452273607`, deterministic E2E `38452273555`, Android cold start `38452273661`, and Android real-network E2E `38452273572`. Those runs do not qualify this later filename change. Require a fresh exact-head matrix for the new commit, then continue independent Rust/Android review and final closeout. Do not check RMD-1802, RMD-1803, RMD-1805 or global completion rules solely from this correction.
