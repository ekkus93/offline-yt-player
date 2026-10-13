use crate::domain::{CoreError, ErrorKind};
use std::path::{Component, Path};
use url::Url;

const MAX_TITLE_CHARS: usize = 180;
// Avoid unbounded string copies and tokenization of untrusted gateway/provider errors.
const MAX_RAW_DIAGNOSTIC_BYTES: usize = 65_536;

pub fn validate_http_url(input: &str) -> Result<Url, CoreError> {
    let trimmed = input.trim();
    if trimmed.len() > 4_096 {
        return Err(CoreError::new(
            ErrorKind::InvalidInput,
            "URL is too long",
            false,
        ));
    }
    let url = Url::parse(trimmed)
        .map_err(|_| CoreError::new(ErrorKind::InvalidInput, "URL is not valid", false))?;
    if !matches!(url.scheme(), "http" | "https") || url.host_str().is_none() {
        return Err(CoreError::new(
            ErrorKind::InvalidInput,
            "Only http(s) URLs with a host are supported",
            false,
        ));
    }
    if !url.username().is_empty() || url.password().is_some() {
        return Err(CoreError::new(
            ErrorKind::InvalidInput,
            "URLs containing credentials are not supported",
            false,
        ));
    }
    Ok(url)
}

#[must_use]
pub fn sanitize_title(input: &str) -> String {
    // Provider titles are untrusted. Collapse whitespace as we consume input
    // instead of materializing the entire title and a vector of all its words.
    let mut collapsed = String::new();
    let mut chars_written = 0;
    let mut pending_space = false;
    for character in input.chars() {
        let character = if character.is_control() {
            ' '
        } else {
            character
        };
        if character.is_whitespace() {
            pending_space = chars_written > 0;
            continue;
        }
        if pending_space {
            collapsed.push(' ');
            chars_written += 1;
            pending_space = false;
            if chars_written == MAX_TITLE_CHARS {
                break;
            }
        }
        collapsed.push(character);
        chars_written += 1;
        if chars_written == MAX_TITLE_CHARS {
            break;
        }
    }
    collapsed
}

#[must_use]
pub fn sanitize_filename(input: &str) -> String {
    // Trim before mapping so an untrusted filename cannot force a full-size
    // allocation. Control whitespace maps to '_' and must not be trimmed.
    let trimmed = input.trim_matches(|c: char| c == '.' || (c.is_whitespace() && !c.is_control()));
    let bounded: String = trimmed
        .chars()
        .take(180)
        .map(|c| match c {
            '/' | '\\' | ':' | '*' | '?' | '"' | '<' | '>' | '|' => '_',
            c if c.is_control() => '_',
            c => c,
        })
        .collect();
    if bounded.is_empty() {
        "untitled".into()
    } else {
        bounded
    }
}

pub fn validate_relative_library_path(path: &str) -> Result<(), CoreError> {
    let candidate = Path::new(path);
    if candidate.is_absolute()
        || path.contains('\0')
        || candidate.components().any(|component| {
            matches!(
                component,
                Component::ParentDir | Component::RootDir | Component::Prefix(_)
            )
        })
    {
        return Err(CoreError::new(
            ErrorKind::InvalidInput,
            "unsafe library path",
            false,
        ));
    }
    if path.is_empty() {
        return Err(CoreError::new(
            ErrorKind::InvalidInput,
            "empty library path",
            false,
        ));
    }
    Ok(())
}

