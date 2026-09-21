use crate::{FfiError, LibraryStore, PlaybackPositionPolicy, persist_playback_position};

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct FfiPlaybackPositionResult {
    pub saved: bool,
    pub error: Option<FfiError>,
}

/// Persist a playback position through the same durable library database used by Android.
/// Near-end positions are normalized by the canonical playback policy before storage.
#[uniffi::export]
pub fn ffi_save_playback_position(
    database_path: String,
    item_id: String,
    position_ms: u64,
    duration_ms: Option<u64>,
) -> FfiPlaybackPositionResult {
    let result = LibraryStore::open(database_path).and_then(|store| {
        persist_playback_position(
            &store,
            &item_id,
            position_ms,
            duration_ms,
            PlaybackPositionPolicy::default(),
        )
    });
    match result {
        Ok(saved) => FfiPlaybackPositionResult { saved, error: None },
        Err(error) => FfiPlaybackPositionResult {
            saved: false,
            error: Some(FfiError::from(&error)),
        },
    }
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
    fn ffi_position_save_survives_reopen_and_applies_completion_policy() {
        let temp = tempfile::tempdir().unwrap();
        let database = temp.path().join("library.sqlite3");
        let store = LibraryStore::open(&database).unwrap();
        store.promote_completed("job-1", &item()).unwrap();
        drop(store);

        let path = database.to_string_lossy().into_owned();
        let saved = ffi_save_playback_position(path.clone(), "item-1".into(), 25_000, Some(60_000));
        assert!(saved.saved);
        assert!(saved.error.is_none());
        assert_eq!(
            LibraryStore::open(&database)
                .unwrap()
                .get("item-1")
                .unwrap()
                .unwrap()
                .playback_position_ms,
            25_000,
        );

        let completed = ffi_save_playback_position(path, "item-1".into(), 55_000, Some(60_000));
        assert!(completed.saved);
        assert_eq!(
            LibraryStore::open(&database)
                .unwrap()
                .get("item-1")
                .unwrap()
                .unwrap()
                .playback_position_ms,
            60_000,
        );
    }
}
