use crate::resume::ResumeRepresentation;
use reqwest::header::{CONTENT_LENGTH, CONTENT_RANGE, ETAG, HeaderMap, LAST_MODIFIED};

/// Build the remote representation identity needed to decide whether persisted partial bytes may
/// be reused. A ranged response's total length comes from Content-Range; a full response falls
/// back to Content-Length.
#[must_use]
pub fn representation_from_headers(url: &str, headers: &HeaderMap) -> ResumeRepresentation {
    ResumeRepresentation {
        url: url.to_owned(),
        etag: header_text(headers, ETAG),
        last_modified: header_text(headers, LAST_MODIFIED),
        total_bytes: content_range_total(headers).or_else(|| header_u64(headers, CONTENT_LENGTH)),
    }
}

fn header_text(headers: &HeaderMap, name: reqwest::header::HeaderName) -> Option<String> {
    headers
        .get(name)
        .and_then(|value| value.to_str().ok())
        .map(str::trim)
        .filter(|value| !value.is_empty())
        .map(str::to_owned)
}

fn header_u64(headers: &HeaderMap, name: reqwest::header::HeaderName) -> Option<u64> {
    headers
        .get(name)
        .and_then(|value| value.to_str().ok())
        .and_then(|value| value.parse::<u64>().ok())
}

fn content_range_total(headers: &HeaderMap) -> Option<u64> {
    headers
        .get(CONTENT_RANGE)
        .and_then(|value| value.to_str().ok())
        .and_then(|value| value.rsplit_once('/'))
        .and_then(|(_, total)| (total != "*").then_some(total))
        .and_then(|total| total.parse::<u64>().ok())
}

#[cfg(test)]
mod tests {
    use super::*;
    use reqwest::header::HeaderValue;

    #[test]
    fn ranged_response_uses_validator_and_complete_representation_size() {
        let mut headers = HeaderMap::new();
        headers.insert(ETAG, HeaderValue::from_static("\"fixture-v1\""));
        headers.insert(CONTENT_LENGTH, HeaderValue::from_static("60"));
        headers.insert(CONTENT_RANGE, HeaderValue::from_static("bytes 40-99/100"));

        assert_eq!(
            representation_from_headers("https://fixture.invalid/media", &headers),
            ResumeRepresentation {
                url: "https://fixture.invalid/media".into(),
                etag: Some("\"fixture-v1\"".into()),
                last_modified: None,
                total_bytes: Some(100),
            }
        );
    }

    #[test]
    fn full_response_uses_content_length_and_last_modified_fallback() {
        let mut headers = HeaderMap::new();
        headers.insert(
            LAST_MODIFIED,
            HeaderValue::from_static("Wed, 16 Sep 2026 00:00:00 GMT"),
        );
        headers.insert(CONTENT_LENGTH, HeaderValue::from_static("100"));

        let representation = representation_from_headers("https://fixture.invalid/media", &headers);
        assert_eq!(representation.total_bytes, Some(100));
        assert_eq!(
            representation.last_modified.as_deref(),
            Some("Wed, 16 Sep 2026 00:00:00 GMT")
        );
        assert!(representation.has_remote_validator());
    }

    #[test]
    fn missing_or_malformed_validators_fail_closed_for_reuse() {
        let mut headers = HeaderMap::new();
        headers.insert(CONTENT_RANGE, HeaderValue::from_static("bytes 40-99/*"));
        let representation = representation_from_headers("https://fixture.invalid/media", &headers);
        assert_eq!(representation.total_bytes, None);
        assert!(!representation.has_remote_validator());
    }
}
