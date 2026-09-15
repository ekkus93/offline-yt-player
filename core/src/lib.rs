//! Portable domain core for Offline YT Player.
//!
//! Android-specific presentation, lifecycle, notifications, and playback stay outside this crate.

pub mod domain;
pub mod download;
pub mod events;
pub mod ffi;
pub mod persistence;
pub mod security;
pub mod source;
pub mod state;
pub mod youtube;

pub use domain::*;
pub use download::*;
pub use events::*;
pub use ffi::*;
pub use persistence::*;
pub use security::*;
pub use source::*;
pub use state::*;
pub use youtube::*;

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
