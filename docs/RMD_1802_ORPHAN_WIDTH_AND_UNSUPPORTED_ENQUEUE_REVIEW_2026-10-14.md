# RMD-1802 — Orphan-directory width and unsupported enqueue review (2026-10-14)

## Scope and findings

The earlier iterative orphan-partial walker eliminated recursion but retained one path for every discovered sibling directory in an explicit pending stack. On extremely wide imported Library trees, the pending stack could grow with sibling count. This is separate from the previously qualified 256-level recursion test.

The exported `FfiDownloadControlService::enqueue_inner` also accepted unsupported URLs as legacy queue-only identifiers when the production source registry could not construct a work plan. Such inputs could produce durable `Queued` rows with no executable work and no legitimate worker completion path. Opaque legacy identifiers are a separate compatibility case and must remain supported.

## Production corrections

- `core/src/download.rs::visit_partials`: depth-first `ReadDir` iterator stack, streaming sibling enumeration instead of storing sibling paths. Symlinks remain rejected and canonical-root containment enforced. Sidecars removed by the partial visitor may disappear from a live iterator; only NotFound on that entry is ignored.
- `core/src/ffi_download_control.rs::enqueue_inner`: reject URL-like identifiers without a supported executable plan, returning typed `UnsupportedSource`. Keep the existing queue-only fallback for opaque legacy identifiers, including externally staged executable plans.

## Behavioral regressions

- `download::tests::orphan_cleanup_handles_deep_library_tree_without_recursion`: 256 nested directories; preserves completed media and removes orphan partial plus sidecar.
- `download::tests::orphan_cleanup_streams_wide_sibling_directories_and_preserves_media`: 512 sibling directories with partial/sidecar/media, exact cleanup count, preserved completed media and idempotence.
- `ffi_download_control::stale_control_tests::unsupported_urls_do_not_leave_unexecutable_queued_jobs`: HTTP/FTP/mailto rejection, no phantom durable queue/work rows, opaque legacy job preserved.
- Existing symlink-escape and control/worker regressions remain mandatory.

## Qualification and closeout

Implementation of the bounded directory walker: `f167612f222e2ac034e8fea43a9681fda6cd7239`. It passed main CI `38649912469`, deterministic fixture E2E `38649912666`, CI evidence `38649912700`, Supply chain `38649912496`, Android cold start `38649912507`, real-network E2E `38649912585`, API-35 FGS timeout run `38649912552` (attempt 2 following a transient Maven dependency-resolution failure in attempt 1). Android smoke had not finished at the time of the next source correction; these are not a complete eight-workflow acceptance record.

Unsupported-URL enqueue implementation commit: `f4d1b1da8f042028128f1a608a917a314c3bf22f`. The reconciled final source/documentation head requires all eight workflows to pass on that exact SHA. Do not check any final RMD-1800/global closure checkbox solely based on these scoped findings. The full independent production audit, engineering closeout, and separate human YouTube/service-policy/legal gate remain outstanding.