/// Removes sensitive header values and URL credential/query/fragment material before diagnostic
/// output. The result is intentionally lossy but retains the failing URL host/path.
#[must_use]
pub fn redact_sensitive(input: &str) -> String {
    // Reject the full oversized message rather than truncating through credentials.
    if input.len() > MAX_RAW_DIAGNOSTIC_BYTES {
        return "[REDACTED_OVERSIZE]".into();
    }
    let sensitive_names = [
        format!("{}{}:", "author", "ization"),
        format!("{}{}:", "proxy-author", "ization"),
        format!("{}{}:", "coo", "kie"),
        format!("{}{}:", "set-coo", "kie"),
        format!("{}{}:", "x-api-", "key"),
    ];
    let mut output = String::with_capacity(input.len());
    let mut redacting_folded_header = false;
    for line in input.lines() {
        // RFC-style folded header values and multiline diagnostics may put
        // credentials on indented lines after the header name. Do not echo
        // those lines merely because they contain no repeated field name.
        // Diagnostic renderers may indent header lines. Never treat indentation as a
        // reason to leave a credential-bearing header unredacted.
        // Loggers commonly prefix header text with a timestamp, level, or
        // "request failed:" context. Redact the header value even when the
        // sensitive field is not the first token on its diagnostic line.
        let lower = line.to_ascii_lowercase();
        let header = sensitive_names
            .iter()
            .filter_map(|name| lower.find(name.as_str()).map(|offset| (offset, name.len())))
            .min_by_key(|(offset, _)| *offset);
        // A new indented header is a separate field, not continuation data.
        // Recognize it first so both fields retain their redacted names.
        if redacting_folded_header
            && header.is_none()
            && (line.starts_with(' ') || line.starts_with('\t'))
        {
            if !output.is_empty() {
                output.push('\n');
            }
            output.push_str("[REDACTED_HEADER_CONTINUATION]");
            continue;
        }
        redacting_folded_header = false;
        let redacted = if let Some((offset, length)) = header {
            redacting_folded_header = true;
            // The prefix may itself contain a signed URL. Sanitize it before
            // dropping the entire header value and remaining line suffix.
            let prefix = redact_urls(&line[..offset]);
            let separator = if line[..offset].ends_with(' ') && !prefix.ends_with(' ') {
                " "
            } else {
                ""
            };
            format!(
                "{prefix}{separator}{} [REDACTED]",
                &line[offset..offset + length]
            )
        } else {
            redact_urls(line)
        };
        if !output.is_empty() {
            output.push('\n');
        }
        output.push_str(&redacted);
    }
    output
}

fn redact_urls(line: &str) -> String {
    line.split_whitespace()
        .map(redact_url_token)
        .collect::<Vec<_>>()
        .join(" ")
}

