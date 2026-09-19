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
