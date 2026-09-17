# Playback Position Audit

## Scope

OYP-903 defines durable playback-position semantics independently of the Android lifecycle wiring.

## Policy

- Periodic persistence is bounded to a five-second default interval rather than writing on every player tick.
- Lifecycle transitions use the same durable `LibraryStore::save_playback_position` path and can force a final write independent of the periodic cadence.
- Saved positions resume directly when they are meaningfully before the end of the item.
- Positions within the final ten seconds are treated as near-end completion: persistence records the duration and the next playback session restarts at zero.
- Positions beyond a known duration are clamped safely.

## Qualification

`playback_position` unit tests prove bounded periodic cadence, intelligent resume behavior, and durable round-trip persistence including the near-end completion threshold. Android lifecycle/player callbacks can consume this policy without duplicating persistence semantics.
