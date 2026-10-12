use crate::{CoreError, ErrorKind, LibraryItem, LocalAsset};
use sha2::{Digest, Sha256};
use std::{fs::File, io::Read, path::Path};

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum AssetValidationDepth {
    Cheap,
    Deep,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum AssetHealth {
    Healthy,
    Missing,
    Corrupt { reason: String },
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct AssetValidation {
    pub asset_id: String,
    pub health: AssetHealth,
}

/// Validate persisted assets without trusting existence/length as proof of integrity.
///
/// Cheap validation checks existence and recorded length. Deep validation additionally verifies
/// SHA-256 whenever the asset has a stored hash. Corruption is returned as explicit, repairable
/// state so callers can offer recovery instead of treating the library item as playable.
pub fn validate_library_item_assets(
    library_root: &Path,
    item: &LibraryItem,
    depth: AssetValidationDepth,
) -> Result<Vec<AssetValidation>, CoreError> {
    if item.completed
        && !item.assets.iter().any(|asset| {
            matches!(asset.kind, crate::MediaKind::Video | crate::MediaKind::Audio)
        })
    {
        return Err(CoreError::new(
            ErrorKind::CorruptAsset,
            "completed library item has no playable media assets",
            false,
        ));
    }
    item.assets
        .iter()
        .map(|asset| validate_asset(library_root, asset, depth))
        .collect()
}

fn validate_asset(
    library_root: &Path,
    asset: &LocalAsset,
    depth: AssetValidationDepth,
) -> Result<AssetValidation, CoreError> {
    crate::validate_relative_library_path(&asset.relative_path)?;
    // A persisted library root can itself be an alias to an external directory.
    // Checking only components underneath it would still follow that symlink and
    // incorrectly mark an unrelated file as an owned, healthy private asset.
    match std::fs::symlink_metadata(library_root) {
        Ok(metadata) if metadata.file_type().is_symlink() => {
            return Ok(result(
                asset,
                AssetHealth::Corrupt {
                    reason: "library root is a symbolic link".into(),
                },
            ));
        }
        Ok(metadata) if !metadata.is_dir() => {
            return Ok(result(
                asset,
                AssetHealth::Corrupt {
                    reason: "library root is not a directory".into(),
                },
            ));
        }
        Ok(_) => {}
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => {
            return Ok(result(asset, AssetHealth::Missing));
        }
        Err(error) => return Err(validation_io_error(error)),
    }

    // Persisted paths may be restored from old metadata. Reject each symlink
    // component before following the asset path: metadata/read would otherwise
    // validate an unrelated file outside the private library as Healthy.
    let mut current = library_root.to_path_buf();
    for component in Path::new(&asset.relative_path).components() {
        current.push(component.as_os_str());
        match std::fs::symlink_metadata(&current) {
            Ok(metadata) if metadata.file_type().is_symlink() => {
                return Ok(result(
                    asset,
                    AssetHealth::Corrupt {
                        reason: "asset path contains a symbolic link".into(),
                    },
                ));
            }
            Ok(_) => {}
            Err(error) if error.kind() == std::io::ErrorKind::NotFound => {
                return Ok(result(asset, AssetHealth::Missing));
            }
            Err(error) => return Err(validation_io_error(error)),
        }
    }
    let path = library_root.join(&asset.relative_path);
    let metadata = match std::fs::metadata(&path) {
        Ok(metadata) if metadata.is_file() => metadata,
        Ok(_) => {
            return Ok(result(
                asset,
                AssetHealth::Corrupt {
                    reason: "asset path is not a regular file".into(),
                },
            ));
        }
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => {
            return Ok(result(asset, AssetHealth::Missing));
        }
        Err(error) => return Err(validation_io_error(error)),
    };
    if metadata.len() != asset.bytes {
        return Ok(result(
            asset,
            AssetHealth::Corrupt {
                reason: "asset length does not match persisted metadata".into(),
            },
        ));
    }
    if depth == AssetValidationDepth::Deep
        && let Some(expected) = asset.sha256.as_deref()
    {
        if !valid_sha256(expected) {
            return Ok(result(
                asset,
                AssetHealth::Corrupt {
                    reason: "persisted SHA-256 is malformed".into(),
                },
            ));
        }
        let actual = sha256_file(&path)?;
        if !actual.eq_ignore_ascii_case(expected) {
            return Ok(result(
                asset,
                AssetHealth::Corrupt {
                    reason: "asset SHA-256 does not match persisted metadata".into(),
                },
            ));
        }
    }
    Ok(result(asset, AssetHealth::Healthy))
}

fn result(asset: &LocalAsset, health: AssetHealth) -> AssetValidation {
    AssetValidation {
        asset_id: asset.asset_id.clone(),
        health,
    }
}

fn valid_sha256(value: &str) -> bool {
    value.len() == 64 && value.bytes().all(|byte| byte.is_ascii_hexdigit())
}

fn sha256_file(path: &Path) -> Result<String, CoreError> {
    let mut file = File::open(path).map_err(validation_io_error)?;
    let mut hasher = Sha256::new();
    let mut buffer = [0_u8; 64 * 1024];
    loop {
        let read = file.read(&mut buffer).map_err(validation_io_error)?;
        if read == 0 {
            break;
        }
        hasher.update(&buffer[..read]);
    }
    Ok(format!("{:x}", hasher.finalize()))
}

fn validation_io_error(error: std::io::Error) -> CoreError {
    CoreError::new(
        ErrorKind::Persistence,
        format!("failed to validate local asset: {error}"),
        false,
    )
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::{MediaKind, SourceIdentity};
    use tempfile::tempdir;

    fn item(bytes: u64, sha256: Option<String>) -> LibraryItem {
        LibraryItem {
            item_id: "item-1".into(),
            source: SourceIdentity::new("fixture", "source-1"),
            display_title: "Fixture".into(),
            duration_ms: None,
            quality_label: "fixture".into(),
            assets: vec![LocalAsset {
                asset_id: "video".into(),
                kind: MediaKind::Video,
                relative_path: "items/item-1/video.bin".into(),
                bytes,
                sha256,
                mime_type: None,
            }],
            created_at_epoch_ms: 1,
            playback_position_ms: 0,
            completed: true,
        }
    }

    fn write_asset(root: &Path, bytes: &[u8]) {
        let path = root.join("items/item-1/video.bin");
        std::fs::create_dir_all(path.parent().unwrap()).unwrap();
        std::fs::write(path, bytes).unwrap();
    }

    #[cfg(unix)]
    #[test]
    fn symlinked_library_root_never_validates_external_assets_as_healthy() {
        use std::os::unix::fs::symlink;

        let app = tempdir().unwrap();
        let external = tempdir().unwrap();
        write_asset(external.path(), b"good");
        let sha256 = format!("{:x}", Sha256::digest(b"good"));
        let item = item(4, Some(sha256));
        let linked_root = app.path().join("untrusted-library");
        symlink(external.path(), &linked_root).unwrap();

        for depth in [AssetValidationDepth::Cheap, AssetValidationDepth::Deep] {
            let checks = validate_library_item_assets(&linked_root, &item, depth).unwrap();
            assert!(matches!(checks[0].health, AssetHealth::Corrupt { .. }));
        }
        // A missing or non-directory root cannot be treated as a healthy asset.
        let missing = validate_library_item_assets(
            &app.path().join("missing"),
            &item,
            AssetValidationDepth::Cheap,
        )
        .unwrap();
        assert_eq!(missing[0].health, AssetHealth::Missing);
        let regular_file = app.path().join("not-a-directory");
        std::fs::write(&regular_file, b"data").unwrap();
        let invalid =
            validate_library_item_assets(&regular_file, &item, AssetValidationDepth::Deep).unwrap();
        assert!(matches!(invalid[0].health, AssetHealth::Corrupt { .. }));
        assert_eq!(
            std::fs::read(external.path().join("items/item-1/video.bin")).unwrap(),
            b"good"
        );
    }

    #[cfg(unix)]
    #[test]
    fn symlinked_file_and_parent_never_validate_external_assets_as_healthy() {
        use std::os::unix::fs::symlink;

        let root = tempdir().unwrap();
        let outside = tempdir().unwrap();
        let external_file = outside.path().join("video.bin");
        std::fs::write(&external_file, b"good").unwrap();
        let expected = format!("{:x}", Sha256::digest(b"good"));
        let item = item(4, Some(expected));
        let parent = root.path().join("items/item-1");
        std::fs::create_dir_all(&parent).unwrap();

        symlink(&external_file, parent.join("video.bin")).unwrap();
        for depth in [AssetValidationDepth::Cheap, AssetValidationDepth::Deep] {
            let checks = validate_library_item_assets(root.path(), &item, depth).unwrap();
            assert!(matches!(checks[0].health, AssetHealth::Corrupt { .. }));
        }
        std::fs::remove_file(parent.join("video.bin")).unwrap();
        std::fs::remove_dir(&parent).unwrap();
        symlink(outside.path(), &parent).unwrap();
        let checks =
            validate_library_item_assets(root.path(), &item, AssetValidationDepth::Deep).unwrap();
        assert!(matches!(checks[0].health, AssetHealth::Corrupt { .. }));
        assert_eq!(std::fs::read(&external_file).unwrap(), b"good");
    }

    #[test]
    fn completed_items_without_playable_assets_fail_integrity_validation() {
        let root = tempdir().unwrap();
        let mut empty = item(0, None);
        empty.assets.clear();
        assert_eq!(validate_library_item_assets(root.path(), &empty, AssetValidationDepth::Deep).unwrap_err().kind, ErrorKind::CorruptAsset);
        let mut thumbnail_only = item(4, None);
        thumbnail_only.assets[0].kind = MediaKind::Thumbnail;
        assert_eq!(validate_library_item_assets(root.path(), &thumbnail_only, AssetValidationDepth::Cheap).unwrap_err().kind, ErrorKind::CorruptAsset);
    }

    #[test]
    fn deep_validation_detects_same_length_corruption_using_stored_hash() {
        let root = tempdir().unwrap();
        let original = b"good";
        let expected = format!("{:x}", Sha256::digest(original));
        let item = item(original.len() as u64, Some(expected));
        write_asset(root.path(), b"evil");

        let cheap =
            validate_library_item_assets(root.path(), &item, AssetValidationDepth::Cheap).unwrap();
        assert_eq!(cheap[0].health, AssetHealth::Healthy);
        let deep =
            validate_library_item_assets(root.path(), &item, AssetValidationDepth::Deep).unwrap();
        assert!(matches!(deep[0].health, AssetHealth::Corrupt { .. }));
    }

    #[test]
    fn deep_validation_accepts_matching_hash() {
        let root = tempdir().unwrap();
        let contents = b"good";
        let expected = format!("{:x}", Sha256::digest(contents));
        let item = item(contents.len() as u64, Some(expected));
        write_asset(root.path(), contents);
        let result =
            validate_library_item_assets(root.path(), &item, AssetValidationDepth::Deep).unwrap();
        assert_eq!(result[0].health, AssetHealth::Healthy);
    }

    #[test]
    fn missing_and_wrong_length_assets_are_explicit_repairable_states() {
        let root = tempdir().unwrap();
        let item = item(4, None);
        let missing =
            validate_library_item_assets(root.path(), &item, AssetValidationDepth::Cheap).unwrap();
        assert_eq!(missing[0].health, AssetHealth::Missing);
        write_asset(root.path(), b"too long");
        let corrupt =
            validate_library_item_assets(root.path(), &item, AssetValidationDepth::Cheap).unwrap();
        assert!(matches!(corrupt[0].health, AssetHealth::Corrupt { .. }));
    }
}
