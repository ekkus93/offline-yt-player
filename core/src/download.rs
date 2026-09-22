use crate::domain::{CoreError, ErrorKind};
use crate::resume::{
    clear_resume_representation, prepare_partial_reuse, save_resume_representation,
};
use crate::resume_http::representation_from_headers;
use crate::security::{validate_http_url, validate_relative_library_path};
use reqwest::blocking::{Client, Response};
use reqwest::header::{CONTENT_LENGTH, CONTENT_RANGE, RANGE};
use sha2::{Digest, Sha256};
use std::error::Error as StdError;
use std::fs::{self, File, OpenOptions};
use std::io::{ErrorKind as IoErrorKind, Read, Seek, SeekFrom, Write};
use std::path::{Path, PathBuf};
use std::sync::atomic::{AtomicBool, Ordering};
use std::time::Duration;

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

    pub fn transfer(
        &self,
        request: &TransferRequest,
        cancel: &AtomicBool,
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
        let mut response = self.request(&request.url, existing)?;
        ensure_success(&response)?;
        let mut observed = representation_from_headers(&request.url, response.headers());

        let mut append = false;
        let mut start = 0;
        if existing > 0 {
            let representation_matches = prepare_partial_reuse(&partial_path, &observed)?;
            if representation_matches
                && response.status() == reqwest::StatusCode::PARTIAL_CONTENT
                && content_range_starts_at(&response, existing)
            {
                append = true;
                start = existing;
            } else {
                response = self.request(&request.url, 0)?;
                ensure_success(&response)?;
                observed = representation_from_headers(&request.url, response.headers());
            }
        } else {
            prepare_partial_reuse(&partial_path, &observed)?;
        }

        if append && !content_range_starts_at(&response, start) {
            return Err(CoreError::new(
                ErrorKind::IntegrityFailure,
                "Server returned an unexpected resume range",
                true,
            ));
        }

        let declared_body = response
            .headers()
            .get(CONTENT_LENGTH)
            .and_then(|value| value.to_str().ok())
            .and_then(|value| value.parse::<u64>().ok());
        if declared_body.is_some_and(|value| value > self.policy.max_asset_bytes) {
            return Err(CoreError::new(
                ErrorKind::InvalidInput,
                "Remote response exceeds configured maximum size",
                false,
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
            total = total.saturating_add(count as u64);
            if total > self.policy.max_asset_bytes {
                return Err(CoreError::new(
                    ErrorKind::InvalidInput,
                    "Downloaded asset exceeded configured maximum size",
                    false,
                ));
            }
            file.write_all(&buffer[..count]).map_err(io_error)?;
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

fn partial_path(final_path: &Path) -> PathBuf {
    let name = final_path
        .file_name()
        .and_then(|name| name.to_str())
        .unwrap_or("asset");
    final_path.with_file_name(format!(".{name}.partial"))
}

fn ensure_safe_library_write_path(
    root: &Path,
    final_path: &Path,
    partial_path: &Path,
) -> Result<(), CoreError> {
    let canonical_root = canonical_library_root(root)?;
    let Some(parent) = final_path.parent() else {
        return Err(CoreError::new(
            ErrorKind::InvalidInput,
            "asset path has no parent",
            false,
        ));
    };
    reject_existing_symlink_components(root, parent)?;
    fs::create_dir_all(parent).map_err(io_error)?;
    let canonical_parent = fs::canonicalize(parent).map_err(io_error)?;
    if !canonical_parent.starts_with(&canonical_root) {
        return Err(CoreError::new(
            ErrorKind::InvalidInput,
            "asset path escapes the selected library root",
            false,
        ));
    }
    ensure_path_not_symlink(final_path)?;
    ensure_path_not_symlink(partial_path)?;
    Ok(())
}

fn canonical_library_root(root: &Path) -> Result<PathBuf, CoreError> {
    fs::create_dir_all(root).map_err(io_error)?;
    fs::canonicalize(root).map_err(io_error)
}

fn reject_existing_symlink_components(root: &Path, target: &Path) -> Result<(), CoreError> {
    let relative = target.strip_prefix(root).map_err(|_| {
        CoreError::new(
            ErrorKind::InvalidInput,
            "asset path escapes the selected library root",
            false,
        )
    })?;
    let mut current = root.to_path_buf();
    for component in relative.components() {
        current.push(component.as_os_str());
        match fs::symlink_metadata(&current) {
            Ok(metadata) if metadata.file_type().is_symlink() => {
                return Err(CoreError::new(
                    ErrorKind::InvalidInput,
                    "library asset path must not contain symlink components",
                    false,
                ));
            }
            Ok(_) => {}
            Err(error) if error.kind() == IoErrorKind::NotFound => break,
            Err(error) => return Err(io_error(error)),
        }
    }
    Ok(())
}

fn ensure_path_not_symlink(path: &Path) -> Result<(), CoreError> {
    match fs::symlink_metadata(path) {
        Ok(metadata) if metadata.file_type().is_symlink() => Err(CoreError::new(
            ErrorKind::InvalidInput,
            "library asset path must not be a symlink",
            false,
        )),
        Ok(_) => Ok(()),
        Err(error) if error.kind() == IoErrorKind::NotFound => Ok(()),
        Err(error) => Err(io_error(error)),
    }
}

fn content_range_starts_at(response: &Response, expected: u64) -> bool {
    response
        .headers()
        .get(CONTENT_RANGE)
        .and_then(|value| value.to_str().ok())
        .and_then(|value| value.strip_prefix("bytes "))
        .and_then(|value| value.split_once('-'))
        .and_then(|(start, _)| start.parse::<u64>().ok())
        == Some(expected)
}

fn sha256_file(path: &Path) -> Result<String, CoreError> {
    let mut file = File::open(path).map_err(io_error)?;
    let mut hasher = Sha256::new();
    let mut buffer = vec![0_u8; COPY_BUFFER_BYTES];
    loop {
        let count = file.read(&mut buffer).map_err(io_error)?;
        if count == 0 {
            break;
        }
        hasher.update(&buffer[..count]);
    }
    Ok(format!("{:x}", hasher.finalize()))
}

fn visit_partials(
    canonical_root: &Path,
    directory: &Path,
    visitor: &mut dyn FnMut(&Path) -> Result<(), CoreError>,
) -> Result<(), CoreError> {
    if !directory.exists() {
        return Ok(());
    }
    for entry in fs::read_dir(directory).map_err(io_error)? {
        let path = entry.map_err(io_error)?.path();
        let metadata = fs::symlink_metadata(&path).map_err(io_error)?;
        if metadata.file_type().is_symlink() {
            return Err(CoreError::new(
                ErrorKind::InvalidInput,
                "library cleanup refused to follow symlink",
                false,
            ));
        }
        if metadata.is_dir() {
            let canonical_directory = fs::canonicalize(&path).map_err(io_error)?;
            if !canonical_directory.starts_with(canonical_root) {
                return Err(CoreError::new(
                    ErrorKind::InvalidInput,
                    "library cleanup path escapes the selected root",
                    false,
                ));
            }
            visit_partials(canonical_root, &path, visitor)?;
        } else if path
            .file_name()
            .and_then(|name| name.to_str())
            .is_some_and(|name| name.ends_with(".partial"))
        {
            visitor(&path)?;
        }
    }
    Ok(())
}

fn map_reqwest_error(error: reqwest::Error) -> CoreError {
    if error.is_timeout() {
        CoreError::new(ErrorKind::NetworkTimeout, "Network request timed out", true)
    } else if error.is_connect() {
        CoreError::new(
            ErrorKind::NetworkUnavailable,
            "Unable to connect to download server",
            true,
        )
    } else {
        CoreError::new(
            ErrorKind::NetworkUnavailable,
            "Network request failed before a response was received",
            true,
        )
    }
}

fn remote_body_read_error(error: std::io::Error) -> CoreError {
    if io_error_is_timeout(&error) {
        return CoreError::new(
            ErrorKind::NetworkTimeout,
            "Network response body read timed out",
            true,
        );
    }

    match error.kind() {
        IoErrorKind::UnexpectedEof => CoreError::new(
            ErrorKind::IntegrityFailure,
            "Network response body ended before the declared content was received",
            true,
        ),
        IoErrorKind::ConnectionAborted
        | IoErrorKind::ConnectionReset
        | IoErrorKind::Interrupted
        | IoErrorKind::NotConnected
        | IoErrorKind::BrokenPipe => CoreError::new(
            ErrorKind::NetworkUnavailable,
            "Network response body read failed before completion",
            true,
        ),
        _ => CoreError::new(
            ErrorKind::NetworkUnavailable,
            "Network response body read failed",
            true,
        ),
    }
}

fn io_error_is_timeout(error: &std::io::Error) -> bool {
    let timeout_kind = matches!(
        error.kind(),
        IoErrorKind::TimedOut | IoErrorKind::WouldBlock
    );
    if timeout_kind || looks_like_timeout(&error.to_string()) {
        return true;
    }

    let mut source = StdError::source(error);
    while let Some(cause) = source {
        if cause
            .downcast_ref::<reqwest::Error>()
            .is_some_and(reqwest::Error::is_timeout)
            || looks_like_timeout(&cause.to_string())
        {
            return true;
        }
        source = cause.source();
    }
    false
}

fn looks_like_timeout(message: &str) -> bool {
    let lower = message.to_ascii_lowercase();
    lower.contains("timed out")
        || lower.contains("timeout")
        || lower.contains("deadline has elapsed")
}

fn io_error(error: std::io::Error) -> CoreError {
    let kind = if error.raw_os_error() == Some(28) {
        ErrorKind::InsufficientStorage
    } else {
        ErrorKind::Internal
    };
    CoreError::new(kind, format!("storage error: {error}"), false)
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::resume::{ResumeRepresentation, load_resume_representation, resume_metadata_path};
    use std::net::{Shutdown, TcpListener, TcpStream};
    use std::sync::Arc;
    use std::thread;
    use tiny_http::{Header, Response as TinyResponse, Server, StatusCode};

    struct FixtureServer {
        address: String,
        stop: Arc<AtomicBool>,
        handle: Option<thread::JoinHandle<()>>,
    }

    impl FixtureServer {
        fn start(body: Vec<u8>) -> Self {
            let server = Server::http("127.0.0.1:0").unwrap();
            let address = format!("http://{}", server.server_addr());
            let stop = Arc::new(AtomicBool::new(false));
            let stop_thread = Arc::clone(&stop);
            let handle = thread::spawn(move || {
                while !stop_thread.load(Ordering::Relaxed) {
                    let Ok(Some(request)) = server.recv_timeout(Duration::from_millis(50)) else {
                        continue;
                    };
                    let range = request
                        .headers()
                        .iter()
                        .find(|header| header.field.equiv("Range"))
                        .map(|header| header.value.as_str().to_string());
                    if let Some(range) = range {
                        let start = range
                            .strip_prefix("bytes=")
                            .and_then(|value| value.strip_suffix('-'))
                            .and_then(|value| value.parse::<usize>().ok())
                            .unwrap_or(0)
                            .min(body.len());
                        let slice = body[start..].to_vec();
                        let mut response =
                            TinyResponse::from_data(slice).with_status_code(StatusCode(206));
                        response.add_header(
                            Header::from_bytes(
                                b"Content-Range".as_slice(),
                                format!(
                                    "bytes {start}-{}/{}",
                                    body.len().saturating_sub(1),
                                    body.len()
                                ),
                            )
                            .unwrap(),
                        );
                        response.add_header(
                            Header::from_bytes(b"ETag".as_slice(), b"\"fixture-v1\"".as_slice())
                                .unwrap(),
                        );
                        request.respond(response).unwrap();
                    } else {
                        let mut response = TinyResponse::from_data(body.clone());
                        response.add_header(
                            Header::from_bytes(b"ETag".as_slice(), b"\"fixture-v1\"".as_slice())
                                .unwrap(),
                        );
                        request.respond(response).unwrap();
                    }
                }
            });
            Self {
                address,
                stop,
                handle: Some(handle),
            }
        }
    }

    impl Drop for FixtureServer {
        fn drop(&mut self) {
            self.stop.store(true, Ordering::Relaxed);
            if let Some(handle) = self.handle.take() {
                handle.join().unwrap();
            }
        }
    }

    struct RawHttpFixtureServer {
        address: String,
        handle: Option<thread::JoinHandle<()>>,
    }

    impl RawHttpFixtureServer {
        fn start(handler: impl FnOnce(TcpStream) + Send + 'static) -> Self {
            let listener = TcpListener::bind("127.0.0.1:0").unwrap();
            let address = format!("http://{}", listener.local_addr().unwrap());
            let handle = thread::spawn(move || {
                let (stream, _) = listener.accept().unwrap();
                handler(stream);
            });
            Self {
                address,
                handle: Some(handle),
            }
        }
    }

    impl Drop for RawHttpFixtureServer {
        fn drop(&mut self) {
            if let Some(handle) = self.handle.take() {
                handle.join().unwrap();
            }
        }
    }

    fn drain_http_request(stream: &mut TcpStream) {
        stream
            .set_read_timeout(Some(Duration::from_secs(2)))
            .unwrap();
        let mut request = Vec::new();
        let mut buffer = [0_u8; 256];
        while !request.windows(4).any(|window| window == b"\r\n\r\n") {
            match stream.read(&mut buffer) {
                Ok(0) => break,
                Ok(count) => request.extend_from_slice(&buffer[..count]),
                Err(error)
                    if matches!(
                        error.kind(),
                        IoErrorKind::WouldBlock | IoErrorKind::TimedOut | IoErrorKind::Interrupted
                    ) =>
                {
                    break;
                }
                Err(error) => panic!("failed to read fixture request: {error}"),
            }
        }
    }

    fn transfer_from(url: String) -> TransferRequest {
        TransferRequest {
            url,
            relative_path: "items/one/video.mp4".into(),
            expected_bytes: None,
            expected_sha256: None,
        }
    }

    #[test]
    fn retry_backoff_is_bounded() {
        assert_eq!(retry_delay(1, 0), Duration::from_secs(1));
        assert_eq!(retry_delay(2, 100), Duration::from_millis(2_100));
        assert!(retry_delay(99, 999) <= Duration::from_secs(30));
    }

    #[test]
    fn downloads_fixture_and_verifies_size() {
        let data = b"deterministic fixture media".repeat(100);
        let server = FixtureServer::start(data.clone());
        let temp = tempfile::tempdir().unwrap();
        let engine = DownloadEngine::new(temp.path(), DownloadPolicy::default()).unwrap();
        let request = TransferRequest {
            url: format!("{}/media", server.address),
            relative_path: "items/one/video.mp4".into(),
            expected_bytes: Some(data.len() as u64),
            expected_sha256: None,
        };
        let result = engine.transfer(&request, &AtomicBool::new(false)).unwrap();
        assert_eq!(result.bytes, data.len() as u64);
        assert!(!result.resumed);
        assert_eq!(
            fs::read(temp.path().join(&request.relative_path)).unwrap(),
            data
        );
        assert!(
            !resume_metadata_path(&partial_path(&temp.path().join(&request.relative_path)))
                .exists()
        );
    }

    #[test]
    fn resumes_existing_partial_only_with_matching_persisted_identity() {
        let data = b"range fixture".repeat(100);
        let server = FixtureServer::start(data.clone());
        let temp = tempfile::tempdir().unwrap();
        let final_path = temp.path().join("items/one/video.mp4");
        fs::create_dir_all(final_path.parent().unwrap()).unwrap();
        let partial = partial_path(&final_path);
        fs::write(&partial, &data[..100]).unwrap();
        let url = format!("{}/media", server.address);
        save_resume_representation(
            &partial,
            &ResumeRepresentation {
                url: url.clone(),
                etag: Some("\"fixture-v1\"".into()),
                last_modified: None,
                total_bytes: Some(data.len() as u64),
            },
        )
        .unwrap();
        let engine = DownloadEngine::new(temp.path(), DownloadPolicy::default()).unwrap();
        let request = TransferRequest {
            url,
            relative_path: "items/one/video.mp4".into(),
            expected_bytes: Some(data.len() as u64),
            expected_sha256: None,
        };
        let result = engine.transfer(&request, &AtomicBool::new(false)).unwrap();
        assert!(result.resumed);
        assert_eq!(fs::read(final_path).unwrap(), data);
        assert_eq!(load_resume_representation(&partial).unwrap(), None);
    }

    #[test]
    fn partial_without_identity_restarts_from_zero() {
        let data = b"safe restart fixture".repeat(100);
        let server = FixtureServer::start(data.clone());
        let temp = tempfile::tempdir().unwrap();
        let final_path = temp.path().join("items/one/video.mp4");
        fs::create_dir_all(final_path.parent().unwrap()).unwrap();
        fs::write(partial_path(&final_path), b"unproven stale bytes").unwrap();
        let engine = DownloadEngine::new(temp.path(), DownloadPolicy::default()).unwrap();
        let request = TransferRequest {
            url: format!("{}/media", server.address),
            relative_path: "items/one/video.mp4".into(),
            expected_bytes: Some(data.len() as u64),
            expected_sha256: None,
        };
        let result = engine.transfer(&request, &AtomicBool::new(false)).unwrap();
        assert!(!result.resumed);
        assert_eq!(fs::read(final_path).unwrap(), data);
    }

    #[test]
    fn malformed_response_does_not_reflect_signed_url_or_tokens() {
        let server = RawHttpFixtureServer::start(|mut stream| {
            drain_http_request(&mut stream);
            stream.write_all(b"not http at all\r\n\r\n").unwrap();
            let _ = stream.shutdown(Shutdown::Both);
        });
        let temp = tempfile::tempdir().unwrap();
        let engine = DownloadEngine::new(temp.path(), DownloadPolicy::default()).unwrap();
        let signed_url = format!(
            "{}/media?signature=SECRET_SIGNED_URL&token=BEARER_SECRET&X-Goog-Signature=COOKIE_SECRET",
            server.address
        );

        let error = engine
            .transfer(&transfer_from(signed_url), &AtomicBool::new(false))
            .unwrap_err();

        assert_eq!(error.kind, ErrorKind::NetworkUnavailable);
        assert!(error.retryable);
        assert_eq!(
            error.message,
            "Network request failed before a response was received"
        );
        for marker in [
            "SECRET_SIGNED_URL",
            "BEARER_SECRET",
            "COOKIE_SECRET",
            "signature=",
            "token=",
            "X-Goog-Signature",
        ] {
            assert!(
                !error.message.contains(marker),
                "diagnostic leaked secret marker {marker}: {}",
                error.message
            );
        }
    }

    #[test]
    fn mid_body_disconnect_is_retryable_remote_failure_not_storage() {
        let server = RawHttpFixtureServer::start(|mut stream| {
            drain_http_request(&mut stream);
            stream
                .write_all(
                    b"HTTP/1.1 200 OK\r\nContent-Length: 1024\r\nConnection: close\r\n\r\npartial body",
                )
                .unwrap();
            let _ = stream.shutdown(Shutdown::Both);
        });
        let temp = tempfile::tempdir().unwrap();
        let engine = DownloadEngine::new(temp.path(), DownloadPolicy::default()).unwrap();

        let error = engine
            .transfer(
                &transfer_from(format!("{}/media", server.address)),
                &AtomicBool::new(false),
            )
            .unwrap_err();

        assert_ne!(error.kind, ErrorKind::Internal);
        assert!(
            matches!(
                error.kind,
                ErrorKind::IntegrityFailure
                    | ErrorKind::NetworkUnavailable
                    | ErrorKind::NetworkTimeout
            ),
            "unexpected error: {error:?}"
        );
        assert!(error.retryable);
        assert!(!error.message.starts_with("storage error:"));
    }

    #[test]
    fn remote_body_timed_out_io_error_maps_to_network_timeout() {
        let error = remote_body_read_error(std::io::Error::new(
            IoErrorKind::TimedOut,
            "body read timed out",
        ));
        assert_eq!(error.kind, ErrorKind::NetworkTimeout);
        assert!(error.retryable);
        assert!(!error.message.starts_with("storage error:"));
    }

    #[test]
    fn delayed_response_body_is_retryable_remote_failure_not_storage() {
        let server = RawHttpFixtureServer::start(|mut stream| {
            drain_http_request(&mut stream);
            stream
                .write_all(b"HTTP/1.1 200 OK\r\nContent-Length: 1024\r\n\r\n")
                .unwrap();
            thread::sleep(Duration::from_millis(500));
        });
        let temp = tempfile::tempdir().unwrap();
        let policy = DownloadPolicy {
            request_timeout: Duration::from_millis(75),
            ..DownloadPolicy::default()
        };
        let engine = DownloadEngine::new(temp.path(), policy).unwrap();

        let error = engine
            .transfer(
                &transfer_from(format!("{}/media", server.address)),
                &AtomicBool::new(false),
            )
            .unwrap_err();

        assert_ne!(error.kind, ErrorKind::Internal);
        assert!(
            matches!(
                error.kind,
                ErrorKind::IntegrityFailure
                    | ErrorKind::NetworkUnavailable
                    | ErrorKind::NetworkTimeout
            ),
            "unexpected error: {error:?}"
        );
        assert!(error.retryable);
        assert!(!error.message.starts_with("storage error:"));
    }

    #[test]
    fn local_enospc_still_maps_to_insufficient_storage() {
        let error = io_error(std::io::Error::from_raw_os_error(28));
        assert_eq!(error.kind, ErrorKind::InsufficientStorage);
        assert!(!error.retryable);
        assert!(error.message.starts_with("storage error:"));
    }

    #[test]
    fn storage_preflight_rejects_insufficient_space() {
        let temp = tempfile::tempdir().unwrap();
        let engine = DownloadEngine::new(temp.path(), DownloadPolicy::default()).unwrap();
        let error = engine.preflight_space(Some(100), Some(99)).unwrap_err();
        assert_eq!(error.kind, ErrorKind::InsufficientStorage);
    }

    #[test]
    fn pre_canceled_transfer_returns_canceled_without_networking() {
        let temp = tempfile::tempdir().unwrap();
        let engine = DownloadEngine::new(temp.path(), DownloadPolicy::default()).unwrap();
        let request = TransferRequest {
            url: "http://127.0.0.1:1/unreachable".into(),
            relative_path: "items/one/video.mp4".into(),
            expected_bytes: None,
            expected_sha256: None,
        };
        let error = engine
            .transfer(&request, &AtomicBool::new(true))
            .unwrap_err();
        assert_eq!(error.kind, ErrorKind::Canceled);
        assert!(!temp.path().join("items/one/video.mp4").exists());
    }

    #[cfg(unix)]
    #[test]
    fn transfer_rejects_symlink_parent_escape_without_touching_outside_file() {
        use std::os::unix::fs::symlink;

        let root = tempfile::tempdir().unwrap();
        let outside = tempfile::tempdir().unwrap();
        fs::create_dir_all(root.path().join("items")).unwrap();
        symlink(outside.path(), root.path().join("items/one")).unwrap();
        let outside_file = outside.path().join(".video.mp4.partial");
        fs::write(&outside_file, b"outside bytes").unwrap();
        let engine = DownloadEngine::new(root.path(), DownloadPolicy::default()).unwrap();
        let request = TransferRequest {
            url: "http://127.0.0.1:1/unreachable".into(),
            relative_path: "items/one/video.mp4".into(),
            expected_bytes: None,
            expected_sha256: None,
        };

        let error = engine.transfer(&request, &AtomicBool::new(false)).unwrap_err();

        assert_eq!(error.kind, ErrorKind::InvalidInput);
        assert_eq!(fs::read(&outside_file).unwrap(), b"outside bytes");
        assert!(!outside.path().join("video.mp4").exists());
    }

    #[cfg(unix)]
    #[test]
    fn cleanup_orphan_partials_rejects_symlinked_directories_without_touching_outside() {
        use std::os::unix::fs::symlink;

        let root = tempfile::tempdir().unwrap();
        let outside = tempfile::tempdir().unwrap();
        fs::write(outside.path().join(".stolen.partial"), b"outside bytes").unwrap();
        symlink(outside.path(), root.path().join("items")).unwrap();
        let engine = DownloadEngine::new(root.path(), DownloadPolicy::default()).unwrap();

        let error = engine.cleanup_orphan_partials().unwrap_err();

        assert_eq!(error.kind, ErrorKind::InvalidInput);
        assert_eq!(
            fs::read(outside.path().join(".stolen.partial")).unwrap(),
            b"outside bytes"
        );
    }
}
