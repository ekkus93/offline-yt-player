use crate::{CoreError, ErrorKind, LibraryItem, LibraryStore};
use std::path::{Component, Path};

/// Deletes every persisted asset owned by `item_id` before removing its library metadata.
///
/// The operation is intentionally retryable after interruption: already-missing files count as
/// deleted, while metadata is retained until all remaining owned files have been removed. This
/// makes a partial delete explicit and lets the same operation reconcile it on the next attempt.
pub fn delete_library_item_owned_assets(
    store: &LibraryStore,
    library_root: &Path,
    item_id: &str,
) -> Result<Option<LibraryItem>, CoreError> {
    let item = match store.get(item_id)? {
        Some(item) => item,
        None => return Ok(None),
    };

    let canonical_root = std::fs::canonicalize(library_root).map_err(delete_io_error)?;
    for asset in &item.assets {
        validate_delete_path(&asset.relative_path)?;
        let path = library_root.join(&asset.relative_path);
        let Some(parent) = path.parent() else {
            return Err(CoreError::new(
                ErrorKind::InvalidInput,
                "asset path has no parent",
                false,
            ));
        };
        let canonical_parent = match std::fs::canonicalize(parent) {
            Ok(parent) => parent,
            Err(error) if error.kind() == std::io::ErrorKind::NotFound => continue,
            Err(error) => return Err(delete_io_error(error)),
        };
        if !canonical_parent.starts_with(&canonical_root) {
            return Err(CoreError::new(
                ErrorKind::InvalidInput,
                "asset path escapes the selected library root",
                false,
            ));
        }
        match std::fs::remove_file(&path) {
            Ok(()) => {}
            Err(error) if error.kind() == std::io::ErrorKind::NotFound => {}
            Err(error) => return Err(delete_io_error(error)),
        }
    }

    store.delete(item_id)
}

fn validate_delete_path(value: &str) -> Result<(), CoreError> {
    let path = Path::new(value);
    if value.is_empty()
        || path.is_absolute()
        || path.components().any(|component| {
            matches!(
                component,
                Component::ParentDir | Component::RootDir | Component::Prefix(_)
            )
        })
    {
        return Err(CoreError::new(
            ErrorKind::InvalidInput,
            "asset path must be a safe relative library path",
            false,
        ));
    }
    Ok(())
}

fn delete_io_error(error: std::io::Error) -> CoreError {
    CoreError::new(
        ErrorKind::Persistence,
        format!("failed to delete owned library asset: {error}"),
        false,
    )
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::{LibraryItem, LocalAsset, MediaKind, SourceIdentity};
    use tempfile::tempdir;

    fn item() -> LibraryItem {
        LibraryItem {
            item_id: "item-1".into(),
            source: SourceIdentity {
                provider: "fixture".into(),
                media_id: "source-1".into(),
                canonical_url: None,
            },
            display_title: "Test Video".into(),
            duration_ms: Some(60_000),
            quality_label: "720p".into(),
            assets: vec![
                LocalAsset {
                    asset_id: "video".into(),
                    kind: MediaKind::Video,
                    relative_path: "items/item-1/video.mp4".into(),
                    bytes: 4,
                    sha256: None,
                    mime_type: Some("video/mp4".into()),
                },
                LocalAsset {
                    asset_id: "subtitle".into(),
                    kind: MediaKind::Subtitle,
                    relative_path: "items/item-1/subtitles/en.vtt".into(),
                    bytes: 3,
                    sha256: None,
                    mime_type: Some("text/vtt".into()),
                },
            ],
            created_at_epoch_ms: 1,
            playback_position_ms: 0,
            completed: true,
        }
    }

    fn persist(store: &LibraryStore, item: &LibraryItem) {
        store.promote_completed("job-1", item).unwrap();
    }

    #[test]
    fn removes_all_owned_assets_before_metadata() {
        let store = LibraryStore::open_in_memory().unwrap();
        let item = item();
        persist(&store, &item);
        let root = tempdir().unwrap();
        for asset in &item.assets {
            let path = root.path().join(&asset.relative_path);
            std::fs::create_dir_all(path.parent().unwrap()).unwrap();
            std::fs::write(&path, vec![0_u8; asset.bytes as usize]).unwrap();
        }

        let deleted = delete_library_item_owned_assets(&store, root.path(), "item-1")
            .unwrap()
            .unwrap();
        assert_eq!(deleted.item_id, "item-1");
        assert!(store.get("item-1").unwrap().is_none());
        for asset in &item.assets {
            assert!(!root.path().join(&asset.relative_path).exists());
        }
    }

    #[test]
    fn partial_failure_keeps_metadata_and_retry_reconciles() {
        let store = LibraryStore::open_in_memory().unwrap();
        let item = item();
        persist(&store, &item);
        let root = tempdir().unwrap();
        let video = root.path().join(&item.assets[0].relative_path);
        std::fs::create_dir_all(video.parent().unwrap()).unwrap();
        std::fs::write(&video, [0_u8; 4]).unwrap();
        let subtitle = root.path().join(&item.assets[1].relative_path);
        std::fs::create_dir_all(&subtitle).unwrap();

        let error = delete_library_item_owned_assets(&store, root.path(), "item-1").unwrap_err();
        assert_eq!(error.kind, ErrorKind::Persistence);
        assert!(!video.exists());
        assert!(store.get("item-1").unwrap().is_some());

        std::fs::remove_dir(&subtitle).unwrap();
        std::fs::write(&subtitle, [0_u8; 3]).unwrap();
        delete_library_item_owned_assets(&store, root.path(), "item-1").unwrap();
        assert!(!subtitle.exists());
        assert!(store.get("item-1").unwrap().is_none());
    }

    #[cfg(unix)]
    #[test]
    fn rejects_symlink_parent_escape_without_touching_outside_file() {
        use std::os::unix::fs::symlink;

        let store = LibraryStore::open_in_memory().unwrap();
        let mut item = item();
        item.assets.truncate(1);
        persist(&store, &item);
        let root = tempdir().unwrap();
        let outside = tempdir().unwrap();
        std::fs::create_dir_all(root.path().join("items")).unwrap();
        symlink(outside.path(), root.path().join("items/item-1")).unwrap();
        let outside_file = outside.path().join("video.mp4");
        std::fs::write(&outside_file, [0_u8; 4]).unwrap();

        let error = delete_library_item_owned_assets(&store, root.path(), "item-1").unwrap_err();
        assert_eq!(error.kind, ErrorKind::InvalidInput);
        assert!(outside_file.exists());
        assert!(store.get("item-1").unwrap().is_some());
    }
}
