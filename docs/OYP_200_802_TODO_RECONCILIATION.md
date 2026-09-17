# OYP-200 through OYP-802 TODO Reconciliation

## Scope

This reconciliation updates the canonical `docs/OFFLINE_YT_PLAYER_TODO.md` for milestones whose implementation, automated qualification, documentation, and exact-head CI evidence were completed before or through PR #114.

The purpose is not to change product scope. It removes stale unchecked boxes that no longer reflect the repository state.

## Reconciled sections

- OYP-200: Android portrait shell, Material 3 setup, Midnight Transit tokens, portrait-only manifest policy, fixed bottom navigation, bounded portrait layout primitives, and compact/large-font visibility checks.
- OYP-302/OYP-303: stable coarse FFI operations, cancellation/error semantics, and Android dispatcher qualification.
- OYP-501 through OYP-506: transfer foundation, resume/restart safety, retry policy, integrity/completion, cleanup/startup reconciliation, and deterministic HTTP fixture coverage.
- OYP-601 through OYP-603: provider-neutral source abstraction, registry dispatch, unsupported-source failures, and direct fixture source.
- OYP-701 through OYP-706: YouTube extraction strategy, URL recognition, metadata/format normalization, diagnostics, and release policy gate.
- OYP-801 through OYP-803: compatibility policy, adaptive local audio/video asset representation and Media3 source construction, persistence relationship, and muxing decision gate.

## Qualification evidence

Recent exact-head CI evidence includes:

- PR #108, source abstraction audit, push CI `35216549067`, PR CI `35216878091`.
- PR #109, OYP-701/OYP-702 audit, push CI `35217154088`, PR CI `35217418496`.
- PR #110, OYP-703 through OYP-705 audit, push CI `35219559685`, PR CI `35219910167`.
- PR #111, OYP-706 release policy gate, push CI `35220213652`, PR CI `35225076128`.
- PR #112, OYP-801/OYP-803 media strategy audit, PR CI `35231708182`.
- PR #113, coordinated local audio/video Media3 source construction, push CI `35244878593`, PR CI `35245193589`.
- PR #114, deterministic adaptive local asset qualification, push CI `35251933611`, PR CI `35252238796`, post-merge master CI `35252652057` at `3ae90ba6782a5742f92383b3e19d0359da235dd0`.

Earlier exact-head CI evidence remains recorded inline in the TODO for bootstrap, core, persistence, dependency/license, release metadata, documentation, and portability sections.

## Remaining boundary after this reconciliation

After this reconciliation, the next unreconciled product milestone is OYP-900, starting with local Media3 player integration around the qualified local asset planning and source-construction boundary. OYP-900 and OYP-1900 still need actual offline playback behavior qualification and end-to-end fixture proof; PR #114 deliberately does not claim those later milestones.
