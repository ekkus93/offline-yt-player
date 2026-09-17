# Download Row Actions Audit

OYP-1402 has an explicit row-presentation policy qualified by Android unit tests.

- Active rows expose visible Pause and Cancel actions.
- Paused rows expose visible Resume and Cancel actions.
- Failed rows expose Retry and Cancel plus a human-readable error reason.
- Progress carries downloaded bytes, total size when known, and a bounded percentage.
- Speed and ETA are surfaced only when both values are present and trustworthy; otherwise they remain hidden.
