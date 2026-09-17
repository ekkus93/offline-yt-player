# OYP-1302 Library item actions audit

Library item actions are modeled as explicit visible actions.

- Play and Details are always present.
- Rename is shown only when the feature is enabled.
- Remove is a visible action, deletes the device copy, and requires confirmation.
- Destructive behavior is explicitly forbidden from being swipe-only.
- Policy tests cover action visibility, conditional rename, confirmation, and destructive-action discoverability.
