# OYP-1602 Download Settings Audit

The download settings policy exposes the required bounded preferences: default quality, Wi-Fi-only operation, concurrent download limit, subtitle default, and retry preference.

Concurrency is explicitly constrained to 1-3 transfers and normalized at the settings boundary. Defaults are deterministic: best-compatible quality, network unrestricted, two concurrent transfers, subtitles off, and automatic retry.

`DownloadSettingsPolicyTest` qualifies the complete preference surface and concurrency bounds.
