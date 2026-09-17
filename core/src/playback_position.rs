use crate::{CoreError, LibraryStore};

pub const DEFAULT_PERSIST_INTERVAL_MS: u64 = 5_000;
pub const DEFAULT_NEAR_END_THRESHOLD_MS: u64 = 10_000;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct PlaybackPositionPolicy {
    pub persist_interval_ms: u64,
    pub near_end_threshold_ms: u64,
}

impl Default for PlaybackPositionPolicy {
    fn default() -> Self {
        Self {
            persist_interval_ms: DEFAULT_PERSIST_INTERVAL_MS,
            near_end_threshold_ms: DEFAULT_NEAR_END_THRESHOLD_MS,
        }
    }
}

impl PlaybackPositionPolicy {
    #[must_use]
    pub fn should_persist_periodically(&self, last_persisted_ms: u64, position_ms: u64) -> bool {
        position_ms.saturating_sub(last_persisted_ms) >= self.persist_interval_ms
    }

    #[must_use]
    pub fn resume_position_ms(&self, saved_position_ms: u64, duration_ms: Option<u64>) -> u64 {
        match duration_ms {
            Some(duration) if self.is_near_end(saved_position_ms, duration) => 0,
            Some(duration) => saved_position_ms.min(duration),
            None => saved_position_ms,
        }
    }

    #[must_use]
    pub fn is_near_end(&self, position_ms: u64, duration_ms: u64) -> bool {
        duration_ms > 0 && duration_ms.saturating_sub(position_ms) <= self.near_end_threshold_ms
    }
}

pub fn persist_playback_position(
    store: &LibraryStore,
    item_id: &str,
    position_ms: u64,
    duration_ms: Option<u64>,
    policy: PlaybackPositionPolicy,
) -> Result<bool, CoreError> {
    let normalized = match duration_ms {
        Some(duration) if policy.is_near_end(position_ms, duration) => duration,
        Some(duration) => position_ms.min(duration),
        None => position_ms,
    };
    store.save_playback_position(item_id, normalized)
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::{LibraryItem, LocalAsset, MediaKind, SourceIdentity};

    fn item() -> LibraryItem {
        LibraryItem {
            item_id: "item-1".into(),
            source: SourceIdentity {
                provider: "fixture".into(),
                media_id: "source-1".into(),
                canonical_url: None,
            },
            display_title: "Fixture".into(),
            duration_ms: Some(60_000),
            quality_label: "720p".into(),
            assets: vec![LocalAsset {
                asset_id: "combined".into(),
                kind: MediaKind::Video,
                relative_path: "items/item-1/video.mp4".into(),
                bytes: 1,
                sha256: None,
                mime_type: Some("video/mp4".into()),
            }],
            created_at_epoch_ms: 1,
            playback_position_ms: 0,
            completed: true,
        }
    }

    #[test]
    fn periodic_persistence_is_bounded() {
        let policy = PlaybackPositionPolicy::default();
        assert!(!policy.should_persist_periodically(10_000, 14_999));
        assert!(policy.should_persist_periodically(10_000, 15_000));
    }

    #[test]
    fn resume_restarts_items_saved_near_end() {
        let policy = PlaybackPositionPolicy::default();
        assert_eq!(policy.resume_position_ms(20_000, Some(60_000)), 20_000);
        assert_eq!(policy.resume_position_ms(55_000, Some(60_000)), 0);
        assert_eq!(policy.resume_position_ms(70_000, Some(60_000)), 0);
    }

    #[test]
    fn lifecycle_persist_round_trips_and_marks_near_end() {
        let store = LibraryStore::open_in_memory().unwrap();
        store.promote_completed("job-1", &item()).unwrap();
        let policy = PlaybackPositionPolicy::default();
        assert!(persist_playback_position(&store, "item-1", 25_000, Some(60_000), policy).unwrap());
        assert_eq!(store.get("item-1").unwrap().unwrap().playback_position_ms, 25_000);
        assert!(persist_playback_position(&store, "item-1", 55_000, Some(60_000), policy).unwrap());
        assert_eq!(store.get("item-1").unwrap().unwrap().playback_position_ms, 60_000);
    }
}
