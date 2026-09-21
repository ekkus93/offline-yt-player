use crate::{CoreError, ErrorKind, LibraryItem, LibraryStore, LocalAsset, MediaKind, SourceIdentity};
use std::sync::Arc;

pub const MAX_LIBRARY_TITLE_CHARS: usize = 120;

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct FfiLibraryRenameResult {
    pub renamed: bool,
    pub display_title: Option<String>,
    pub error_message: Option<String>,
}

#[derive(Debug, thiserror::Error, uniffi::Error)]
pub enum FfiLibraryRenameServiceOpenError {
    #[error("persistence error: {message}")]
    Persistence { message: String },
}

#[derive(Debug, uniffi::Object)]
pub struct FfiLibraryRenameService {
    library: LibraryStore,
}

#[uniffi::export]
impl FfiLibraryRenameService {
    #[uniffi::constructor]
    pub fn open(database_path: String) -> Result<Arc<Self>, FfiLibraryRenameServiceOpenError> {
        LibraryStore::open(&database_path)
            .map(|library| Arc::new(Self { library }))
            .map_err(|error| FfiLibraryRenameServiceOpenError::Persistence {
                message: error.message,
            })
    }

    pub fn rename_display_title(
        &self,
        item_id: String,
        display_title: String,
    ) -> FfiLibraryRenameResult {
        let title = match bounded_display_title(&display_title) {
            Ok(title) => title,
            Err(error) => {
                return FfiLibraryRenameResult {
                    renamed: false,
                    display_title: None,
                    error_message: Some(error.message),
                };
            }
        };
        match self.library.rename(&item_id, &title) {
            Ok(true) => FfiLibraryRenameResult {
                renamed: true,
                display_title: Some(title),
                error_message: None,
            },
            Ok(false) => FfiLibraryRenameResult {
                renamed: false,
                display_title: None,
                error_message: Some("Library item was not found".into()),
            },
            Err(error) => FfiLibraryRenameResult {
                renamed: false,
                display_title: None,
                error_message: Some(error.message),
            },
        }
    }
}

fn bounded_display_title(value: &str) -> Result<String, CoreError> {
    let normalized = value
        .chars()
        .filter_map(|character| match character {
            '\n' | '\r' | '\t' => Some(' '),
            c if c.is_control() => None,
            c => Some(c),
        })
        .collect::<String>()
        .split_whitespace()
        .collect::<Vec<_>>()
        .join(" ");
    if normalized.is_empty() {
        return Err(CoreError::new(
            ErrorKind::InvalidInput,
            "Display title must not be empty",
            false,
        ));
    }
    let bounded = normalized
        .chars()
        .take(MAX_LIBRARY_TITLE_CHARS)
        .collect::<String>();
    Ok(bounded)
}

#[cfg(test)]
mod tests {
    use super::*;

    fn item() -> LibraryItem {
        LibraryItem {
            item_id: "item-1".into(),
            source: SourceIdentity::new("fixture", "source-1"),
            display_title: "Original".into(),
            duration_ms: Some(60_000),
            quality_label: "720p".into(),
            assets: vec![LocalAsset {
                asset_id: "video".into(),
                kind: MediaKind::Video,
                relative_path: "items/item-1/video.mp4".into(),
                bytes: 10,
                sha256: None,
                mime_type: Some("video/mp4".into()),
            }],
            created_at_epoch_ms: 1,
            playback_position_ms: 0,
            completed: true,
        }
    }

    #[test]
    fn rename_is_metadata_only_and_survives_reopen() {
        let temp = tempfile::tempdir().unwrap();
        let database = temp.path().join("library.sqlite3");
        let store = LibraryStore::open(&database).unwrap();
        store.promote_completed("job-1", &item()).unwrap();
        drop(store);

        let service = FfiLibraryRenameService::open(database.to_string_lossy().into_owned()).unwrap();
        let result = service.rename_display_title("item-1".into(), "  Renamed fixture  ".into());
        assert!(result.renamed);
        assert_eq!(result.display_title.as_deref(), Some("Renamed fixture"));

        let reopened = LibraryStore::open(&database)
            .unwrap()
            .get("item-1")
            .unwrap()
            .unwrap();
        assert_eq!(reopened.display_title, "Renamed fixture");
        assert_eq!(
            reopened.assets.single().relative_path,
            "items/item-1/video.mp4"
        );
    }

    #[test]
    fn rename_rejects_empty_titles_and_bounds_long_titles() {
        assert_eq!(
            bounded_display_title(" \n\t ").unwrap_err().kind,
            ErrorKind::InvalidInput
        );
        let long = bounded_display_title(&"x".repeat(MAX_LIBRARY_TITLE_CHARS + 20)).unwrap();
        assert_eq!(long.chars().count(), MAX_LIBRARY_TITLE_CHARS);
    }

    #[test]
    fn rename_reports_missing_item_without_creating_it() {
        let store = LibraryStore::open_in_memory().unwrap();
        let service = FfiLibraryRenameService { library: store };
        let result = service.rename_display_title("missing".into(), "New".into());
        assert!(!result.renamed);
        assert_eq!(
            result.error_message.as_deref(),
            Some("Library item was not found")
        );
    }
}
