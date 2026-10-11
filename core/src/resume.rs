use crate::domain::{CoreError, ErrorKind};
use serde::{Deserialize, Serialize};
use std::fs::{self, OpenOptions};
use std::io::{ErrorKind as IoErrorKind, Write};
use std::path::{Path, PathBuf};

/// Durable identity for the remote representation associated with a partial download.
///
/// At least one validator must be present before a partial is eligible for append/reuse. This
/// prevents a same-URL resource that changed between process lifetimes from being concatenated
/// with stale bytes.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct ResumeRepresentation {
    pub url: String,
    pub etag: Option<String>,
    pub last_modified: Option<String>,
    pub total_bytes: Option<u64>,
}

impl ResumeRepresentation {
    #[must_use]
    pub fn has_remote_validator(&self) -> bool {
        match self.etag.as_deref() {
            // Weak ETags assert semantic equivalence, not byte-for-byte
            // identity, so they cannot authorize appending a byte range.
            Some(etag) => is_strong_etag(etag),
            None => self
                .last_modified
                .as_deref()
                .is_some_and(|value| !value.trim().is_empty()),
        }
    }

    /// A stored partial may only be reused when the URL is unchanged and a validator still
    /// identifies the same remote representation. Strong ETags take precedence; Last-Modified
    /// is a fallback only when neither response supplies an ETag. A weak ETag or validator
    /// downgrade cannot prove byte identity. Known total length is an additional check.
    #[must_use]
    pub fn matches(&self, current: &Self) -> bool {
        if self.url != current.url
            || !self.has_remote_validator()
            || !current.has_remote_validator()
        {
            return false;
        }
        if self.total_bytes.is_some()
            && current.total_bytes.is_some()
            && self.total_bytes != current.total_bytes
        {
            return false;
        }
        match (&self.etag, &current.etag) {
            (Some(stored), Some(now)) => {
                is_strong_etag(stored) && is_strong_etag(now) && stored == now
            }
            (None, None) => match (&self.last_modified, &current.last_modified) {
                (Some(stored), Some(now)) => !stored.trim().is_empty() && stored == now,
                _ => false,
            },
            _ => false,
        }
    }
}

fn is_strong_etag(value: &str) -> bool {
    let value = value.trim();
    !value.is_empty()
        && !value
            .get(..2)
            .is_some_and(|prefix| prefix.eq_ignore_ascii_case("W/"))
}

#[must_use]
pub fn resume_metadata_path(partial_path: &Path) -> PathBuf {
    let name = partial_path
        .file_name()
        .and_then(|value| value.to_str())
        .unwrap_or("asset.partial");
    partial_path.with_file_name(format!("{name}.resume.json"))
}

pub fn save_resume_representation(
    partial_path: &Path,
    representation: &ResumeRepresentation,
) -> Result<(), CoreError> {
    if !representation.has_remote_validator() {
        return Err(CoreError::new(
            ErrorKind::IntegrityFailure,
            "Cannot persist resumable state without a remote representation validator",
            false,
        ));
    }
    let bytes = serde_json::to_vec(representation).map_err(|error| {
        CoreError::new(
            ErrorKind::Internal,
            format!("Unable to encode resume metadata: {error}"),
            false,
        )
    })?;
    let path = resume_metadata_path(partial_path);
    let temp = path.with_extension("json.tmp");
    // The library root can contain imported files. Never follow an attacker-
    // supplied sidecar/temp symlink when persisting signed URL metadata.
    reject_nonregular_resume_path(&path)?;
    match fs::symlink_metadata(&temp) {
        Ok(metadata) if metadata.is_file() => fs::remove_file(&temp).map_err(io_error)?,
        Ok(_) => {
            return Err(CoreError::new(
                ErrorKind::InvalidInput,
                "Resume metadata temp path must be a regular file",
                false,
            ));
        }
        Err(error) if error.kind() == IoErrorKind::NotFound => {}
        Err(error) => return Err(io_error(error)),
    }
    // create_new refuses symlinks even if the path changes after inspection.
    let mut file = OpenOptions::new()
        .write(true)
        .create_new(true)
        .open(&temp)
        .map_err(io_error)?;
    file.write_all(&bytes).map_err(io_error)?;
    file.sync_all().map_err(io_error)?;
    drop(file);
    fs::rename(&temp, &path).map_err(io_error)
}

fn reject_nonregular_resume_path(path: &Path) -> Result<(), CoreError> {
    match fs::symlink_metadata(path) {
        Ok(metadata) if metadata.is_file() => Ok(()),
        Ok(_) => Err(CoreError::new(
            ErrorKind::InvalidInput,
            "Resume metadata path must be a regular file",
            false,
        )),
        Err(error) if error.kind() == IoErrorKind::NotFound => Ok(()),
        Err(error) => Err(io_error(error)),
    }
}

