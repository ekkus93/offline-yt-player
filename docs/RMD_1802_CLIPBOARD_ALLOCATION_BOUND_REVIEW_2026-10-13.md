# RMD-1802 — Bound production clipboard Paste materialization (2026-10-13)

## Independent resource finding

The actual Add/Analyze Paste path calls `ui/AddWorkflowPolicy.kt::boundedClipboardText` with an Android clipboard `CharSequence`. The earlier implementation invoked `rawText.toString()` before applying its documented 4,096-character limit, then scanned the entire intermediate String for blankness. A very large or hostile clipboard object could force a full-size allocation/scan before UI input validation. This is a potential denial-of-service/resource-exhaustion path, not proof of a malicious live clipboard payload.

## Production correction and executable fast-gate test

The production helper now reads at most 4,096 indexed characters from `CharSequence` into a bounded `StringBuilder` and tests only those characters for blankness. It never calls the source object's `toString()` or `subSequence()`. Nonblank bounded input retains the existing Paste status; null/blank input retains the existing missing-clipboard status. The pre-existing truncation regression still checks that a 4,116-character String yields exactly 4,096 characters.

New host-JVM regression `AddWorkflowPolicyTest.clipboardCharSequenceIsReadOnlyUpToTheLimit` constructs a million-character `CharSequence` that throws on source `toString()`, `subSequence()`, or any `get(index)` beyond the input bound, and asserts that the actual production helper returns the bounded Paste result. No emulator is required for this contract; Compose/instrumentation tests separately qualify the actual Paste/Analyze behavior.

## Qualification status

The last known eight-of-eight passing implementation was `072b6d7443ee6c6ff4ac805b99184ae4bb1624ac`; this new change requires **its own successful exact-head CI** after all intervening source/doc changes. RMD-1802, RMD-1803, RMD-1804, RMD-1805 and all global proof controls remain open pending full independent evidence and closeout. Human YouTube/service-policy/legal approval remains separate and unapproved.
