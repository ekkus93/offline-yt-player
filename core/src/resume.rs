use crate::domain::{CoreError, ErrorKind};
use serde::{Deserialize, Serialize};
use std::fs;
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
        self.etag.as_deref().is_some_and(|value| !value.is_empty())
            || self
                .last_modified
                .as_deref()
                .is_some_and(|value| !value.is_empty())
    }

    /// A stored partial may only be reused when the URL is unchanged and a validator still
    /// identifies the same remote representation. Prefer ETag when both sides provide one;
    /// otherwise use Last-Modified. Known total length is an additional consistency check.
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
            (Some(stored), Some(now)) => stored == now,
            _ => match (&self.last_modified, &current.last_modified) {
                (Some(stored), Some(now)) => stored == now,
                _ => false,
            },
        }
    }
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
    fs::write(&temp, bytes).map_err(io_error)?;
    fs::rename(&temp, &path).map_err(io_error)
}

pub fn load_resume_representation(
    partial_path: &Path,
) -> Result<Option<ResumeRepresentation>, CoreError> {
    let path = resume_metadata_path(partial_path);
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
}
