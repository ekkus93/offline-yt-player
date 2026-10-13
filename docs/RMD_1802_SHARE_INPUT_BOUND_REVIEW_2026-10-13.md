# RMD-1802 — Shared-input resource bound and stale test review

Date: 2026-10-13.

The old `DownloadSetupRoute.previewFor` test depended on a removed, fabricated Download Setup preview. Exact-head CI `38470211289` at `702d21ec7ae375df49bb626c6e74d4a5abac41e8` failed Kotlin test compilation because `ShareInputTest` still referenced this deleted helper. Commit `43f95cd3bea616d25642fa2c8525325caf80fb75` replaced these tests with executable share-input canonicalization and ambiguous-source rejection. Production ACTION_SEND-to-download behavior remains covered separately by Android instrumentation.

The parser also trimmed untrusted raw shared text before enforcing its 8,192-character limit, potentially scanning and allocating an oversized padded input. Commit `d43bc9778f385414cad1584932681e12128467a6` moves the bound ahead of trimming. Commit `c99633411c0946bf7dc0258d7920c7df9738898f` adds a million-character padded-input regression.

Qualification is pending a successful exact-head CI matrix. Do not check RMD-1802, RMD-1803, RMD-1805 or global proof rules on implementation presence alone. The external service-policy/legal release gate remains separate.
