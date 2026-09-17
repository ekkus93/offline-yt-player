# Downloads Screen Audit

OYP-1401 is represented by a deterministic Android UI policy seam.

- App bar, filter controls, and bottom navigation are fixed chrome.
- Only the transfer-list region is allowed to scroll; whole-screen scrolling is prohibited.
- Active, paused, failed, and completed transfers each have an explicit presentation state.
- `DownloadsScreenPolicyTest` qualifies these layout and state invariants in Android unit CI.
