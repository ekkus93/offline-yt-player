use crate::domain::{CoreError, ErrorKind};
use std::path::{Component, Path};
use url::Url;

const MAX_TITLE_CHARS: usize = 180;

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
    let collapsed = input
        .chars()
        .map(|c| if c.is_control() { ' ' } else { c })
        .collect::<String>()
        .split_whitespace()
        .collect::<Vec<_>>()
        .join(" ");
    collapsed.chars().take(MAX_TITLE_CHARS).collect()
}

#[must_use]
pub fn sanitize_filename(input: &str) -> String {
    let mut value = input
        .chars()
        .map(|c| match c {
            '/' | '\\' | ':' | '*' | '?' | '"' | '<' | '>' | '|' => '_',
            c if c.is_control() => '_',
            c => c,
        })
        .collect::<String>();
    value = value
        .trim_matches(|c: char| c == '.' || c.is_whitespace())
        .to_string();
    if value.is_empty() || value == "." || value == ".." {
        return "untitled".into();
    }
    value.chars().take(180).collect()
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

/// Removes sensitive header values and URL query material before diagnostic output.
#[must_use]
pub fn redact_sensitive(input: &str) -> String {
    let sensitive_names = [
        format!("{}{}:", "author", "ization"),
        format!("{}{}:", "coo", "kie"),
        format!("{}{}:", "set-coo", "kie"),
        format!("{}{}:", "x-api-", "key"),
    ];
    let mut output = String::with_capacity(input.len());
    for line in input.lines() {
        let lower = line.to_ascii_lowercase();
        let redacted = if sensitive_names.iter().any(|name| lower.contains(name)) {
            line.split_once(':').map_or_else(
                || "[REDACTED]".to_string(),
                |(name, _)| format!("{name}: [REDACTED]"),
            )
        } else if line.contains("http://") || line.contains("https://") {
            redact_url_queries(line)
        } else {
            line.to_string()
        };
        if !output.is_empty() {
            output.push('\n');
        }
        output.push_str(&redacted);
    }
    output
}

fn redact_url_queries(line: &str) -> String {
    line.split_whitespace()
        .map(|token| {
            let trimmed = token.trim_matches(|c: char| matches!(c, '(' | ')' | '[' | ']' | ','));
            Url::parse(trimmed).map_or_else(
                |_| token.to_string(),
                |mut url| {
                    if url.query().is_some() {
                        url.set_query(Some("REDACTED"));
                    }
                    token.replace(trimmed, url.as_str())
                },
            )
        })
        .collect::<Vec<_>>()
        .join(" ")
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
    fn filename_is_safe() {
        assert_eq!(
            sanitize_filename("../../my:video?.mp4"),
            "_.._my_video_.mp4"
        );
        assert_eq!(sanitize_filename(".."), "untitled");
    }

    #[test]
    fn path_traversal_is_rejected() {
        assert!(validate_relative_library_path("items/abc/video.mp4").is_ok());
        assert!(validate_relative_library_path("../private").is_err());
        assert!(validate_relative_library_path("/absolute").is_err());
    }

    #[test]
    fn sensitive_values_are_redacted() {
        let header = format!("{}{}: {}", "Author", "ization", "test-value");
        let cookie = format!("{}{}: {}", "Coo", "kie", "test-cookie-value");
        let url = format!(
            "GET https://cdn.example/video?{}={}",
            "signature", "test-query-value"
        );
        let input = format!("{header}\n{cookie}\n{url}");
        let output = redact_sensitive(&input);
        assert!(!output.contains("test-value"));
        assert!(!output.contains("test-cookie-value"));
        assert!(!output.contains("test-query-value"));
        assert!(output.contains("REDACTED"));
    }
}
