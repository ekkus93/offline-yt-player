use super::*;

pub(super) fn escape_like(value: &str) -> String {
    value
        .replace('\\', "\\\\")
        .replace('%', "\\%")
        .replace('_', "\\_")
}

pub(super) fn validate_relative_asset_path(value: &str) -> Result<(), CoreError> {
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

pub(super) fn upsert_item(
    transaction: &Transaction<'_>,
    item: &LibraryItem,
) -> Result<(), CoreError> {
    transaction
        .execute(
            "INSERT INTO library_items(item_id, provider, source_media_id, canonical_url,
                 display_title, duration_ms, quality_label, created_at_epoch_ms,
                 playback_position_ms, completed)
             VALUES(?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10)
             ON CONFLICT(item_id) DO UPDATE SET
               provider=excluded.provider,
               source_media_id=excluded.source_media_id,
               canonical_url=excluded.canonical_url,
               display_title=excluded.display_title,
               duration_ms=excluded.duration_ms,
               quality_label=excluded.quality_label,
               created_at_epoch_ms=excluded.created_at_epoch_ms,
               playback_position_ms=excluded.playback_position_ms,
               completed=excluded.completed",
            params![
                item.item_id,
                item.source.provider,
                item.source.media_id,
                item.source.canonical_url,
                item.display_title,
                item.duration_ms.map(to_i64).transpose()?,
                item.quality_label,
                to_i64(item.created_at_epoch_ms)?,
                to_i64(item.playback_position_ms)?,
                i64::from(item.completed)
            ],
        )
        .map_err(db_error)?;
    Ok(())
}

pub(super) fn insert_asset(
    transaction: &Transaction<'_>,
    item_id: &str,
    asset: &LocalAsset,
) -> Result<(), CoreError> {
    transaction
        .execute(
            "INSERT INTO assets(item_id, asset_id, kind_json, relative_path, bytes, sha256, mime_type)
             VALUES(?1, ?2, ?3, ?4, ?5, ?6, ?7)",
            params![
                item_id,
                asset.asset_id,
                serde_json::to_string(&asset.kind).map_err(json_error)?,
                asset.relative_path,
                to_i64(asset.bytes)?,
                asset.sha256,
                asset.mime_type
            ],
        )
        .map_err(db_error)?;
    Ok(())
}

pub(super) fn map_item_row(row: &rusqlite::Row<'_>) -> rusqlite::Result<LibraryItem> {
    Ok(LibraryItem {
        item_id: row.get(0)?,
        source: SourceIdentity {
            provider: row.get(1)?,
            media_id: row.get(2)?,
            canonical_url: row.get(3)?,
        },
        display_title: row.get(4)?,
        duration_ms: row.get::<_, Option<i64>>(5)?.map(from_i64).transpose()?,
        quality_label: row.get(6)?,
        assets: Vec::new(),
        created_at_epoch_ms: from_i64(row.get(7)?)?,
        playback_position_ms: from_i64(row.get(8)?)?,
        completed: row.get::<_, i64>(9)? != 0,
    })
}

pub(super) fn promote_item_transaction(
    transaction: &Transaction<'_>,
    job_id: &str,
    item: &LibraryItem,
) -> Result<(), CoreError> {
    upsert_item(transaction, item)?;
    transaction
        .execute(
            "DELETE FROM assets WHERE item_id=?1",
            [item.item_id.as_str()],
        )
        .map_err(db_error)?;
    for asset in &item.assets {
        insert_asset(transaction, &item.item_id, asset)?;
    }
    transaction
        .execute("DELETE FROM staged_assets WHERE job_id=?1", [job_id])
        .map_err(db_error)?;
    Ok(())
}

pub(super) fn load_assets(
    connection: &Connection,
    item_id: &str,
) -> Result<Vec<LocalAsset>, CoreError> {
    let mut statement = connection
        .prepare(
            "SELECT asset_id, kind_json, relative_path, bytes, sha256, mime_type
             FROM assets WHERE item_id=?1 ORDER BY asset_id",
        )
        .map_err(db_error)?;
    let rows = statement
        .query_map([item_id], |row| {
            let kind_json: String = row.get(1)?;
            let kind = serde_json::from_str(&kind_json).map_err(|error| {
                rusqlite::Error::FromSqlConversionFailure(
                    1,
                    rusqlite::types::Type::Text,
                    Box::new(error),
                )
            })?;
            Ok(LocalAsset {
                asset_id: row.get(0)?,
                kind,
                relative_path: row.get(2)?,
                bytes: from_i64(row.get(3)?)?,
                sha256: row.get(4)?,
                mime_type: row.get(5)?,
            })
        })
        .map_err(db_error)?;
    rows.collect::<Result<Vec<_>, _>>().map_err(db_error)
}

pub(super) fn to_i64(value: u64) -> Result<i64, CoreError> {
    i64::try_from(value).map_err(|_| {
        CoreError::new(
            ErrorKind::Persistence,
            "numeric value exceeds SQLite range",
            false,
        )
    })
}

pub(super) fn from_i64(value: i64) -> rusqlite::Result<u64> {
    u64::try_from(value).map_err(|error| {
        rusqlite::Error::FromSqlConversionFailure(
            0,
            rusqlite::types::Type::Integer,
            Box::new(error),
        )
    })
}

pub(super) fn db_error(error: rusqlite::Error) -> CoreError {
    CoreError::new(
        ErrorKind::Persistence,
        format!("database error: {error}"),
        false,
    )
}

pub(super) fn json_error(error: serde_json::Error) -> CoreError {
    CoreError::new(
        ErrorKind::Persistence,
        format!("serialization error: {error}"),
        false,
    )
}
