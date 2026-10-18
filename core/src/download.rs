use crate::domain::{CoreError, ErrorKind};
use crate::resume::{
    clear_resume_representation, prepare_partial_reuse, save_resume_representation,
};
use crate::resume_http::representation_from_headers;
use crate::security::{validate_http_url, validate_relative_library_path};
use reqwest::blocking::{Client, Response};
use reqwest::header::{CONTENT_LENGTH, RANGE};
use std::error::Error as StdError;
use std::fs::{self, File, OpenOptions};
use std::io::{ErrorKind as IoErrorKind, Read, Seek, SeekFrom, Write};
use std::path::{Path, PathBuf};
use std::sync::atomic::{AtomicBool, Ordering};
use std::time::Duration;

mod errors;
mod integrity;
mod library_paths;
#[cfg(test)]
mod tests;

use errors::*;
use integrity::*;
use library_paths::*;

const DEFAULT_CONNECT_TIMEOUT: Duration = Duration::from_secs(15);
const DEFAULT_REQUEST_TIMEOUT: Duration = Duration::from_secs(60);
const COPY_BUFFER_BYTES: usize = 64 * 1024;

#[derive(Debug, Clone)]
pub struct DownloadPolicy {
    pub connect_timeout: Duration,
    pub request_timeout: Duration,
    pub max_attempts: u32,
    pub max_asset_bytes: u64,
    pub retain_partial_on_cancel: bool,
}

