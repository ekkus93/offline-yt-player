# RMD-1802 — Width-dependent Android Storage Settings allocation (2026-10-14)

## Independent finding

The prior StorageSettingsManager streaming directory changes eliminated eager File.listFiles child arrays but enqueued all directory children into the pending ArrayDeque before processing them. A very wide media or cache directory could still grow this stack proportionally to its number of entries, undermining the intended resource bound.

## Production change

Storage accounting and incomplete cleanup now enumerate the managed tree with lazy Files.walk, which does not follow symbolic links by default. Cache deletion uses the JDK Files.walkFileTree visitor and performs post-order directory removal without materializing a child array. The existing managed-root checks, path containment, fail-closed I/O and no-follow symlink behavior remain. A new host-JVM regression exercises 1,024 cache files, 1,024 incomplete files, a completed media file and an external symlink, verifying byte totals, owned deletion, external preservation and idempotence.

## Qualification

Changes are on master; exact-head main CI and all eight standard workflows must pass before qualification. The RMD-1802 independent review, frozen candidate closeout, post-merge evidence, global controls, and external service-policy/legal approval remain open.
