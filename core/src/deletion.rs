use crate::resume::resume_metadata_path;
use crate::{CoreError, ErrorKind, LibraryItem, LibraryStore};
use sha2::{Digest, Sha256};
use std::path::{Component, Path, PathBuf};

/// Deletes every persisted asset owned by `item_id` before removing its library metadata.
///
/// The operation is intentionally retryable after interruption: already-missing files count as
/// deleted, while metadata is retained until all remaining owned files, transfer partials, and
/// resume sidecars have been removed. This makes a partial delete explicit and lets the same
/// operation reconcile it on the next attempt.
pub fn delete_library_item_owned_assets(
    store: &LibraryStore,
    library_root: &Path,
    item_id: &str,
) -> Result<Option<LibraryItem>, CoreError> {
    let item = match store.get(item_id)? {
        Some(item) => item,
        None => return Ok(None),
    };

    // The root itself must be an owned directory. Canonicalizing a symbolic
    // link first would redefine an external directory as our deletion boundary.
    // Keep all file/sidecar operations and metadata untouched on invalid roots.
    match std::fs::symlink_metadata(library_root) {
        Ok(metadata) if metadata.file_type().is_symlink() || !metadata.is_dir() => {
            return Err(CoreError::new(
                ErrorKind::InvalidInput,
                "library root must be a real directory, not a symlink",
                false,
            ));
        }
        Ok(_) => {}
        Err(error) => return Err(delete_io_error(error)),
    }
    let canonical_root = std::fs::canonicalize(library_root).map_err(delete_io_error)?;
    // Validate every asset's namespace before any deletion, including partials
    // and resume sidecars. Being inside library_root does not prove ownership.
    let owned_directory = owned_item_directory(item_id)?;
    for asset in &item.assets {
        validate_delete_path(&asset.relative_path)?;
        let normalized_path: PathBuf = Path::new(&asset.relative_path)
            .components()
            .filter_map(|component| match component {
                Component::Normal(name) => Some(name),
                _ => None,
            })
            .collect();
        if !normalized_path.starts_with(&owned_directory) || normalized_path == owned_directory {
            return Err(CoreError::new(
                ErrorKind::InvalidInput,
                "asset path does not belong to the selected library item",
                false,
            ));
        }
    }
    for asset in &item.assets {
        validate_delete_path(&asset.relative_path)?;
        let asset_path = library_root.join(&asset.relative_path);
        let partial_path = transfer_partial_path(&asset_path);
        let resume_path = resume_metadata_path(&partial_path);

        remove_owned_file(library_root, &canonical_root, &asset_path)?;
        remove_owned_file(library_root, &canonical_root, &partial_path)?;
        remove_owned_file(library_root, &canonical_root, &resume_path)?;
    }

    store.delete(item_id)
}

fn owned_item_directory(item_id: &str) -> Result<PathBuf, CoreError> {
    if crate::security::validate_http_url(item_id).is_ok() {
        // The durable URL job ID owns a hashed namespace, not the media ID.
        let digest = Sha256::digest(item_id.as_bytes());
        return Ok(PathBuf::from("items").join(format!("by-job-{digest:x}")));
    }
    let mut components = Path::new(item_id).components();
    match (components.next(), components.next()) {
        (Some(Component::Normal(name)), None) if !name.is_empty() => {
            // This namespace is reserved for URL-owned downloads. Otherwise
            // a forged non-URL item ID could impersonate a URL item's digest
            // and delete its media while staying inside the library root.
            if item_id
                .strip_prefix("by-job-")
                .is_some_and(|suffix| suffix.len() == 64 && suffix.bytes().all(|b| b.is_ascii_hexdigit()))
            {
                return Err(CoreError::new(
                    ErrorKind::InvalidInput,
                    "hashed URL download directory is reserved",
                    false,
                ));
            }
            Ok(PathBuf::from("items").join(name))
        }
        _ => Err(CoreError::new(
            ErrorKind::InvalidInput,
            "library item ID is not a safe single directory name",
            false,
        )),
    }
}