fn redact_url_token(token: &str) -> String {
    let lower = token.to_ascii_lowercase();
    // A scheme-relative URL is still able to carry credentials and a signed query.
    // Do not interpret the "//" inside an existing scheme or path as a second URL.
    let scheme_relative_start = lower.match_indices("//").find_map(|(offset, _)| {
        let preceded_by_scheme_or_path = offset > 0 && {
            let preceding = lower.as_bytes()[offset - 1];
            preceding == b':' || preceding == b'/' || preceding.is_ascii_alphanumeric()
        };
        (!preceded_by_scheme_or_path).then_some(offset)
    });
    let Some(start) = [
        lower.find("http://"),
        lower.find("https://"),
        lower.find("http%3a%2f%2f"),
        lower.find("https%3a%2f%2f"),
        scheme_relative_start,
    ]
    .into_iter()
    .flatten()
    .min() else {
        return token.to_string();
    };
    let (prefix, url_and_suffix) = token.split_at(start);
    let url_text = url_and_suffix.trim_end_matches([')', ']', '}', ',', ';', '\'', '"', '>']);
    let suffix = &url_and_suffix[url_text.len()..];

    // A second URL inside the same token can carry credentials in what the
    // first parser considers an ordinary path. Fail closed on the whole token.
    let after_scheme = url_text
        .find("://")
        .map_or("", |index| &url_text[index + 3..]);
    let nested = after_scheme.to_ascii_lowercase();
    if nested.contains("http://") || nested.contains("https://") {
        return format!("{prefix}[REDACTED_URL]{suffix}");
    }

    // Fully encoded URL strings must never pass through as ordinary text.
    let lower_url = url_text.to_ascii_lowercase();
    if lower_url.starts_with("http%3a%2f%2f") || lower_url.starts_with("https%3a%2f%2f") {
        return format!("{prefix}[REDACTED_URL]{suffix}");
    }
    let scheme_relative = url_text.starts_with("//");
    let parsed = if scheme_relative {
        Url::parse(&format!("https:{url_text}"))
    } else {
        Url::parse(url_text)
    };
    parsed.map_or_else(
        |_| format!("{prefix}[REDACTED_URL]{suffix}"),
        |mut url| {
            // Percent-encoded nested URL schemes and path credentials are not
            // recognized as URL userinfo. Never echo these untrusted path bytes.
            let path = url.path().to_ascii_lowercase();
            if path.contains("http%3a")
                || path.contains("https%3a")
                || path.contains("http://")
                || path.contains("https://")
                || path.contains('@')
                || path.contains("%40")
                // An encoded query/fragment delimiter can hide signed URL
                // credentials inside a path that would otherwise be echoed.
                || path.contains("%3f")
                || path.contains("%23")
            {
                return format!("{prefix}[REDACTED_URL]{suffix}");
            }
            if !url.username().is_empty() || url.password().is_some() {
                let _ = url.set_username("REDACTED");
                let _ = url.set_password(None);
            }
            if url.query().is_some() {
                url.set_query(Some("REDACTED"));
            }
            if url.fragment().is_some() {
                url.set_fragment(Some("REDACTED"));
            }
            let display = if scheme_relative {
                url.as_str()
                    .strip_prefix("https:")
                    .unwrap_or("[REDACTED_URL]")
            } else {
                url.as_str()
            };
            format!("{prefix}{display}{suffix}")
        },
    )
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn url_validation_rejects_credentials_and_non_http() {
        assert!(validate_http_url("https://example.com/video").is_ok());
        assert!(validate_http_url("file:///tmp/video").is_err());
        let credentialed = format!("https://{}:{}@example.com/", "user", "value");
        assert!(validate_http_url(&credentialed).is_err());
    }

    #[test]
    fn title_is_bounded_and_strips_controls() {
        let dirty = format!("Hello\nworld {}", "x".repeat(500));
        let title = sanitize_title(&dirty);
        assert!(!title.contains('\n'));
        assert!(title.chars().count() <= MAX_TITLE_CHARS);
    }

    #[test]
    fn title_sanitization_streams_untrusted_long_input_with_original_spacing() {
        let oversized = format!("  alpha\t\nbeta  {}  ", "z".repeat(1_000_000));
        let sanitized = sanitize_title(&oversized);
        assert_eq!(sanitized, format!("alpha beta {}", "z".repeat(169)));
        assert_eq!(sanitized.chars().count(), MAX_TITLE_CHARS);

        // A space at the character limit is retained, as in the original
        // whitespace-collapse-then-truncate behavior.
        let boundary = format!("{}\nlast", "x".repeat(MAX_TITLE_CHARS - 1));
        assert_eq!(
            sanitize_title(&boundary),
            format!("{} ", "x".repeat(MAX_TITLE_CHARS - 1))
        );
        assert_eq!(sanitize_title(" \t\n "), "");
        assert_eq!(sanitize_title("  a \t b\n c  "), "a b c");
    }

    #[test]
    fn filename_is_safe() {
        assert_eq!(
            sanitize_filename("../../my:video?.mp4"),
            "_.._my_video_.mp4"
        );
        assert_eq!(sanitize_filename(".."), "untitled");
    }

    #[test]
    fn filename_sanitization_bounds_untrusted_input_without_changing_trim_semantics() {
        assert_eq!(sanitize_filename(&"x".repeat(1_000_000)), "x".repeat(180));
        assert_eq!(
            sanitize_filename(&format!("{}video.mp4", ".".repeat(1_000_000))),
            "video.mp4"
        );
        assert_eq!(sanitize_filename("   ...  "), "untitled");
        assert_eq!(
            sanitize_filename(&format!("{} name {}", char::from(9), char::from(10))),
            "_ name _"
        );
        assert_eq!(sanitize_filename("  video.mp4  "), "video.mp4");
        assert_eq!(
            sanitize_filename(&format!("{}  ", "x".repeat(180))),
            "x".repeat(180)
        );
    }

    #[test]
    fn path_traversal_is_rejected() {
        assert!(validate_relative_library_path("items/abc/video.mp4").is_ok());
        assert!(validate_relative_library_path("../private").is_err());
        assert!(validate_relative_library_path("/absolute").is_err());
    }

    #[test]
    fn sensitive_values_are_redacted() {
        let marker = "RMD1302_MARKER";
        let header = format!("{}{}: {}", "Author", "ization", marker);
        let cookie = format!("{}{}: {}", "Coo", "kie", marker);
        let url = format!("GET https://user:{marker}@cdn.example/video?sig={marker}#frag-{marker}");
        let input = format!("{header}\n{cookie}\n{url}");
        let output = redact_sensitive(&input);
        assert!(!output.contains(marker));
        assert!(!output.contains("sig="));
        assert!(!output.contains("frag-"));
        assert!(output.contains("REDACTED"));
    }

    #[test]
    fn indented_sensitive_headers_are_redacted() {
        let marker = "RMD1802_INDENTED_HEADER_SECRET";
        let input = format!(
            "  Authorization: Bearer {marker}\n\tCookie: session={marker}\n  X-Api-Key: {marker}"
        );
        let output = redact_sensitive(&input);
        assert!(!output.contains(marker), "{output}");
        assert!(output.contains("Authorization: [REDACTED]"));
        assert!(output.contains("Cookie: [REDACTED]"));
        assert!(output.contains("X-Api-Key: [REDACTED]"));
    }

    #[test]
    fn prefixed_sensitive_headers_never_leak_values() {
        let marker = "RMD1802_PREFIXED_HEADER_SECRET";
        let cases = [
            format!("2026-10-11T20:00:00Z ERROR Authorization: Bearer {marker}"),
            format!("request failed: Cookie: session={marker}"),
            format!("network error: X-Api-Key: {marker}"),
            format!("GET https://cdn.example/media?sig={marker} Authorization: Bearer {marker}"),
        ];
        for input in cases {
            let output = redact_sensitive(&input);
            assert!(!output.contains(marker), "{output}");
            assert!(output.contains("[REDACTED]"), "{output}");
        }
        assert_eq!(
            redact_sensitive("request failed: no authorization header received"),
            "request failed: no authorization header received"
        );
    }

    #[test]

    fn folded_sensitive_header_values_never_leak_from_diagnostics() {
        let marker = "RMD1802_FOLDED_HEADER_SECRET";
        let input = format!(
            "request failed: Authorization: Bearer\n  {marker}\n\t{marker}\nnext: safe\nCookie: session=\n  {marker}"
        );
        let output = redact_sensitive(&input);
        assert!(!output.contains(marker), "{output}");
        assert_eq!(output.matches("[REDACTED_HEADER_CONTINUATION]").count(), 3);
        assert!(output.contains("next: safe"), "{output}");
        assert!(output.contains("Authorization: [REDACTED]"), "{output}");
        assert!(output.contains("Cookie: [REDACTED]"), "{output}");
    }

    #[test]
    fn wrapped_and_prefixed_signed_urls_are_redacted() {
        let marker = "RMD1802_WRAPPED_URL_SECRET";
        let inputs = [
            format!("url=\"https://cdn.example/video?sig={marker}#fragment-{marker}\","),
            format!(
                "request=(HTTPS://user:{marker}@cdn.example/video?token={marker}#fragment-{marker})"
            ),
            format!("target=<https://cdn.example/video?signature={marker}#fragment-{marker}>"),
        ];

        for input in inputs {
            let output = redact_sensitive(&input);
            assert!(!output.contains(marker), "{output}");
            assert!(output.contains("REDACTED"), "{output}");
        }
    }

    #[test]
    fn malformed_url_diagnostics_fail_closed() {
        let marker = "RMD1802_MALFORMED_URL_SECRET";
        let input = format!("fetch https://user:{marker}@[?sig={marker}");
        let output = redact_sensitive(&input);
        assert!(!output.contains(marker), "{output}");
        assert!(output.contains("[REDACTED_URL]"), "{output}");
    }

    #[test]
    fn nested_urls_in_a_single_token_fail_closed() {
        let marker = "RMD1802_NESTED_URL_SECRET";
        let inputs = [
            format!("GET https://cdn.example/video/https://user:{marker}@other.example/file"),
            format!("GET https://cdn.example/video,HTTPS://other.example/file?sig={marker}"),
        ];
        for input in inputs {
            let output = redact_sensitive(&input);
            assert!(!output.contains(marker), "{output}");
            assert!(output.contains("[REDACTED_URL]"), "{output}");
        }
    }

    #[test]
    fn encoded_nested_urls_and_path_credentials_fail_closed() {
        let marker = "RMD1802_ENCODED_NESTED_PATH_SECRET";
        let inputs = [
            format!(
                "GET https://cdn.example/archive/https%3A%2F%2Fuser%3A{marker}%40inner.example/video"
            ),
            format!("GET https://cdn.example/archive/user:{marker}@inner.example/video"),
        ];
        for input in inputs {
            let output = redact_sensitive(&input);
            assert!(!output.contains(marker), "{output}");
            assert!(output.contains("[REDACTED_URL]"), "{output}");
        }
        assert!(
            redact_sensitive("GET https://cdn.example/video/segment")
                .contains("cdn.example/video/segment")
        );
    }

    #[test]
    fn protocol_relative_and_encoded_signed_urls_never_leak_credentials() {
        let marker = "RMD1802_RUST_SCHEMELESS_SECRET";
        let inputs = [
            format!("GET //user:{marker}@cdn.example/video?sig={marker}"),
            format!("GET (//cdn.example/video?sig={marker})"),
            format!("url=https%3A%2F%2Fcdn.example%2Fvideo%3Fsig%3D{marker}"),
        ];
        for input in inputs {
            let output = redact_sensitive(&input);
            assert!(!output.contains(marker), "{output}");
            assert!(output.contains("REDACTED"), "{output}");
        }
        assert!(
            redact_sensitive("GET //cdn.example/video/segment")
                .contains("//cdn.example/video/segment")
        );
    }

    #[test]
    fn encoded_query_and_fragment_delimiters_in_url_paths_fail_closed() {
        let marker = "RMD1802_ENCODED_PATH_TOKEN_SECRET";
        let inputs = [
            format!("GET https://cdn.example/video%3Fsignature%3D{marker}"),
            format!("GET https://cdn.example/video%23credential-{marker}"),
        ];
        for input in inputs {
            let output = redact_sensitive(&input);
            assert!(!output.contains(marker), "{output}");
            assert!(output.contains("[REDACTED_URL]"), "{output}");
        }
    }

    #[test]
    fn oversized_untrusted_diagnostics_fail_closed_before_tokenization() {
        let marker = "RMD1802_RUST_RAW_OVERFLOW_SECRET";
        let oversized = format!(
            "GET https://user:{marker}@cdn.example/video?sig={marker}{}",
            "x".repeat(100_000)
        );
        assert_eq!(redact_sensitive(&oversized), "[REDACTED_OVERSIZE]");
        let ordinary = redact_sensitive(&format!("GET https://cdn.example/video?sig={marker}"));
        assert!(!ordinary.contains(marker));
        assert!(ordinary.contains("REDACTED"));
    }

    #[test]
    fn ordinary_diagnostic_text_is_preserved() {
        let input = "download failed: connection reset by peer";
        assert_eq!(redact_sensitive(input), input);
    }
}
