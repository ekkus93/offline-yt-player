# Share-to-Download UX Audit

## Scope

OYP-1102 defines the deterministic routing contract used after Android share-intent parsing has produced a trusted shared URL.

## Policy

- A non-empty parsed shared URL opens Download Setup directly instead of requiring the user to re-enter the URL on the Add screen.
- Share-origin Download Setup preserves the fixed-control-layout invariant by routing through the same setup surface policy as ordinary adds.
- The route records a clear Library back-stack destination so Back exits the setup flow predictably rather than preserving an arbitrary external-task stack.
- Empty or absent shared input falls back to Library rather than opening a malformed setup route.

## Qualification

`ShareToDownloadPolicyTest` locks direct setup routing, trimming of the already-parsed URL handoff, fixed-control-layout/back-stack invariants, and Library fallback for missing shared input. Android intent parsing remains covered by `ShareIntentPolicyTest` under OYP-1101.
