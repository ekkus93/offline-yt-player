# OYP-1902 Android Share E2E audit

The deterministic Share E2E contract covers the complete required route: receive an Android shared URL, validate it, resolve metadata, select the bounded default quality, download, and verify a completed library item.

Qualification tests reject flows that omit either Android Share ingress or final library verification. Shared text is constrained to a bounded HTTPS URL before it can enter the resolver path.

A device-capable environment can drive this same contract through an Android share intent; ordinary CI retains the deterministic contract test when no emulator/device is available.