pub fn load_resume_representation(
    partial_path: &Path,
) -> Result<Option<ResumeRepresentation>, CoreError> {
    let path = resume_metadata_path(partial_path);
    reject_nonregular_resume_path(&path)?;
    let bytes = match fs::read(path) {
        Ok(bytes) => bytes,
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => return Ok(None),
        Err(error) => return Err(io_error(error)),
    };
    serde_json::from_slice(&bytes).map(Some).map_err(|_| {
        CoreError::new(
            ErrorKind::IntegrityFailure,
            "Stored resume metadata is corrupt",
            false,
        )
    })
}

pub fn clear_resume_representation(partial_path: &Path) -> Result<(), CoreError> {
    let path = resume_metadata_path(partial_path);
    match fs::remove_file(path) {
        Ok(()) => Ok(()),
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => Ok(()),
        Err(error) => Err(io_error(error)),
    }
}

/// Decide whether an existing partial may be appended to for the currently observed remote
/// representation. Unsafe or unproven partials are removed together with their sidecar before
/// returning `false`, so callers cannot accidentally append merely because bytes happen to exist.
///
/// Corrupt sidecars remain an explicit integrity failure rather than being silently discarded:
/// callers should surface/reconcile corruption deliberately instead of converting it into an
/// apparently clean restart.
pub fn prepare_partial_reuse(
    partial_path: &Path,
    current: &ResumeRepresentation,
) -> Result<bool, CoreError> {
    if !partial_path.exists() {
        clear_resume_representation(partial_path)?;
        return Ok(false);
    }

    let stored = load_resume_representation(partial_path)?;
    if stored
        .as_ref()
        .is_some_and(|stored| stored.matches(current))
    {
        return Ok(true);
    }

    match fs::remove_file(partial_path) {
        Ok(()) => {}
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => {}
        Err(error) => return Err(io_error(error)),
    }
    clear_resume_representation(partial_path)?;
    Ok(false)
}

fn io_error(error: std::io::Error) -> CoreError {
    CoreError::new(
        ErrorKind::Internal,
        format!("resume metadata storage error: {error}"),
        false,
    )
}

#[cfg(test)]
mod tests {
    use super::*;

    fn representation(etag: Option<&str>, modified: Option<&str>) -> ResumeRepresentation {
        ResumeRepresentation {
            url: "https://fixture.invalid/media".into(),
            etag: etag.map(str::to_owned),
            last_modified: modified.map(str::to_owned),
            total_bytes: Some(100),
        }
    }

    #[test]
    fn validator_is_required_for_reuse() {
        let none = representation(None, None);
        assert!(!none.has_remote_validator());
        assert!(!none.matches(&none));
    }

    #[test]
    fn etag_identity_must_match() {
        assert!(representation(Some("v1"), None).matches(&representation(Some("v1"), None)));
        assert!(!representation(Some("v1"), None).matches(&representation(Some("v2"), None)));
    }

    #[test]
    fn weak_etag_never_authorizes_byte_range_append() {
        let weak = representation(Some("W/\"fixture-v1\""), None);
        assert!(!weak.has_remote_validator());
        assert!(!weak.matches(&weak));
        let weak_with_date = representation(
            Some("W/\"fixture-v1\""),
            Some("Wed, 16 Sep 2026 00:00:00 GMT"),
        );
        assert!(!weak_with_date.has_remote_validator());
        assert!(!weak_with_date.matches(&weak_with_date));
        assert!(!representation(Some("\"fixture-v1\""), None).matches(&weak));
    }

    #[test]
    fn etag_presence_change_cannot_silently_downgrade_to_date() {
        let strong = representation(
            Some("\"fixture-v1\""),
            Some("Wed, 16 Sep 2026 00:00:00 GMT"),
        );
        let date_only = representation(None, Some("Wed, 16 Sep 2026 00:00:00 GMT"));
        assert!(!strong.matches(&date_only));
        assert!(!date_only.matches(&strong));
    }

    #[test]
    fn last_modified_is_valid_fallback() {
        assert!(
            representation(None, Some("Wed, 16 Sep 2026 00:00:00 GMT"))
                .matches(&representation(None, Some("Wed, 16 Sep 2026 00:00:00 GMT")))
        );
    }

    #[test]
    fn size_or_url_change_invalidates_partial() {
        let stored = representation(Some("v1"), None);
        let mut changed = stored.clone();
        changed.total_bytes = Some(101);
        assert!(!stored.matches(&changed));
        changed = stored.clone();
        changed.url = "https://fixture.invalid/other".into();
        assert!(!stored.matches(&changed));
    }

    #[test]
    fn metadata_round_trips_and_clears() {
        let temp = tempfile::tempdir().unwrap();
        let partial = temp.path().join(".video.mp4.partial");
        let expected = representation(Some("fixture-v1"), None);
        save_resume_representation(&partial, &expected).unwrap();
        assert_eq!(
            load_resume_representation(&partial).unwrap(),
            Some(expected)
        );
        clear_resume_representation(&partial).unwrap();
        assert_eq!(load_resume_representation(&partial).unwrap(), None);
    }

