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
    let raw = headers.get(CONTENT_RANGE)?.to_str().ok()?;
    let (range, total) = raw.strip_prefix("bytes ")?.split_once('/')?;
    let (first, last) = range.split_once('-')?;
    let first: u64 = first.parse().ok()?;
    let last: u64 = last.parse().ok()?;
    let total: u64 = total.parse().ok()?;
    (first <= last && last < total).then_some(total)
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

    #[test]
    fn malformed_content_ranges_never_report_a_trustworthy_total() {
        let mut headers = HeaderMap::new();
        for malformed in [
            "bogus/100",
            "bytes */100",
            "bytes 90-40/100",
            "bytes 0-100/100",
            "bytes 0-99/*",
            "bytes -99/100",
            "bytes 0-z/100",
            "bytes 0-99/0",
        ] {
            headers.insert(CONTENT_RANGE, HeaderValue::from_str(malformed).unwrap());
            assert_eq!(
                content_range_total(&headers),
                None,
                "malformed content range {malformed}"
            );
        }
        headers.insert(CONTENT_RANGE, HeaderValue::from_static("bytes 3-99/100"));
        assert_eq!(content_range_total(&headers), Some(100));
    }

    #[test]
    fn seeded_remote_validator_matrix_never_appends_unproven_partial_bytes() {
        use crate::resume::{
            ResumeRepresentation, prepare_partial_reuse, save_resume_representation,
        };
        let root = tempfile::tempdir().unwrap();
        let mut seed: u64 = 0xC0FF_EE5A_26D1;
        let url = "https://fixture.invalid/resume";
        for case in 0..128 {
            // Fixed xorshift seed: every CI failure identifies a reproducible input.
            seed ^= seed << 13;
            seed ^= seed >> 7;
            seed ^= seed << 17;
            let etag = match seed % 4 {
                0 => None,
                1 => Some("\"v1\""),
                2 => Some("\"v2\""),
                _ => Some("W/\"v1\""),
            };
            let total = match (seed >> 4) % 3 {
                0 => None,
                1 => Some(100_u64),
                _ => Some(101_u64),
            };
            let mut headers = HeaderMap::new();
            if let Some(value) = etag {
                headers.insert(ETAG, HeaderValue::from_str(value).unwrap());
            }
            headers.insert(
                LAST_MODIFIED,
                HeaderValue::from_static("Wed, 16 Sep 2026 00:00:00 GMT"),
            );
            if let Some(bytes) = total {
                headers.insert(
                    CONTENT_RANGE,
                    HeaderValue::from_str(&format!("bytes 40-99/{bytes}")).unwrap(),
                );
            }
            let current = representation_from_headers(url, &headers);
            let partial = root.path().join(format!("case-{case}.partial"));
            std::fs::write(&partial, b"unverified partial bytes").unwrap();
            let stored = ResumeRepresentation {
                url: url.into(),
                etag: Some("\"v1\"".into()),
                last_modified: None,
                total_bytes: Some(100),
            };
            save_resume_representation(&partial, &stored).unwrap();
            let reused = prepare_partial_reuse(&partial, &current).unwrap();
            let expected = etag == Some("\"v1\"") && total != Some(101);
            assert_eq!(
                reused, expected,
                "seed=0x{seed:016x}, case={case}, etag={etag:?}, total={total:?}"
            );
            assert_eq!(partial.exists(), expected);
            if expected {
                assert_eq!(
                    std::fs::read(&partial).unwrap(),
                    b"unverified partial bytes"
                );
            }
        }
    }
}
