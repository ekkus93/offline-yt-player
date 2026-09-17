# OYP-1301 Library screen audit

The library surface has an explicit deterministic policy for the v1 portrait shell.

- Top controls and the existing bottom navigation are fixed chrome; only the item region may scroll.
- List and grid are explicit layout modes rather than implicit responsive behavior.
- Search matches normalized title/source text and an optional source filter composes with it.
- The empty state requires a visible Add action.
- Policy tests cover fixed chrome, bounded scrolling, list/grid, search/filter composition, and the empty-state Add affordance.
