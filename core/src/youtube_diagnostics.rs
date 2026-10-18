use crate::domain::{CoreError, ErrorKind};

/// Sanitized provider diagnostic intended for logs/support bundles.
///
/// Deliberately emits a bounded code/message rather than reflecting the provider error text. The
/// fixed-message comparisons below recognize the adapter's own playability sentinels; no
/// provider-controlled text is copied into the diagnostic.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct YouTubeDiagnostic {
    pub code: &'static str,
    pub user_message: &'static str,
    pub retryable: bool,
}

#[must_use]
pub fn youtube_diagnostic(error: &CoreError) -> YouTubeDiagnostic {
    match error.kind {
        ErrorKind::NetworkUnavailable => YouTubeDiagnostic {
            code: "youtube.network_unavailable",
            user_message: "YouTube could not be reached. Check the network and try again.",
            retryable: true,
        },
        ErrorKind::NetworkTimeout => YouTubeDiagnostic {
            code: "youtube.network_timeout",
            user_message: "YouTube did not respond in time. Try again.",
            retryable: true,
        },
        ErrorKind::HttpStatus => YouTubeDiagnostic {
            code: "youtube.http_failure",
            user_message: "YouTube rejected or could not complete the request.",
            retryable: error.retryable,
        },
        ErrorKind::SourceChanged
            if error.message == "YouTube requires sign-in for this request" =>
        {
            YouTubeDiagnostic {
                code: "youtube.login_required",
                user_message: "YouTube requires sign-in for this request.",
                retryable: false,
            }
        }
        ErrorKind::SourceChanged
            if error.message == "YouTube did not make this media playable to this request" =>
        {
            YouTubeDiagnostic {
                code: "youtube.playback_rejected",
                user_message: "YouTube did not make this video playable to the app.",
                retryable: false,
            }
        }
        ErrorKind::SourceChanged => YouTubeDiagnostic {
            code: "youtube.source_changed",
            user_message: "YouTube changed how this video is delivered. An app update may be required.",
            retryable: false,
        },
        ErrorKind::NoCompatibleFormat => YouTubeDiagnostic {
            code: "youtube.no_compatible_format",
            user_message: "No supported downloadable format was found for this video.",
            retryable: false,
        },
        ErrorKind::UnsupportedSource | ErrorKind::InvalidInput => YouTubeDiagnostic {
            code: "youtube.unsupported_url",
            user_message: "This is not a supported YouTube video URL.",
            retryable: false,
        },
        ErrorKind::Canceled => YouTubeDiagnostic {
            code: "youtube.canceled",
            user_message: "The YouTube operation was canceled.",
            retryable: false,
        },
        _ => YouTubeDiagnostic {
            code: "youtube.internal",
            user_message: "The YouTube operation could not be completed.",
            retryable: error.retryable,
        },
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn unsupported_changed_network_and_playability_failures_are_distinct() {
        let unsupported = youtube_diagnostic(&CoreError::new(
            ErrorKind::UnsupportedSource,
            "unsupported",
            false,
        ));
        let network =
            youtube_diagnostic(&CoreError::new(ErrorKind::NetworkTimeout, "timeout", true));
        let changed = youtube_diagnostic(&CoreError::new(
            ErrorKind::SourceChanged,
            "player signature changed",
            false,
        ));
        let login_required = youtube_diagnostic(&CoreError::new(
            ErrorKind::SourceChanged,
            "YouTube requires sign-in for this request",
            false,
        ));
        let playback_rejected = youtube_diagnostic(&CoreError::new(
            ErrorKind::SourceChanged,
            "YouTube did not make this media playable to this request",
            false,
        ));
        assert_eq!(unsupported.code, "youtube.unsupported_url");
        assert_eq!(network.code, "youtube.network_timeout");
        assert_eq!(changed.code, "youtube.source_changed");
        assert_eq!(login_required.code, "youtube.login_required");
        assert_eq!(playback_rejected.code, "youtube.playback_rejected");
        assert!(network.retryable);
        assert!(!changed.retryable);
        assert!(!login_required.retryable);
        assert!(!playback_rejected.retryable);
    }

    #[test]
    fn diagnostic_never_reflects_sensitive_original_message() {
        let secret = "https://rr.example/videoplayback?sig=SECRET_TOKEN&expire=999";
        let error = CoreError::new(ErrorKind::SourceChanged, secret, false);
        let diagnostic = youtube_diagnostic(&error);
        assert!(!diagnostic.user_message.contains("SECRET_TOKEN"));
        assert!(!diagnostic.user_message.contains("videoplayback"));
        assert!(!diagnostic.code.contains("SECRET_TOKEN"));
    }

    #[test]
    fn http_retryability_is_preserved_without_reflecting_message() {
        let error = CoreError::new(
            ErrorKind::HttpStatus,
            "authorization=Bearer SECRET cookie=SECRET",
            true,
        );
        let diagnostic = youtube_diagnostic(&error);
        assert_eq!(diagnostic.code, "youtube.http_failure");
        assert!(diagnostic.retryable);
        assert!(!diagnostic.user_message.contains("SECRET"));
    }
}
