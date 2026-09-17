use crate::{CoreError, ErrorKind, LibraryItem, LocalAsset, MediaKind};
use std::path::{Component, Path};

pub const THUMBNAIL_ASSET_ID: &str = "thumbnail";
pub const THUMBNAIL_DIRECTORY: &str = "thumbnails";

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ThumbnailAssetPlan {
    pub asset_id: String,
    pub relative_path: String,
    pub mime_type: String,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ThumbnailCleanupDisposition {
    Keep,
    RemoveOrphan,
}

pub fn thumbnail_asset_plan(
    item_id: &str,
    extension: &str,
    mime_type: &str,
) -> Result<ThumbnailAssetPlan, CoreError> {
    let item_id = safe_segment(item_id, "item id")?;
    let extension = safe_extension(extension)?;
    if !mime_type.starts_with("image/") {
        return Err(CoreError::new(
            ErrorKind::InvalidInput,
            "thumbnail mime type must be an image",
            false,
        ));
    }
    Ok(ThumbnailAssetPlan {
        asset_id: THUMBNAIL_ASSET_ID.into(),
        relative_path: format!(
            "items/{item_id}/{THUMBNAIL_DIRECTORY}/{THUMBNAIL_ASSET_ID}.{extension}"
        ),
        mime_type: mime_type.into(),
    })
}

pub fn offline_thumbnail_asset(item: &LibraryItem) -> Option<&LocalAsset> {
    item.assets.iter().find(|asset| {
        asset.kind == MediaKind::Thumbnail && is_safe_relative_path(&asset.relative_path)
    })
}

pub fn classify_thumbnail_cleanup(
    asset: &LocalAsset,
    owning_item_exists: bool,
) -> ThumbnailCleanupDisposition {
    if asset.kind == MediaKind::Thumbnail && !owning_item_exists {
        ThumbnailCleanupDisposition::RemoveOrphan
    } else {
        ThumbnailCleanupDisposition::Keep
    }
}

fn safe_segment(value: &str, label: &str) -> Result<String, CoreError> {
    let trimmed = value.trim();
    if trimmed.is_empty()
        || trimmed.contains('/')
        || trimmed.contains('\\')
        || trimmed.contains("..")
        || trimmed.contains(':')
    {
        return Err(CoreError::new(
            ErrorKind::InvalidInput,
            format!("{label} must be a safe path segment"),
            false,
        ));
    }
    Ok(trimmed.into())
}

fn safe_extension(value: &str) -> Result<String, CoreError> {
    let trimmed = value.trim().trim_start_matches('.');
    if trimmed.is_empty()
        || trimmed.contains('/')
        || trimmed.contains('\\')
        || trimmed.contains("..")
        || trimmed.contains(':')
    {
        return Err(CoreError::new(
            ErrorKind::InvalidInput,
            "thumbnail extension must be safe",
            false,
        ));
    }
    Ok(trimmed.into())
}

fn is_safe_relative_path(value: &str) -> bool {
    let path = Path::new(value);
    !value.is_empty()
        && !path.is_absolute()
        && !path.components().any(|component| {
            matches!(
                component,
                Component::ParentDir | Component::RootDir | Component::Prefix(_)
            )
        })
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::{LibraryItem, SourceIdentity};

    fn item_with(asset: LocalAsset) -> LibraryItem {
        LibraryItem {
            item_id: "item-1".into(),
            source: SourceIdentity::new("fixture", "source-1"),
            display_title: "Fixture".into(),
            duration_ms: Some(60_000),
            quality_label: "720p".into(),
            assets: vec![asset],
            created_at_epoch_ms: 1,
            playback_position_ms: 0,
            completed: true,
        }
    }

    #[test]
    fn thumbnail_plan_uses_safe_local_library_path() {
        let plan = thumbnail_asset_plan("item-1", ".jpg", "image/jpeg").unwrap();

        assert_eq!(THUMBNAIL_ASSET_ID, plan.asset_id);
        assert_eq!("items/item-1/thumbnails/thumbnail.jpg", plan.relative_path);
        assert_eq!("image/jpeg", plan.mime_type);
    }

    #[test]
    fn thumbnail_plan_rejects_path_traversal_and_non_images() {
        assert!(thumbnail_asset_plan("../item", "jpg", "image/jpeg").is_err());
        assert!(thumbnail_asset_plan("item-1", "../jpg", "image/jpeg").is_err());
        assert!(thumbnail_asset_plan("item-1", "jpg", "text/plain").is_err());
    }

    #[test]
    fn offline_display_uses_persisted_thumbnail_asset_only() {
        let thumbnail = LocalAsset {
            asset_id: THUMBNAIL_ASSET_ID.into(),
            kind: MediaKind::Thumbnail,
            relative_path: "items/item-1/thumbnails/thumbnail.webp".into(),
            bytes: 42,
            sha256: None,
            mime_type: Some("image/webp".into()),
        };

        let item = item_with(thumbnail.clone());
        assert_eq!(Some(&thumbnail), offline_thumbnail_asset(&item));
    }

    #[test]
    fn cleanup_removes_orphaned_thumbnail_assets_only() {
        let thumbnail = LocalAsset {
            asset_id: THUMBNAIL_ASSET_ID.into(),
            kind: MediaKind::Thumbnail,
            relative_path: "items/item-1/thumbnails/thumbnail.jpg".into(),
            bytes: 42,
            sha256: None,
            mime_type: Some("image/jpeg".into()),
        };
        let video = LocalAsset {
            asset_id: "video".into(),
            kind: MediaKind::Video,
            relative_path: "items/item-1/video.mp4".into(),
            bytes: 100,
            sha256: None,
            mime_type: Some("video/mp4".into()),
        };

        assert_eq!(
            ThumbnailCleanupDisposition::RemoveOrphan,
            classify_thumbnail_cleanup(&thumbnail, false),
        );
        assert_eq!(
            ThumbnailCleanupDisposition::Keep,
            classify_thumbnail_cleanup(&thumbnail, true),
        );
        assert_eq!(
            ThumbnailCleanupDisposition::Keep,
            classify_thumbnail_cleanup(&video, false),
        );
    }
}
