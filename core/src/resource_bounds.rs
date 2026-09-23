use crate::{
    CoreError, DownloadPolicy, ErrorKind, MAX_CONCURRENT_DOWNLOADS, bounded_download_concurrency,
};
use std::time::Duration;

/// Upper bound for a provider metadata response parsed in memory.
pub const MAX_PROVIDER_RESPONSE_BYTES: usize = 4 * 1024 * 1024;

/// Upper bound for provider-supplied stream, subtitle, and thumbnail URLs.
pub const MAX_PROVIDER_URL_BYTES: usize = 16 * 1024;

/// Upper bounds for provider metadata collections and strings.
pub const MAX_PROVIDER_STREAMS: usize = 256;
pub const MAX_PROVIDER_SUBTITLES: usize = 128;
pub const MAX_PROVIDER_TITLE_CHARS: usize = 512;
pub const MAX_PROVIDER_SUBTITLE_LANGUAGE_BYTES: usize = 64;
pub const MAX_PROVIDER_SUBTITLE_LABEL_CHARS: usize = 128;
pub const MAX_PROVIDER_SUBTITLE_ID_BYTES: usize = 256;

/// HTTP client bounds for provider metadata fetches.
pub const MAX_PROVIDER_REDIRECTS: usize = 3;
pub const PROVIDER_CONNECT_TIMEOUT_SECS: u64 = 10;
pub const PROVIDER_REQUEST_TIMEOUT_SECS: u64 = 20;

#[must_use]
pub fn provider_connect_timeout() -> Duration {
    Duration::from_secs(PROVIDER_CONNECT_TIMEOUT_SECS)
}

#[must_use]
pub fn provider_request_timeout() -> Duration {
    Duration::from_secs(PROVIDER_REQUEST_TIMEOUT_SECS)
}

pub fn ensure_provider_response_size(
    label: &str,
    declared_length: Option<u64>,
    observed_bytes: usize,
) -> Result<(), CoreError> {
    let observed = u64::try_from(observed_bytes).unwrap_or(u64::MAX);
    if declared_length.is_some_and(|bytes| bytes > MAX_PROVIDER_RESPONSE_BYTES as u64)
        || observed > MAX_PROVIDER_RESPONSE_BYTES as u64
    {
        return Err(CoreError::new(
            ErrorKind::SourceChanged,
            format!("{label} response exceeded the parser bound"),
            false,
        ));
    }
    Ok(())
}

pub fn ensure_provider_url_bound(label: &str, url: &str) -> Result<(), CoreError> {
    if url.len() > MAX_PROVIDER_URL_BYTES {
        return Err(CoreError::new(
            ErrorKind::SourceChanged,
            format!("{label} exceeded provider URL bound"),
            false,
        ));
    }
    Ok(())
}

#[must_use]
pub fn truncate_provider_title(title: &str) -> String {
    title.chars().take(MAX_PROVIDER_TITLE_CHARS).collect()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn provider_response_bound_rejects_declared_and_observed_oversize() {
        assert!(ensure_provider_response_size("provider", None, MAX_PROVIDER_RESPONSE_BYTES).is_ok());
        assert_eq!(
            ensure_provider_response_size(
                "provider",
                Some(MAX_PROVIDER_RESPONSE_BYTES as u64 + 1),
                0,
            )
            .unwrap_err()
            .kind,
            ErrorKind::SourceChanged
        );
        assert_eq!(
            ensure_provider_response_size("provider", None, MAX_PROVIDER_RESPONSE_BYTES + 1)
                .unwrap_err()
                .kind,
            ErrorKind::SourceChanged
        );
    }

    #[test]
    fn provider_url_bound_rejects_oversized_signed_urls() {
        let bounded = format!("https://media.example/{}", "a".repeat(MAX_PROVIDER_URL_BYTES - 22));
        assert!(ensure_provider_url_bound("media URL", &bounded).is_ok());
        let oversized = format!("https://media.example/{}", "a".repeat(MAX_PROVIDER_URL_BYTES));
        assert_eq!(
            ensure_provider_url_bound("media URL", &oversized)
                .unwrap_err()
                .kind,
            ErrorKind::SourceChanged
        );
    }

    #[test]
    fn provider_metadata_title_is_bounded() {
        let title = "🤖".repeat(MAX_PROVIDER_TITLE_CHARS + 8);
        let truncated = truncate_provider_title(&title);
        assert_eq!(truncated.chars().count(), MAX_PROVIDER_TITLE_CHARS);
        assert!(truncated.len() < title.len());
    }

    #[test]
    fn provider_http_policy_has_explicit_redirect_and_timeout_bounds() {
        assert_eq!(MAX_PROVIDER_REDIRECTS, 3);
        assert_eq!(provider_connect_timeout(), Duration::from_secs(10));
        assert_eq!(provider_request_timeout(), Duration::from_secs(20));
    }

    #[test]
    fn core_download_resource_policy_bounds_assets_concurrency_and_retries() {
        let policy = DownloadPolicy::default();
        assert_eq!(policy.max_attempts, 4);
        assert_eq!(policy.max_asset_bytes, 64 * 1024 * 1024 * 1024);
        assert_eq!(
            bounded_download_concurrency(usize::MAX),
            MAX_CONCURRENT_DOWNLOADS
        );
        assert_eq!(bounded_download_concurrency(0), 1);
    }
}
