# RMD-407 — Deterministic quality ranking reconciliation

RMD-407 is implemented on current `master` (`c2f211b808bef2bb99b7c0d7057f5e07baa2dff4`). This note records implementation and qualification evidence without changing the canonical checklist state before this evidence commit itself is qualified and merged.

## Requirement mapping

- **Compatibility before equal-height deduplication:** `core/src/youtube_extract.rs::curate_quality_choices` sorts formats with `format_rank` before `dedup_by_key(height)`. The rank includes compatibility and stream role before bitrate/fps/codec/container/format-id tie breakers, so provider order cannot select an inferior equal-height representation.
- **Prefer direct-play combined streams:** `format_compatibility` maps compatible combined streams to `Compatibility::Preferred`, `compatibility_rank` ranks them ahead of separate-assets and mux-required formats, and `role_rank` ranks combined ahead of video-only. `quality_dedup_prefers_direct_combined_even_when_provider_order_is_adversarial` proves this with deliberately adversarial provider order.
- **Prefer compatible split A/V over mux-required variants:** compatible video-only streams map to `RequiresSeparateAssets`, ranked ahead of `RequiresMuxing`; `quality_dedup_prefers_compatible_split_over_mux_required` proves the selection.
- **Deterministic codec/bitrate/fps/format-ID tie breaking:** `format_rank` includes descending bitrate and fps followed by codec, container, and format ID, with height remaining the primary quality grouping. `quality_ranking_has_deterministic_tie_breakers` exercises adversarial equal-height candidates and proves stable preference independent of provider order.
- **Adversarial provider-order coverage:** the direct-combined and split-over-mux tests intentionally place inferior choices before preferred choices, preventing the old order-before-dedup defect from regressing.

## Historical implementation qualification

The RMD-407 implementation was qualified on exact head `d434f72a045b3d153f66b5d5c70d4c9084a2e971` by push CI run `35420720921` and pull-request CI run `35420977677`, both successful. The same `curate_quality_choices`, ranking tuple, compatibility/role ranking, and adversarial tests are present on current master.

## Current-master qualification

Master `0ea81f8336a77ed233fb1153de727dd937da54bd` passed CI `35949623488`, Android smoke `35949623385`, and Android FGS-timeout `35949623359`. The RMD-406 evidence merge advanced master to `c2f211b808bef2bb99b7c0d7057f5e07baa2dff4`; RMD-407 production behavior is unchanged. RMD-407 is deterministic Rust/provider-normalization behavior, so the Android qualification acceleration plan correctly places its primary proof in Rust tests rather than emulator-only proof.

## Canonical TODO reconciliation rule

The five RMD-407 checkboxes in `docs/OFFLINE_YT_PLAYER_REMEDIATION_TODO_2026-09-17.md` may be checked only after this evidence note is qualified on its exact head and merged to `master`; the canonical TODO should then cite this note plus the exact merged SHA and CI runs.
