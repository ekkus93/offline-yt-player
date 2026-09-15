use crate::domain::{CoreError, ErrorKind};
use url::Url;

const YOUTUBE_HOSTS: &[&str] = &[
    "youtube.com",
    "www.youtube.com",
    "m.youtube.com",
    "music.youtube.com",
];

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct YouTubeVideoUrl {
    pub video_id: String,
    pub canonical_url: String,
}

pub fn recognize_youtube_video_url(input: &str) -> Result<YouTubeVideoUrl, CoreError> {
    let parsed = Url::parse(input).map_err(|_| unsupported())?;
    if parsed.scheme() != "https" && parsed.scheme() != "http" {
        return Err(unsupported());
    }
    let host = parsed.host_str().unwrap_or_default().to_ascii_lowercase();
    let video_id = if host == "youtu.be" {
        short_video_id(&parsed)?
    } else if YOUTUBE_HOSTS.contains(&host.as_str()) {
        watch_video_id(&parsed)?
    } else {
        return Err(unsupported());
    };
    if !valid_video_id(&video_id) {
        return Err(unsupported());
    }
    Ok(YouTubeVideoUrl {
        canonical_url: format!("https://www.youtube.com/watch?v={video_id}"),
        video_id,
    })
}

fn watch_video_id(url: &Url) -> Result<String, CoreError> {
    if url.path() != "/watch" {
        return Err(unsupported());
    }
    url.query_pairs()
        .find(|(key, _)| key == "v")
        .map(|(_, value)| value.into_owned())
        .ok_or_else(unsupported)
}

fn short_video_id(url: &Url) -> Result<String, CoreError> {
    let mut segments = url.path_segments().ok_or_else(unsupported)?;
    let id = segments
        .next()
        .filter(|value| !value.is_empty())
        .ok_or_else(unsupported)?;
    if segments.next().is_some() {
        return Err(unsupported());
    }
    Ok(id.to_owned())
}

fn valid_video_id(id: &str) -> bool {
    id.len() == 11
        && id
            .bytes()
            .all(|byte| byte.is_ascii_alphanumeric() || byte == b'-' || byte == b'_')
}

fn unsupported() -> CoreError {
    CoreError::new(
        ErrorKind::UnsupportedSource,
        "This URL is not a supported YouTube video URL",
        false,
    )
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn recognizes_canonical_watch_url() {
        let parsed = recognize_youtube_video_url(
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ&feature=share",
        )
        .unwrap();
        assert_eq!(parsed.video_id, "dQw4w9WgXcQ");
        assert_eq!(
            parsed.canonical_url,
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
        );
    }

    #[test]
    fn recognizes_short_share_url() {
        let parsed = recognize_youtube_video_url("https://youtu.be/dQw4w9WgXcQ?t=43").unwrap();
        assert_eq!(parsed.video_id, "dQw4w9WgXcQ");
    }

    #[test]
    fn recognizes_mobile_watch_url() {
        let parsed =
            recognize_youtube_video_url("https://m.youtube.com/watch?v=dQw4w9WgXcQ").unwrap();
        assert_eq!(parsed.video_id, "dQw4w9WgXcQ");
    }

    #[test]
    fn rejects_non_video_youtube_forms() {
        for url in [
            "https://www.youtube.com/playlist?list=PL123",
            "https://www.youtube.com/@example",
            "https://www.youtube.com/shorts/dQw4w9WgXcQ",
            "https://youtu.be/dQw4w9WgXcQ/extra",
        ] {
            assert_eq!(
                recognize_youtube_video_url(url).unwrap_err().kind,
                ErrorKind::UnsupportedSource
            );
        }
    }

    #[test]
    fn rejects_spoofed_hosts_and_invalid_ids() {
        for url in [
            "https://youtube.com.evil.example/watch?v=dQw4w9WgXcQ",
            "https://evil.example/?next=https://youtu.be/dQw4w9WgXcQ",
            "https://www.youtube.com/watch?v=too-short",
            "ftp://www.youtube.com/watch?v=dQw4w9WgXcQ",
        ] {
            assert_eq!(
                recognize_youtube_video_url(url).unwrap_err().kind,
                ErrorKind::UnsupportedSource
            );
        }
    }
}
