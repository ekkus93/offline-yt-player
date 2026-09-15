//! Portable domain core for Offline YT Player.
//!
//! Android-specific presentation, lifecycle, notifications, and playback stay outside this crate.

/// Returns a stable human-readable identifier used by bootstrap tests and diagnostics.
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
