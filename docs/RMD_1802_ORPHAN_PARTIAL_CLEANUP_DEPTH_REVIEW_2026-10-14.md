# RMD-1802 — Iterative orphan-partial cleanup (2026-10-14)

## Independent finding

The Rust production function core/src/download.rs::visit_partials recursively descended into each nested Library directory. Deeply nested trees, including imported files, could exhaust the process call stack during orphan cleanup.

## Implementation and executable proof

The walker now uses an explicit pending-directory stack, streaming directory entries through fs::read_dir while retaining symlink rejection, canonical-root containment and I/O-error handling. Only pending directory paths are retained. The new Rust regression download::tests::orphan_cleanup_handles_deep_library_tree_without_recursion builds 256 nested directories with an orphan partial and sidecar plus completed media, and verifies cleanup, preservation and idempotence. Existing symlink guards/tests remain mandatory.

## Qualification and remaining gates

Code/test and evidence note are committed directly to master. Require passing exact-head eight-workflow CI and remaining independent review. This does not close RMD-1802, RMD-1803, RMD-1804, RMD-1805, global proof controls or the separate external YouTube/service/legal release gate.
