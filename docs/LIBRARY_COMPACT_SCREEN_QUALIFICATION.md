# OYP-1303 compact-screen qualification

Deterministic library layout qualification covers the compact portrait target.

- Existing portrait layout budgeting must fit primary controls at 640 dp height at both normal and 1.30x font scale.
- Horizontal scrolling is forbidden.
- Only the bounded item region may scroll; fixed top controls and bottom navigation remain visible.
- Large-font mode retains primary actions in fixed chrome rather than moving them into a horizontally or vertically hidden control strip.
