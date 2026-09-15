use crate::{CoreError, ErrorKind, LibraryItem, LocalAsset};
use sha2::{Digest, Sha256};
use std::fs::File;
use std::io::Read;
use std::path::{Component, Path, PathBuf};

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum AssetIssue {
    Missing { asset_id: String },
    SizeMismatch { asset_id: String, expected: u64, actual: u64 },
    ChecksumMismatch { asset_id: String },
    UnsafePath { asset_id: String },
    Unreadable { asset_id: String },
}

pub fn validate_library_item(root: &Path, item: &LibraryItem) -> Vec<AssetIssue> {
    item.assets
        .iter()
        .filter_map(|asset| validate_asset(root, asset))
        .collect()
}

fn validate_asset(root: &Path, asset: &LocalAsset) -> Option<AssetIssue> {
    let path = match safe_asset_path(root, &asset.relative_path) {
        Ok(path) => path,
        Err(_) => return Some(AssetIssue::UnsafePath { asset_id: asset.asset_id.clone() }),
    };
    let metadata = match path.metadata() {
        Ok(metadata) if metadata.is_file() => metadata,
        Ok(_) => return Some(AssetIssue::Unreadable { asset_id: asset.asset_id.clone() }),
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => {
            return Some(AssetIssue::Missing { asset_id: asset.asset_id.clone() });
        }
        Err(_) => return Some(AssetIssue::Unreadable { asset_id: asset.asset_id.clone() }),
    };
    if metadata.len() != asset.bytes {
        return Some(AssetIssue::SizeMismatch {
            asset_id: asset.asset_id.clone(),
            expected: asset.bytes,
            actual: metadata.len(),
        });
    }
    if let Some(expected) = asset.sha256.as_deref() {
        match sha256_file(&path) {
            Ok(actual) if actual.eq_ignore_ascii_case(expected) => {}
            Ok(_) => return Some(AssetIssue::ChecksumMismatch { asset_id: asset.asset_id.clone() }),
            Err(_) => return Some(AssetIssue::Unreadable { asset_id: asset.asset_id.clone() }),
        }
    }
    None
}

fn safe_asset_path(root: &Path, relative: &str) -> Result<PathBuf, CoreError> {
    let relative = Path::new(relative);
    if relative.is_absolute()
        || relative.components().any(|component| {
            matches!(component, Component::ParentDir | Component::RootDir | Component::Prefix(_))
        })
    {
        return Err(CoreError::new(ErrorKind::InvalidInput, "unsafe library asset path", false));
    }
    Ok(root.join(relative))
}

fn sha256_file(path: &Path) -> std::io::Result<String> {
    let mut file = File::open(path)?;
    let mut hasher = Sha256::new();
    let mut buffer = [0_u8; 64 * 1024];
    loop {
        let read = file.read(&mut buffer)?;
        if read == 0 {
            break;
        }
        hasher.update(&buffer[..read]);
    }
    Ok(format!("{:x}", hasher.finalize()))
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::{MediaKind, SourceIdentity};
    use std::fs;

    fn item(relative_path: &str, bytes: u64, sha256: Option<String>) -> LibraryItem {
        LibraryItem {
            item_id: "one".into(),
            source: SourceIdentity::new("fixture", "one"),
            display_title: "One".into(),
            duration_ms: None,
            quality_label: "fixture".into(),
            assets: vec![LocalAsset {
                asset_id: "video".into(),
                kind: MediaKind::Video,
                relative_path: relative_path.into(),
                bytes,
                sha256,
                mime_type: Some("video/mp4".into()),
            }],
            created_at_epoch_ms: 0,
            playback_position_ms: 0,
            completed: true,
        }
    }

    #[test]
    fn valid_asset_has_no_issues() {
        let root = tempfile::tempdir().unwrap();
        fs::create_dir_all(root.path().join("items/one")).unwrap();
        fs::write(root.path().join("items/one/video.mp4"), b"abc").unwrap();
        let digest = format!("{:x}", Sha256::digest(b"abc"));
        assert!(validate_library_item(root.path(), &item("items/one/video.mp4", 3, Some(digest))).is_empty());
    }

    #[test]
    fn reports_missing_size_and_checksum_corruption() {
        let root = tempfile::tempdir().unwrap();
        let missing = validate_library_item(root.path(), &item("items/one/video.mp4", 3, None));
        assert!(matches!(missing.as_slice(), [AssetIssue::Missing { .. }]));

        fs::create_dir_all(root.path().join("items/one")).unwrap();
        fs::write(root.path().join("items/one/video.mp4"), b"abcd").unwrap();
        let size = validate_library_item(root.path(), &item("items/one/video.mp4", 3, None));
        assert!(matches!(size.as_slice(), [AssetIssue::SizeMismatch { .. }]));

        let checksum = validate_library_item(root.path(), &item("items/one/video.mp4", 4, Some("00".repeat(32))));
        assert!(matches!(checksum.as_slice(), [AssetIssue::ChecksumMismatch { .. }]));
    }

    #[test]
    fn rejects_path_traversal_before_touching_filesystem() {
        let root = tempfile::tempdir().unwrap();
        let issues = validate_library_item(root.path(), &item("../outside.mp4", 0, None));
        assert!(matches!(issues.as_slice(), [AssetIssue::UnsafePath { .. }]));
    }
}