impl Default for DownloadPolicy {
    fn default() -> Self {
        Self {
            connect_timeout: DEFAULT_CONNECT_TIMEOUT,
            request_timeout: DEFAULT_REQUEST_TIMEOUT,
            max_attempts: 4,
            max_asset_bytes: 64 * 1024 * 1024 * 1024,
            retain_partial_on_cancel: true,
        }
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct TransferRequest {
    pub url: String,
    pub relative_path: String,
    pub expected_bytes: Option<u64>,
    pub expected_sha256: Option<String>,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct TransferResult {
    pub relative_path: String,
    pub bytes: u64,
    pub sha256: String,
    pub resumed: bool,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum FailureClass {
    Retryable,
    Permanent,
}

#[must_use]
pub const fn classify_error(kind: &ErrorKind) -> FailureClass {
    match kind {
        ErrorKind::NetworkUnavailable
        | ErrorKind::NetworkTimeout
        | ErrorKind::HttpStatus
        | ErrorKind::SourceChanged => FailureClass::Retryable,
        _ => FailureClass::Permanent,
    }
}

#[must_use]
pub fn retry_delay(attempt: u32, jitter_ms: u64) -> Duration {
    let exponent = attempt.saturating_sub(1).min(5);
    let base_ms = 1_000_u64.saturating_mul(1_u64 << exponent);
    Duration::from_millis(base_ms.saturating_add(jitter_ms.min(750)).min(30_000))
}

pub struct DownloadEngine {
    client: Client,
    root: PathBuf,
    policy: DownloadPolicy,
    #[cfg(test)]
    test_write_failure_after_bytes: Option<u64>,
}

impl DownloadEngine {
    pub fn new(root: impl Into<PathBuf>, policy: DownloadPolicy) -> Result<Self, CoreError> {
        let client = Client::builder()
            .connect_timeout(policy.connect_timeout)
            .timeout(policy.request_timeout)
            .redirect(reqwest::redirect::Policy::limited(8))
            .build()
            .map_err(map_reqwest_error)?;
        Ok(Self {
            client,
            root: root.into(),
            policy,
            #[cfg(test)]
            test_write_failure_after_bytes: None,
        })
    }

    pub fn preflight_space(
        &self,
        expected_bytes: Option<u64>,
        available_bytes: Option<u64>,
    ) -> Result<(), CoreError> {
        if let (Some(expected), Some(available)) = (expected_bytes, available_bytes)
            && expected > available
        {
            return Err(CoreError::new(
                ErrorKind::InsufficientStorage,
                "Not enough free storage for this download",
                false,
            ));
        }
        Ok(())
    }

    fn preflight_transfer_space(
        &self,
        request: &TransferRequest,
        existing_bytes: u64,
    ) -> Result<(), CoreError> {
        let Some(expected_bytes) = request.expected_bytes else {
            return Ok(());
        };
        let required_bytes = expected_bytes.saturating_sub(existing_bytes);
        let available_bytes = available_space_bytes(&self.root)?;
        self.preflight_space(Some(required_bytes), Some(available_bytes))
    }

    pub fn transfer(
        &self,
        request: &TransferRequest,
        cancel: &AtomicBool,
    ) -> Result<TransferResult, CoreError> {
        self.transfer_with_progress(request, cancel, |_| Ok(()))
    }

    /// Reports actual bytes written, including safely resumed partial bytes, as
    /// each body chunk lands. Consumers can coalesce updates before durable I/O.
    pub fn transfer_with_progress(
        &self,
        request: &TransferRequest,
        cancel: &AtomicBool,
        mut on_progress: impl FnMut(u64) -> Result<(), CoreError>,
    ) -> Result<TransferResult, CoreError> {
        validate_http_url(&request.url)?;
        validate_relative_library_path(&request.relative_path)?;
        if cancel.load(Ordering::Relaxed) {
            return Err(CoreError::new(
                ErrorKind::Canceled,
                "Download canceled",
                false,
            ));
        }
        if request
            .expected_bytes
            .is_some_and(|bytes| bytes > self.policy.max_asset_bytes)
        {
            return Err(CoreError::new(
                ErrorKind::InvalidInput,
                "Asset exceeds configured maximum size",
                false,
            ));
        }

        let final_path = self.root.join(&request.relative_path);
        let partial_path = partial_path(&final_path);
        ensure_safe_library_write_path(&self.root, &final_path, &partial_path)?;

        let existing = fs::metadata(&partial_path).map_or(0, |metadata| metadata.len());
        if existing > self.policy.max_asset_bytes {
            return Err(CoreError::new(
                ErrorKind::InvalidInput,
                "Existing partial asset exceeds configured maximum size",
                false,
            ));
        }
        if existing == 0 {
            self.preflight_transfer_space(request, 0)?;
        }
        let mut response = self.request(&request.url, existing)?;
        // A complete partial may survive a crash before final promotion.
        // Retry an unsatisfiable open-ended range as a full transfer.
        if existing > 0 && response.status() == reqwest::StatusCode::RANGE_NOT_SATISFIABLE {
            response = self.request(&request.url, 0)?;
        }
        ensure_success(&response)?;
        let mut observed = representation_from_headers(&request.url, response.headers());

        let mut append = false;
        let mut start = 0;
        if existing > 0 {
            let representation_matches = prepare_partial_reuse(&partial_path, &observed)?;
            if representation_matches
                && response.status() == reqwest::StatusCode::PARTIAL_CONTENT
                && complete_resume_range(&response, existing).is_some()
            {
                append = true;
                start = existing;
            } else if response.status() == reqwest::StatusCode::PARTIAL_CONTENT {
                // Re-request only a rejected 206, not a full 200 response.
                response = self.request(&request.url, 0)?;
                ensure_success(&response)?;
                observed = representation_from_headers(&request.url, response.headers());
            }
        } else {
            prepare_partial_reuse(&partial_path, &observed)?;
        }

        // A ranged response must cover precisely the remaining tail of the
        // representation, never merely advertise the correct starting offset.
        let expected_resume_total = if append {
            Some(complete_resume_range(&response, start).ok_or_else(|| {
                CoreError::new(
                    ErrorKind::IntegrityFailure,
                    "Server returned an inconsistent resume range",
                    true,
                )
            })?)
        } else {
            // An unsolicited 206 is not evidence of a complete asset.
            if response.status() == reqwest::StatusCode::PARTIAL_CONTENT {
                return Err(CoreError::new(
                    ErrorKind::IntegrityFailure,
                    "Server returned a partial response to a full download",
                    true,
                ));
            }
            None
        };

        if existing > 0 {
            self.preflight_transfer_space(request, start)?;
        }
        let declared_body = response
            .headers()
            .get(CONTENT_LENGTH)
            .and_then(|value| value.to_str().ok())
            .and_then(|value| value.parse::<u64>().ok());
        if declared_body.is_some_and(|value| {
            start
                .checked_add(value)
                .is_none_or(|total| total > self.policy.max_asset_bytes)
        }) {
            return Err(CoreError::new(
                ErrorKind::InvalidInput,
                "Remote response exceeds configured maximum size",
                false,
            ));
        }
        // Known provider asset size is a tighter bound than the global policy.
        // Reject an oversized advertised response before allocating a partial.
        if let Some(expected) = request.expected_bytes
            && (start > expected
                || declared_body.is_some_and(|value| {
                    start
                        .checked_add(value)
                        .is_none_or(|total| total > expected)
                }))
        {
            return Err(CoreError::new(
                ErrorKind::IntegrityFailure,
                "Remote response exceeds expected asset size",
                true,
            ));
        }

        if observed.has_remote_validator() {
            save_resume_representation(&partial_path, &observed)?;
        } else {
            clear_resume_representation(&partial_path)?;
        }

        let mut file = open_partial(&partial_path, append)?;
        if !append {
            file.set_len(0).map_err(io_error)?;
            file.seek(SeekFrom::Start(0)).map_err(io_error)?;
        }
        let mut total = start;
        let mut buffer = vec![0_u8; COPY_BUFFER_BYTES];
        loop {
            if cancel.load(Ordering::Relaxed) {
                drop(file);
                if !self.policy.retain_partial_on_cancel {
                    let _ = fs::remove_file(&partial_path);
                    clear_resume_representation(&partial_path)?;
                }
                return Err(CoreError::new(
                    ErrorKind::Canceled,
                    "Download canceled",
                    false,
                ));
            }
            let count = response.read(&mut buffer).map_err(remote_body_read_error)?;
            if count == 0 {
                break;
            }
            #[cfg(test)]
            if let Some(fail_after) = self.test_write_failure_after_bytes {
                if total < fail_after && total.saturating_add(count as u64) >= fail_after {
                    let writable = usize::try_from(fail_after.saturating_sub(total))
                        .unwrap_or(usize::MAX)
                        .min(count);
                    file.write_all(&buffer[..writable]).map_err(io_error)?;
                    file.sync_all().map_err(io_error)?;
                    return Err(io_error(std::io::Error::from_raw_os_error(28)));
                }
                if total >= fail_after {
                    return Err(io_error(std::io::Error::from_raw_os_error(28)));
                }
            }

            let next_total = total.checked_add(count as u64).ok_or_else(|| {
                CoreError::new(
                    ErrorKind::InvalidInput,
                    "Downloaded asset exceeded configured maximum size",
                    false,
                )
            })?;
            if next_total > self.policy.max_asset_bytes {
                return Err(CoreError::new(
                    ErrorKind::InvalidInput,
                    "Downloaded asset exceeded configured maximum size",
                    false,
                ));
            }
            // Fail before writing even one chunk beyond a known selected asset size.
            if request
                .expected_bytes
                .is_some_and(|expected| next_total > expected)
            {
                return Err(CoreError::new(
                    ErrorKind::IntegrityFailure,
                    "Downloaded asset exceeded expected size",
                    true,
                ));
            }
            total = next_total;
            file.write_all(&buffer[..count]).map_err(io_error)?;
            if let Err(error) = on_progress(total) {
                drop(file);
                if error.kind == ErrorKind::Canceled && !self.policy.retain_partial_on_cancel {
                    let _ = fs::remove_file(&partial_path);
                    clear_resume_representation(&partial_path)?;
                }
                return Err(error);
            }
        }
        file.sync_all().map_err(io_error)?;
        drop(file);

        if let Some(expected) = request.expected_bytes
            && total != expected
        {
            return Err(CoreError::new(
                ErrorKind::IntegrityFailure,
                format!("Downloaded {total} bytes but expected {expected}"),
                true,
            ));
        }
        if let Some(body_bytes) = declared_body
            && total.saturating_sub(start) != body_bytes
        {
            return Err(CoreError::new(
                ErrorKind::IntegrityFailure,
                "Response body was shorter than its declared content length",
                true,
            ));
        }
        if let Some(expected_total) = expected_resume_total
            && total != expected_total
        {
            return Err(CoreError::new(
                ErrorKind::IntegrityFailure,
                "Resumed body did not contain the advertised remaining range",
                true,
            ));
        }
        let digest = sha256_file(&partial_path)?;
        if request
            .expected_sha256
            .as_ref()
            .is_some_and(|expected| !expected.eq_ignore_ascii_case(&digest))
        {
            return Err(CoreError::new(
                ErrorKind::IntegrityFailure,
                "Downloaded asset checksum did not match",
                false,
            ));
        }
        ensure_path_not_symlink(&final_path)?;
        fs::rename(&partial_path, &final_path).map_err(io_error)?;
        clear_resume_representation(&partial_path)?;
        Ok(TransferResult {
            relative_path: request.relative_path.clone(),
            bytes: total,
            sha256: digest,
            resumed: append,
        })
    }

    pub fn cleanup_orphan_partials(&self) -> Result<usize, CoreError> {
        let canonical_root = canonical_library_root(&self.root)?;
        let mut removed = 0;
        visit_partials(&canonical_root, &canonical_root, &mut |path| {
            fs::remove_file(path).map_err(io_error)?;
            clear_resume_representation(path)?;
            removed += 1;
            Ok(())
        })?;
        Ok(removed)
    }

    fn request(&self, url: &str, existing: u64) -> Result<Response, CoreError> {
        let mut request = self.client.get(url);
        if existing > 0 {
            request = request.header(RANGE, format!("bytes={existing}-"));
        }
        request.send().map_err(map_reqwest_error)
    }
}

fn available_space_bytes(path: &Path) -> Result<u64, CoreError> {
    canonical_library_root(path)?;
    fs2::available_space(path).map_err(io_error)
}

fn ensure_success(response: &Response) -> Result<(), CoreError> {
    if response.status().is_success() {
        return Ok(());
    }
    Err(CoreError::new(
        ErrorKind::HttpStatus,
        format!("Download server returned HTTP {}", response.status()),
        response.status().is_server_error()
            || response.status() == reqwest::StatusCode::TOO_MANY_REQUESTS,
    ))
}

fn open_partial(path: &Path, append: bool) -> Result<File, CoreError> {
    OpenOptions::new()
        .create(true)
        .read(true)
        .write(true)
        .append(append)
        .truncate(!append)
        .open(path)
        .map_err(io_error)
}
