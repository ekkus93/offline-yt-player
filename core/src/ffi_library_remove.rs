use crate::{CoreError, LibraryStore, delete_library_item_owned_assets};
use std::path::Path;
use std::sync::Arc;

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct FfiLibraryRemoveResult {
    pub removed: bool,
    pub item_id: Option<String>,
    pub error_message: Option<String>,
}

#[derive(Debug, thiserror::Error, uniffi::Error)]
pub enum FfiLibraryRemoveServiceOpenError {
    #[error("persistence error: {message}")]
    Persistence { message: String },
}

#[derive(Debug, uniffi::Object)]
pub struct FfiLibraryRemoveService {
    library: LibraryStore,
}

#[uniffi::export]
impl FfiLibraryRemoveService {
    #[uniffi::constructor]
    pub fn open(database_path: String) -> Result<Arc<Self>, FfiLibraryRemoveServiceOpenError> {
        LibraryStore::open(&database_path)
            .map(|library| Arc::new(Self { library }))
            .map_err(|error| FfiLibraryRemoveServiceOpenError::Persistence {
                message: error.message,
            })
    }

    pub fn remove_library_item(
        &self,
        library_root: String,
        item_id: String,
        confirmed: bool,
    ) -> FfiLibraryRemoveResult {
        if !confirmed {
            return FfiLibraryRemoveResult {
                removed: false,
                item_id: None,
                error_message: Some(
                    "Destructive library removal requires explicit confirmation".into(),
                ),
            };
        }
        match remove_item(&self.library, Path::new(&library_root), &item_id) {
            Ok(Some(removed_item_id)) => FfiLibraryRemoveResult {
                removed: true,
                item_id: Some(removed_item_id),
                error_message: None,
            },
            Ok(None) => FfiLibraryRemoveResult {
                removed: false,
                item_id: None,
                error_message: Some("Library item was not found".into()),
            },
            Err(error) => FfiLibraryRemoveResult {
                removed: false,
                item_id: None,
                error_message: Some(error.message),
            },
        }
    }
}

fn remove_item(
    library: &LibraryStore,
    library_root: &Path,
    item_id: &str,
) -> Result<Option<String>, CoreError> {
    delete_library_item_owned_assets(library, library_root, item_id)
        .map(|item| item.map(|deleted| deleted.item_id))
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::{LibraryItem, LocalAsset, MediaKind, SourceIdentity};
    use tempfile::tempdir;

    fn item() -> LibraryItem {
        LibraryItem {
            item_id: "item-1".into(),
            source: SourceIdentity::new("fixture", "source-1"),
            display_title: "Fixture".into(),
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

    fn persist_with_files(store: &LibraryStore, root: &std::path::Path) -> LibraryItem {
        let item = item();
        store.promote_completed("job-1", &item).unwrap();
        for asset in &item.assets {
            let path = root.join(&asset.relative_path);
            std::fs::create_dir_all(path.parent().unwrap()).unwrap();
            std::fs::write(&path, vec![0_u8; asset.bytes as usize]).unwrap();
        }
        item
    }

    #[test]
    fn remove_requires_confirmation_before_touching_files_or_metadata() {
        let store = LibraryStore::open_in_memory().unwrap();
        let root = tempdir().unwrap();
        let item = persist_with_files(&store, root.path());
        let service = FfiLibraryRemoveService {
            library: store.clone(),
        };

        let result = service.remove_library_item(
            root.path().to_string_lossy().into_owned(),
            item.item_id.clone(),
            false,
        );

        assert!(!result.removed);
        assert!(result.error_message.unwrap().contains("confirmation"));
        assert!(store.get("item-1").unwrap().is_some());
        assert!(root.path().join(&item.assets[0].relative_path).exists());
    }

    #[test]
    fn confirmed_remove_deletes_owned_files_before_metadata() {
        let store = LibraryStore::open_in_memory().unwrap();
        let root = tempdir().unwrap();
        let item = persist_with_files(&store, root.path());
        let service = FfiLibraryRemoveService {
            library: store.clone(),
        };

        let result = service.remove_library_item(
            root.path().to_string_lossy().into_owned(),
            "item-1".into(),
            true,
        );

        assert!(result.removed);
        assert_eq!(result.item_id.as_deref(), Some("item-1"));
        assert!(result.error_message.is_none());
        assert!(store.get("item-1").unwrap().is_none());
        for asset in &item.assets {
            assert!(!root.path().join(&asset.relative_path).exists());
        }
    }

    #[test]
    fn remove_reports_missing_item_without_creating_it() {
        let store = LibraryStore::open_in_memory().unwrap();
        let root = tempdir().unwrap();
        let service = FfiLibraryRemoveService { library: store };

        let result = service.remove_library_item(
            root.path().to_string_lossy().into_owned(),
            "missing".into(),
            true,
        );

        assert!(!result.removed);
        assert_eq!(
            result.error_message.as_deref(),
            Some("Library item was not found")
        );
    }
}
