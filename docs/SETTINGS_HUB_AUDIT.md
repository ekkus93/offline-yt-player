# OYP-1601 Settings Hub Audit

The settings hub is represented by a deterministic five-row policy: Downloads, Playback, Storage, Appearance, and About.

Primary navigation remains fixed. The policy budgets header, five 56 dp rows, bottom navigation, and vertical padding to 448 dp total, which fits the target 640 dp compact portrait height without requiring scrolling.

`SettingsHubPolicyTest` locks the required row set and compact-screen no-scroll budget.
