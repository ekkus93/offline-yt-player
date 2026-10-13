# RMD-1802 — Untrusted title sanitization allocation bound (2026-10-13)

## Independent finding

`core/src/security.rs::sanitize_title` previously copied the entire provider-supplied title into a new `String`, split it into a `Vec` of all whitespace-delimited words, and joined another unbounded string before finally truncating to 180 Unicode scalar values. A very large provider title could therefore consume memory proportional to the untrusted input despite the documented 180-character presentation limit. This is a resource-bound defect, not evidence that a real provider currently emits such a title.

## Correction and deterministic proof

The sanitizer now performs whitespace collapsing and the 180-character cap in a single streaming pass, without allocating a copy of the complete input or a vector of words. Control characters remain whitespace, leading/trailing whitespace is suppressed, interior whitespace is collapsed to one space, and the truncation boundary retains the prior behavior. `core/src/security.rs::tests::title_sanitization_streams_untrusted_long_input_with_original_spacing` exercises a million-character input, normal whitespace/control normalization, an all-whitespace input, and the exact 180-character boundary. The existing `title_is_bounded_and_strips_controls` test remains.

## Qualification and closeout

This change is committed directly to `master` with an exact expected-head compare-and-swap through Ralph Bridge. The implementation/test commit must pass exact-head CI before being treated as qualified. This focused finding does not establish completion of the independent Rust/Android review, RMD-1803 final candidate freeze, RMD-1805 engineering closeout, or the separate human YouTube/service-policy/legal release gate. Keep those canonical TODO checkboxes unchecked until their full acceptance criteria are satisfied.
