use super::*;

pub(super) fn map_reqwest_error(error: reqwest::Error) -> CoreError {
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

pub(super) fn remote_body_read_error(error: std::io::Error) -> CoreError {
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

pub(super) fn io_error_is_timeout(error: &std::io::Error) -> bool {
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

pub(super) fn looks_like_timeout(message: &str) -> bool {
    let lower = message.to_ascii_lowercase();
    lower.contains("timed out")
        || lower.contains("timeout")
        || lower.contains("deadline has elapsed")
}

pub(super) fn io_error(error: std::io::Error) -> CoreError {
    let kind = if error.raw_os_error() == Some(28) {
        ErrorKind::InsufficientStorage
    } else {
        ErrorKind::Internal
    };
    CoreError::new(kind, format!("storage error: {error}"), false)
}
