# RMD-1802 — Provider choice label collisions (2026-10-14)

## Independent production finding

The Add screen built quality, subtitle, and audio executable-ID maps using provider-controlled display labels as map keys. Kotlin `associate` overwrote earlier choices on duplicate labels; `AddWorkflowPolicy.qualityLabels(...).distinct()` also removed quality choices. Different executable formats or tracks could become unselectable, or a user selection could enqueue the wrong provider ID. Upstream label normalization is not guaranteed for all `AppSourceAnalysisGateway` implementations.

## Production correction

`AddWorkflowPolicy.selectionKeys` assigns deterministic, collision-free presentation keys, reserving original provider labels before adding numeric suffixes. It preserves provider ordering and all executable identities, including blank labels through an unnamed fallback. Production `AddScreen` constructs quality/estimated-byte/separate-audio, subtitle-track, and audio-format maps by zipping these unique keys with the original provider choice records. The selected executable ID is no longer inferred from a possibly duplicated raw provider label.

## Behavioral qualification

Host-JVM `AddWorkflowPolicyTest.duplicateProviderLabelsPreserveAllExecutableChoiceKeys` checks duplicate and already-suffixed labels, unique keys, and original IDs. Packaged Compose `DuplicateProviderOptionsInstrumentedTest.duplicateAudioAndSubtitleLabelsEnqueueSelectedOriginalIds` now feeds raw duplicate provider labels and verifies the selected original audio/subtitle IDs. New `duplicateQualityLabelsEnqueueTheSelectedOriginalChoiceId` selects a duplicate quality label and verifies the original format ID is enqueued. Both methods are mandatory isolated API-29 smoke invocations with exact-method XML assertions.

## CI and closeout

Implementation on `master`: `c77e0922931026188c0582d0635e38348cc4abd5`. Packaged regression and mandatory smoke wiring: `567f7301cde7e73961585db5b511e4bf33b452a6`. These changes require their own exact-head eight-workflow CI; passing results from an earlier SHA must not be substituted. RMD-1802 independent review, RMD-1803/1805 final engineering closeout, and external YouTube/service/legal approval remain open.
