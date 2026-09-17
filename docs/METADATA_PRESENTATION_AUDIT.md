# Metadata Presentation Audit

OYP-1203 defines a bounded metadata contract for portrait primary screens.

- Primary metadata is limited to a two-line title plus three compact fields: duration, quality, and source.
- Blank optional fields are omitted instead of consuming layout space.
- Primary screens explicitly prohibit an unbounded metadata wall.
- Extended metadata is normalized into deterministic rows for a dedicated details page/list region, where scrolling can be isolated from primary controls.

This keeps the fixed portrait control budget intact while retaining access to detailed media information.