fn remove_owned_file(
    library_root: &Path,
    canonical_root: &Path,
    path: &Path,
) -> Result<(), CoreError> {
    let Some(parent) = path.parent() else {
        return Err(CoreError::new(
            ErrorKind::InvalidInput,
            "asset path has no parent",
            false,
        ));
    };
    // Canonical containment alone does not prove ownership. An alias
    // inside the root may resolve to a different library item's directory.
    let relative_parent = parent.strip_prefix(library_root).map_err(|_| {
        CoreError::new(
            ErrorKind::InvalidInput,
            "asset path escapes the library root",
            false,
        )
    })?;
    let mut current = library_root.to_path_buf();
    for component in relative_parent.components() {
        if let Component::Normal(name) = component {
            current.push(name);
            match std::fs::symlink_metadata(&current) {
                Ok(meta) if meta.file_type().is_symlink() => {
                    return Err(CoreError::new(
                        ErrorKind::InvalidInput,
                        "asset parent must not traverse a symbolic link",
                        false,
                    ));
                }
                Ok(_) => {}
                Err(err) if err.kind() == std::io::ErrorKind::NotFound => return Ok(()),
                Err(err) => return Err(delete_io_error(err)),
            }
        }
    }
    let canonical_parent = match std::fs::canonicalize(parent) {
        Ok(parent) => parent,
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => return Ok(()),
        Err(error) => return Err(delete_io_error(error)),
    };
    if !canonical_parent.starts_with(canonical_root) {
        return Err(CoreError::new(
            ErrorKind::InvalidInput,
            "asset path escapes the selected library root",
            false,
        ));
    }
    if path.strip_prefix(library_root).is_err() {
        return Err(CoreError::new(
            ErrorKind::InvalidInput,
            "asset path escapes the selected library root",
            false,
        ));
    }
    match std::fs::remove_file(path) {
        Ok(()) => Ok(()),
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => Ok(()),
        Err(error) => Err(delete_io_error(error)),
    }
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

fn transfer_partial_path(final_path: &Path) -> PathBuf {
    let name = final_path
        .file_name()
        .and_then(|name| name.to_str())
        .unwrap_or("asset");
    final_path.with_file_name(format!(".{name}.partial"))
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
    use crate::{LibraryItem, LocalAsset, MediaKind, SourceIdentity, THUMBNAIL_ASSET_ID};
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
                    asset_id: "audio".into(),
                    kind: MediaKind::Audio,
                    relative_path: "items/item-1/audio.m4a".into(),
                    bytes: 6,
                    sha256: None,
                    mime_type: Some("audio/mp4".into()),
                },
                LocalAsset {
                    asset_id: "z-subtitle".into(),
                    kind: MediaKind::Subtitle,
                    relative_path: "items/item-1/subtitles/en.vtt".into(),
                    bytes: 3,
                    sha256: None,
                    mime_type: Some("text/vtt".into()),
                },
                LocalAsset {
                    asset_id: THUMBNAIL_ASSET_ID.into(),
                    kind: MediaKind::Thumbnail,
                    relative_path: "items/item-1/thumbnails/thumbnail.jpg".into(),
                    bytes: 5,
                    sha256: None,
                    mime_type: Some("image/jpeg".into()),
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

    fn write_asset_family(
        root: &Path,
        relative_path: &str,
        bytes: u64,
    ) -> (PathBuf, PathBuf, PathBuf) {
        let final_path = root.join(relative_path);
        std::fs::create_dir_all(final_path.parent().unwrap()).unwrap();
        std::fs::write(&final_path, vec![0_u8; bytes as usize]).unwrap();
        let partial_path = transfer_partial_path(&final_path);
        std::fs::write(&partial_path, b"partial bytes").unwrap();
        let resume_path = resume_metadata_path(&partial_path);
        std::fs::write(&resume_path, b"{\"sidecar\":true}").unwrap();
        (final_path, partial_path, resume_path)
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
    fn deleting_item_removes_managed_thumbnail_before_metadata() {
        let store = LibraryStore::open_in_memory().unwrap();
        let item = item();
        persist(&store, &item);
        let root = tempdir().unwrap();
        let thumbnail = item
            .assets
            .iter()
            .find(|asset| asset.kind == MediaKind::Thumbnail)
            .unwrap();
        let thumbnail_path = root.path().join(&thumbnail.relative_path);
        std::fs::create_dir_all(thumbnail_path.parent().unwrap()).unwrap();
        std::fs::write(&thumbnail_path, [1_u8; 5]).unwrap();

        delete_library_item_owned_assets(&store, root.path(), "item-1").unwrap();

        assert!(!thumbnail_path.exists());
        assert!(store.get("item-1").unwrap().is_none());
    }

    #[test]
    fn removes_final_partial_and_resume_sidecar_for_each_asset_kind() {
        let store = LibraryStore::open_in_memory().unwrap();
        let item = item();
        persist(&store, &item);
        let root = tempdir().unwrap();
        let owned_paths = item
            .assets
            .iter()
            .map(|asset| write_asset_family(root.path(), &asset.relative_path, asset.bytes))
            .collect::<Vec<_>>();

        delete_library_item_owned_assets(&store, root.path(), "item-1").unwrap();

        assert!(store.get("item-1").unwrap().is_none());
        for (final_path, partial_path, resume_path) in owned_paths {
            assert!(!final_path.exists(), "final asset remained: {final_path:?}");
            assert!(
                !partial_path.exists(),
                "partial asset remained: {partial_path:?}"
            );
            assert!(
                !resume_path.exists(),
                "resume sidecar remained: {resume_path:?}"
            );
        }
    }

    #[test]
    fn partial_failure_keeps_metadata_and_retry_reconciles() {
        let store = LibraryStore::open_in_memory().unwrap();
        let mut item = item();
        item.assets.truncate(1);
        persist(&store, &item);
        let root = tempdir().unwrap();
        let video = root.path().join(&item.assets[0].relative_path);
        std::fs::create_dir_all(video.parent().unwrap()).unwrap();
        std::fs::write(&video, [0_u8; 4]).unwrap();
        let partial = transfer_partial_path(&video);
        std::fs::create_dir(&partial).unwrap();

        let error = delete_library_item_owned_assets(&store, root.path(), "item-1").unwrap_err();
        assert_eq!(error.kind, ErrorKind::Persistence);
        assert!(!video.exists());
        assert!(partial.exists());
        assert!(store.get("item-1").unwrap().is_some());

        std::fs::remove_dir(&partial).unwrap();
        std::fs::write(&partial, b"partial bytes").unwrap();
        delete_library_item_owned_assets(&store, root.path(), "item-1").unwrap();
        assert!(!partial.exists());
        assert!(store.get("item-1").unwrap().is_none());
    }

    #[test]
    fn resume_sidecar_failure_keeps_metadata_and_retry_reconciles() {
        let store = LibraryStore::open_in_memory().unwrap();
        let mut item = item();
        item.assets.truncate(1);
        persist(&store, &item);
        let root = tempdir().unwrap();
        let (final_path, partial_path, resume_path) = write_asset_family(
            root.path(),
            &item.assets[0].relative_path,
            item.assets[0].bytes,
        );
        std::fs::remove_file(&resume_path).unwrap();
        std::fs::create_dir(&resume_path).unwrap();

        let error = delete_library_item_owned_assets(&store, root.path(), "item-1").unwrap_err();

        assert_eq!(error.kind, ErrorKind::Persistence);
        assert!(!final_path.exists());
        assert!(!partial_path.exists());
        assert!(resume_path.exists());
        assert!(store.get("item-1").unwrap().is_some());

        std::fs::remove_dir(&resume_path).unwrap();
        std::fs::write(&resume_path, b"{\"sidecar\":true}").unwrap();
        delete_library_item_owned_assets(&store, root.path(), "item-1").unwrap();
        assert!(!resume_path.exists());
        assert!(store.get("item-1").unwrap().is_none());
    }

    #[cfg(unix)]
    #[test]
    fn rejects_symlinked_root_without_deleting_external_assets_or_metadata() {
        use std::os::unix::fs::symlink;

        let store = LibraryStore::open_in_memory().unwrap();
        let mut item = item();
        item.assets.truncate(1);
        persist(&store, &item);

        let outside = tempdir().unwrap();
        let root_parent = tempdir().unwrap();
        let (asset, partial, resume) =
            write_asset_family(outside.path(), &item.assets[0].relative_path, 4);
        let aliased_root = root_parent.path().join("linked-library");
        symlink(outside.path(), &aliased_root).unwrap();

        let error = delete_library_item_owned_assets(&store, &aliased_root, "item-1").unwrap_err();
        assert_eq!(error.kind, ErrorKind::InvalidInput);
        assert!(asset.is_file(), "an external completed asset was deleted");
        assert!(
            partial.is_file(),
            "an external transfer partial was deleted"
        );
        assert!(resume.is_file(), "an external resume sidecar was deleted");
        assert!(
            store.get("item-1").unwrap().is_some(),
            "metadata was deleted"
        );
    }

    #[test]
    fn rejects_non_directory_root_without_removing_library_metadata() {
        let store = LibraryStore::open_in_memory().unwrap();
        let mut item = item();
        item.assets.truncate(1);
        persist(&store, &item);

        let root_parent = tempdir().unwrap();
        let invalid_root = root_parent.path().join("regular-file");
        std::fs::write(&invalid_root, b"unrelated data").unwrap();

        let error = delete_library_item_owned_assets(&store, &invalid_root, "item-1").unwrap_err();
        assert_eq!(error.kind, ErrorKind::InvalidInput);
        assert_eq!(std::fs::read(&invalid_root).unwrap(), b"unrelated data");
        assert!(
            store.get("item-1").unwrap().is_some(),
            "metadata was deleted"
        );
    }

    #[cfg(unix)]
    #[test]
    fn linked_parent_within_library_cannot_remove_another_items_asset() {
        use std::os::unix::fs::symlink;

        let store = LibraryStore::open_in_memory().unwrap();
        let mut victim = item();
        victim.assets.truncate(1);
        persist(&store, &victim);
        let root = tempdir().unwrap();
        let items = root.path().join("items");
        let other_dir = items.join("item-2");
        std::fs::create_dir_all(&other_dir).unwrap();
        let other_video = other_dir.join("video.mp4");
        std::fs::write(&other_video, b"unrelated content").unwrap();
        symlink(&other_dir, items.join("item-1")).unwrap();

        let error = delete_library_item_owned_assets(&store, root.path(), "item-1").unwrap_err();
        assert_eq!(error.kind, ErrorKind::InvalidInput);
        assert_eq!(std::fs::read(&other_video).unwrap(), b"unrelated content");
        assert!(store.get("item-1").unwrap().is_some());
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

    #[test]
    fn corrupt_asset_metadata_cannot_delete_a_different_items_files() {
        let store = LibraryStore::open_in_memory().unwrap();
        let mut victim = item();
        victim.assets.truncate(1);
        victim.assets[0].relative_path = "items/item-2/video.mp4".into();
        persist(&store, &victim);
        let root = tempdir().unwrap();
        let (foreign, partial, resume) =
            write_asset_family(root.path(), &victim.assets[0].relative_path, 4);

        let error = delete_library_item_owned_assets(&store, root.path(), "item-1").unwrap_err();
        assert_eq!(error.kind, ErrorKind::InvalidInput);
        assert_eq!(std::fs::read(&foreign).unwrap(), vec![0_u8; 4]);
        assert!(partial.exists());
        assert!(resume.exists());
        assert!(store.get("item-1").unwrap().is_some());
    }

    #[test]
    fn all_asset_ownership_is_checked_before_deleting_the_first_asset() {
        let store = LibraryStore::open_in_memory().unwrap();
        let mut victim = item();
        victim.assets.truncate(2);
        victim.assets[1].relative_path = "items/item-2/audio.m4a".into();
        persist(&store, &victim);
        let root = tempdir().unwrap();
        let (owned, partial, resume) =
            write_asset_family(root.path(), &victim.assets[0].relative_path, 4);
        let (foreign, _, _) = write_asset_family(root.path(), &victim.assets[1].relative_path, 6);

        let error = delete_library_item_owned_assets(&store, root.path(), "item-1").unwrap_err();
        assert_eq!(error.kind, ErrorKind::InvalidInput);
        assert!(owned.exists());
        assert!(partial.exists());
        assert!(resume.exists());
        assert!(foreign.exists());
        assert!(store.get("item-1").unwrap().is_some());
    }

    #[test]
    fn url_job_deletes_only_its_hashed_namespace() {
        let job_id = "https://fixture.invalid/watch/item-one";
        let directory = owned_item_directory(job_id).unwrap();
        assert!(directory.to_string_lossy().starts_with("items/by-job-"));
        let store = LibraryStore::open_in_memory().unwrap();
        let mut selected = item();
        selected.item_id = job_id.into();
        selected.assets.truncate(1);
        selected.assets[0].relative_path =
            directory.join("video.mp4").to_string_lossy().into_owned();
        persist(&store, &selected);
        let root = tempdir().unwrap();
        let (asset, partial, resume) =
            write_asset_family(root.path(), &selected.assets[0].relative_path, 4);
        delete_library_item_owned_assets(&store, root.path(), job_id).unwrap();
        assert!(!asset.exists());
        assert!(!partial.exists());
        assert!(!resume.exists());
        assert!(store.get(job_id).unwrap().is_none());
    }

    #[test]
    fn non_url_item_cannot_impersonate_a_url_downloads_hashed_directory() {
        let url_id = "https://fixture.invalid/watch/private";
        let reserved = owned_item_directory(url_id).unwrap();
        let spoofed_id = reserved.file_name().unwrap().to_str().unwrap();
        assert_eq!(
            owned_item_directory(spoofed_id).unwrap_err().kind,
            ErrorKind::InvalidInput
        );

        let store = LibraryStore::open_in_memory().unwrap();
        let mut forged = item();
        forged.item_id = spoofed_id.to_owned();
        forged.assets.truncate(1);
        forged.assets[0].relative_path =
            reserved.join("video.mp4").to_string_lossy().into_owned();
        persist(&store, &forged);
        let root = tempdir().unwrap();
        let (foreign, partial, resume) =
            write_asset_family(root.path(), &forged.assets[0].relative_path, 4);

        let error = delete_library_item_owned_assets(&store, root.path(), spoofed_id)
            .unwrap_err();
        assert_eq!(error.kind, ErrorKind::InvalidInput);
        assert!(foreign.exists());
        assert!(partial.exists());
        assert!(resume.exists());
        assert!(store.get(spoofed_id).unwrap().is_some());
    }

    #[test]
    fn ownership_rejects_invalid_ids_and_sibling_prefixes() {
        for invalid in ["", ".", "..", "item-1/../item-2", "item-1/child"] {
            assert_eq!(
                owned_item_directory(invalid).unwrap_err().kind,
                ErrorKind::InvalidInput
            );
        }
        let store = LibraryStore::open_in_memory().unwrap();
        let mut selected = item();
        selected.assets.truncate(1);
        selected.assets[0].relative_path = "items/item-1-extra/video.mp4".into();
        persist(&store, &selected);
        let root = tempdir().unwrap();
        let (foreign, _, _) = write_asset_family(root.path(), &selected.assets[0].relative_path, 4);
        assert_eq!(
            delete_library_item_owned_assets(&store, root.path(), "item-1")
                .unwrap_err()
                .kind,
            ErrorKind::InvalidInput
        );
        assert!(foreign.exists());
        assert!(store.get("item-1").unwrap().is_some());
    }
}