    #[test]
    fn corrupt_metadata_is_not_treated_as_resumable() {
        let temp = tempfile::tempdir().unwrap();
        let partial = temp.path().join(".video.mp4.partial");
        fs::write(resume_metadata_path(&partial), b"not-json").unwrap();
        let error = load_resume_representation(&partial).unwrap_err();
        assert_eq!(error.kind, ErrorKind::IntegrityFailure);
    }

    #[test]
    fn matching_representation_keeps_partial_for_append() {
        let temp = tempfile::tempdir().unwrap();
        let partial = temp.path().join(".video.mp4.partial");
        let current = representation(Some("fixture-v1"), None);
        fs::write(&partial, b"partial bytes").unwrap();
        save_resume_representation(&partial, &current).unwrap();

        assert!(prepare_partial_reuse(&partial, &current).unwrap());
        assert_eq!(fs::read(&partial).unwrap(), b"partial bytes");
        assert_eq!(load_resume_representation(&partial).unwrap(), Some(current));
    }

    #[test]
    fn changed_representation_discards_partial_and_sidecar() {
        let temp = tempfile::tempdir().unwrap();
        let partial = temp.path().join(".video.mp4.partial");
        let stored = representation(Some("fixture-v1"), None);
        let current = representation(Some("fixture-v2"), None);
        fs::write(&partial, b"stale bytes").unwrap();
        save_resume_representation(&partial, &stored).unwrap();

        assert!(!prepare_partial_reuse(&partial, &current).unwrap());
        assert!(!partial.exists());
        assert_eq!(load_resume_representation(&partial).unwrap(), None);
    }

    #[test]
    fn partial_without_sidecar_is_never_reused() {
        let temp = tempfile::tempdir().unwrap();
        let partial = temp.path().join(".video.mp4.partial");
        fs::write(&partial, b"unproven bytes").unwrap();

        assert!(
            !prepare_partial_reuse(&partial, &representation(Some("fixture-v1"), None)).unwrap()
        );
        assert!(!partial.exists());
    }

    #[cfg(unix)]
    #[test]
    fn resume_sidecar_symlink_cannot_read_or_replace_outside_file() {
        use std::os::unix::fs::symlink;

        let temp = tempfile::tempdir().unwrap();
        let outside = tempfile::tempdir().unwrap();
        let outside_path = outside.path().join("private.json");
        fs::write(&outside_path, b"outside private bytes").unwrap();
        let partial = temp.path().join(".video.mp4.partial");
        let sidecar = resume_metadata_path(&partial);
        symlink(&outside_path, &sidecar).unwrap();

        let error = load_resume_representation(&partial).unwrap_err();
        assert_eq!(error.kind, ErrorKind::InvalidInput);
        let error =
            save_resume_representation(&partial, &representation(Some("\"fixture-v1\""), None))
                .unwrap_err();
        assert_eq!(error.kind, ErrorKind::InvalidInput);
        assert_eq!(fs::read(&outside_path).unwrap(), b"outside private bytes");
    }

    #[cfg(unix)]
    #[test]
    fn resume_temp_symlink_cannot_overwrite_outside_file() {
        use std::os::unix::fs::symlink;

        let temp = tempfile::tempdir().unwrap();
        let outside = tempfile::tempdir().unwrap();
        let outside_path = outside.path().join("private.json");
        fs::write(&outside_path, b"outside private bytes").unwrap();
        let partial = temp.path().join(".video.mp4.partial");
        let sidecar = resume_metadata_path(&partial);
        symlink(&outside_path, sidecar.with_extension("json.tmp")).unwrap();

        let error =
            save_resume_representation(&partial, &representation(Some("\"fixture-v1\""), None))
                .unwrap_err();
        assert_eq!(error.kind, ErrorKind::InvalidInput);
        assert_eq!(fs::read(&outside_path).unwrap(), b"outside private bytes");
        assert!(!sidecar.exists());
    }

    #[test]
    fn stale_regular_resume_temp_is_recovered() {
        let temp = tempfile::tempdir().unwrap();
        let partial = temp.path().join(".video.mp4.partial");
        let sidecar = resume_metadata_path(&partial);
        fs::write(sidecar.with_extension("json.tmp"), b"stale crash temp").unwrap();
        let expected = representation(Some("\"fixture-v1\""), None);
        save_resume_representation(&partial, &expected).unwrap();
        assert_eq!(
            load_resume_representation(&partial).unwrap(),
            Some(expected)
        );
    }

    #[test]
    fn missing_partial_clears_stale_sidecar() {
        let temp = tempfile::tempdir().unwrap();
        let partial = temp.path().join(".video.mp4.partial");
        save_resume_representation(&partial, &representation(Some("fixture-v1"), None)).unwrap();

        assert!(
            !prepare_partial_reuse(&partial, &representation(Some("fixture-v1"), None)).unwrap()
        );
        assert_eq!(load_resume_representation(&partial).unwrap(), None);
    }
}
