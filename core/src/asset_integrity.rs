use crate::domain::{CoreError, ErrorKind, LocalAsset};
use crate::security::validate_relative_library_path;
use sha2::{Digest, Sha256};
use std::fs::File;
use std::io::Read;
use std::path::Path;

const HASH_BUFFER_BYTES: usize = 64 * 1024;

/// Verifies a managed local asset for explicit/deep validation paths.
///
/// Cheap existence, file-kind, and size checks run first. When a persisted SHA-256 is present,
/// the file bytes are hashed and compared after the cheap checks pass so same-size corruption is
/// detected without forcing a hash for records that do not store one yet.
pub fn validate_local_asset_integrity(
    library_root: &Path,
    asset: &LocalAsset,
) -> Result<(), CoreError> {
    validate_relative_library_path(&asset.relative_path)?;
    let path = library_root.join(&asset.relative_path);
    let metadata = std::fs::metadata(&path).map_err(|_| {
        CoreError::new(
            ErrorKind::MissingAsset,
            format!("missing local asset {}", asset.asset_id),
            false,
        )
    })?;
    if !metadata.is_file() || metadata.len() != asset.bytes {
        return Err(CoreError::new(
            ErrorKind::CorruptAsset,
            format!("local asset {} has unexpected size", asset.asset_id),
            false,
        ));
    }
    if let Some(expected) = &asset.sha256 {
        let actual = sha256_file(&path)?;
        if !expected.eq_ignore_ascii_case(&actual) {
            return Err(CoreError::new(
                ErrorKind::CorruptAsset,
                format!("local asset {} failed checksum validation", asset.asset_id),
                false,
            ));
        }
    }
    Ok(())
}

fn sha256_file(path: &Path) -> Result<String, CoreError> {
    let mut file = File::open(path).map_err(hash_io_error)?;
    let mut hasher = Sha256::new();
    let mut buffer = vec![0_u8; HASH_BUFFER_BYTES];
    loop {
        let count = file.read(&mut buffer).map_err(hash_io_error)?;
        if count == 0 {
            break;
        }
        hasher.update(&buffer[..count]);
    }
    Ok(format!("{:x}", hasher.finalize()))
}

fn hash_io_error(error: std::io::Error) -> CoreError {
    CoreError::new(
        ErrorKind::Persistence,
        format!("failed to validate local asset integrity: {error}"),
        false,
    )
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::domain::{MediaKind, LocalAsset};
    use tempfile::tempdir;

    fn asset(bytes: u64, sha256: Option<String>) -> LocalAsset {
        LocalAsset {
            asset_id: "video".into(),
            kind: MediaKind::Video,
            relative_path: "items/item-1/video.mp4".into(),
            bytes,
            sha256,
            mime_type: Some("video/mp4".into()),
        }
    }

    fn write_asset(root: &Path, bytes: &[u8]) {
        let path = root.join("items/item-1/video.mp4");
        std::fs::create_dir_all(path.parent().unwrap()).unwrap();
        std::fs::write(path, bytes).unwrap();
    }

    #[test]
    fn cheap_existence_and_size_checks_are_preserved() {
        let root = tempdir().unwrap();
        let missing = validate_local_asset_integrity(root.path(), &asset(3, None)).unwrap_err();
        assert_eq!(missing.kind, ErrorKind::MissingAsset);

        write_asset(root.path(), b"abc");
        let wrong_size = validate_local_asset_integrity(root.path(), &asset(4, None)).unwrap_err();
        assert_eq!(wrong_size.kind, ErrorKind::CorruptAsset);
    }

    #[test]
    fn matching_stored_hash_passes_deep_validation() {
        let root = tempdir().unwrap();
        write_asset(root.path(), b"abc");
        let expected = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";

        validate_local_asset_integrity(root.path(), &asset(3, Some(expected.into()))).unwrap();
    }

    #[test]
    fn same_length_corruption_fails_when_stored_hash_exists() {
        let root = tempdir().unwrap();
        write_asset(root.path(), b"abd");
        let expected_for_abc =
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";

        let error = validate_local_asset_integrity(
            root.path(),
            &asset(3, Some(expected_for_abc.into())),
        )
        .unwrap_err();

        assert_eq!(error.kind, ErrorKind::CorruptAsset);
        assert!(error.message.contains("checksum"));
    }

    #[test]
    fn absent_hash_does_not_force_deep_validation() {
        let root = tempdir().unwrap();
        write_asset(root.path(), b"abd");

        validate_local_asset_integrity(root.path(), &asset(3, None)).unwrap();
    }
}
