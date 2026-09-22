//! Portable domain core for Offline YT Player.
//!
//! Android-specific presentation, lifecycle, notifications, and playback stay outside this crate.

pub mod asset_validation;
pub mod concurrency;
pub mod deletion;
pub mod domain;
pub mod download;
pub mod events;
pub mod ffi;
pub mod ffi_download_control;
pub mod ffi_library_details;
pub mod ffi_library_playback;
pub mod ffi_library_remove;
pub mod ffi_library_rename;
pub mod ffi_playback_position;
pub mod ffi_source;
pub mod ffi_startup_reconciliation;
pub mod offline_assets;
pub mod persistence;
pub mod playback_position;
pub mod resume;
pub mod resume_http;
pub mod retry;
pub mod security;
pub mod source;
pub mod startup_reconciliation;
pub mod state;
pub mod subtitle;
pub mod thumbnail;
pub mod worker;
pub mod worker_pause;
pub mod youtube;
pub mod youtube_diagnostics;
// The quality rank is intentionally a lexicographic tuple so every tie-breaker remains explicit.
#[allow(clippy::type_complexity)]
mod youtube_extract;
pub mod youtube_source;

#[cfg(test)]
mod worker_cancel_tests;
#[cfg(test)]
mod youtube_source_fixture_tests;

pub use asset_validation::*;
pub use concurrency::*;
pub use deletion::*;
pub use domain::*;
pub use download::*;
pub use events::*;
pub use ffi::*;
pub use ffi_download_control::*;
pub use ffi_library_details::*;
pub use ffi_library_playback::*;
pub use ffi_library_remove::*;
pub use ffi_library_rename::*;
pub use ffi_playback_position::*;
pub use ffi_source::*;
pub use ffi_startup_reconciliation::*;
pub use offline_assets::*;
pub use persistence::*;
pub use playback_position::*;
pub use resume::*;
pub use resume_http::*;
pub use retry::*;
pub use security::*;
pub use source::*;
pub use startup_reconciliation::*;
pub use state::*;
pub use subtitle::*;
pub use thumbnail::*;
pub use worker::*;
pub use worker_pause::*;
pub use youtube::*;
pub use youtube_diagnostics::*;
pub use youtube_source::*;

uniffi::setup_scaffolding!();

/// Returns a stable human-readable identifier used by diagnostics.
#[must_use]
pub const fn core_identity() -> &'static str {
    "offline-yt-core"
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn identity_is_stable() {
        assert_eq!(core_identity(), "offline-yt-core");
    }
}
