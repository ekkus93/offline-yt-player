//! YouTube source boundary. Extraction is intentionally separate from URL recognition so parser
//! changes cannot leak provider-specific details into generic domain APIs.

use crate::{
    CoreError, DownloadPlan, ErrorKind, MediaFormat, MediaInfo, MediaSource, QualityChoice,
    SourceFuture,
};
use url::Url;

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct YouTubeVideoRef {
    pub video_id: String,
    pub canonical_url: String,
}

pub fn parse_youtube_video_url(input: &str) -> Result<YouTubeVideoRef, CoreError> {
    let url = Url::parse(input)
        .map_err(|_| CoreError::new(ErrorKind::InvalidInput, "Invalid video URL", false))?;
    if url.scheme() != "https" && url.scheme() != "http" {
        return Err(unsupported());
    }

    let host = url
        .host_str()
        .unwrap_or_default()
        .trim_end_matches('.')
        .to_ascii_lowercase();
    let video_id = match host.as_str() {
        "youtu.be" | "www.youtu.be" => {
            let mut segments = url
                .path_segments()
                .into_iter()
                .flatten()
                .filter(|segment| !segment.is_empty());
            let id = segments.next().filter(|_| segments.next().is_none());
            id.map(str::to_owned)
        }
        "youtube.com" | "www.youtube.com" | "m.youtube.com" => {
            if url.path() != "/watch" {
                None
            } else {
                url.query_pairs()
                    .find(|(key, _)| key == "v")
                    .map(|(_, value)| value.into_owned())
            }
        }
        _ => None,
    }
    .filter(|id| valid_video_id(id))
    .ok_or_else(unsupported)?;

    Ok(YouTubeVideoRef {
        canonical_url: format!("https://www.youtube.com/watch?v={video_id}"),
        video_id,
    })
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
        "URL is not a supported YouTube video form",
        false,
    )
}

#[derive(Debug, Default)]
pub struct YouTubeSource;

impl MediaSource for YouTubeSource {
    fn id(&self) -> &'static str {
        "youtube"
    }

    fn can_handle(&self, url: &str) -> bool {
        parse_youtube_video_url(url).is_ok()
    }

    fn resolve<'a>(&'a self, _url: &'a str) -> SourceFuture<'a, MediaInfo> {
        Box::pin(async {
            Err(CoreError::new(
                ErrorKind::SourceChanged,
                "YouTube extraction is not enabled in this build",
                false,
            ))
        })
    }

    fn formats<'a>(&'a self, _media: &'a MediaInfo) -> SourceFuture<'a, Vec<MediaFormat>> {
        Box::pin(async { Ok(Vec::new()) })
    }

    fn choices<'a>(&'a self, _media: &'a MediaInfo) -> SourceFuture<'a, Vec<QualityChoice>> {
        Box::pin(async { Ok(Vec::new()) })
    }

    fn download_plan<'a>(
        &'a self,
        _media: &'a MediaInfo,
        _choice_id: &'a str,
    ) -> SourceFuture<'a, DownloadPlan> {
        Box::pin(async {
            Err(CoreError::new(
                ErrorKind::NoCompatibleFormat,
                "No extracted YouTube formats are available",
                false,
            ))
        })
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    const VIDEO_ID: &str = "AbC_12-xYz9";

    #[test]
    fn recognizes_canonical_watch_url() {
        let parsed = parse_youtube_video_url(&format!(
            "https://www.youtube.com/watch?v={VIDEO_ID}&feature=share"
        ))
        .unwrap();
        assert_eq!(parsed.video_id, VIDEO_ID);
        assert_eq!(
            parsed.canonical_url,
            format!("https://www.youtube.com/watch?v={VIDEO_ID}")
        );
    }

    #[test]
    fn recognizes_short_share_url() {
        let parsed = parse_youtube_video_url(&format!("https://youtu.be/{VIDEO_ID}?si=tracking"))
            .unwrap();
        assert_eq!(parsed.video_id, VIDEO_ID);
    }

    #[test]
    fn recognizes_mobile_watch_url() {
        assert!(parse_youtube_video_url(&format!(
            "https://m.youtube.com/watch?v={VIDEO_ID}"
        ))
        .is_ok());
    }

    #[test]
    fn rejects_non_video_and_lookalike_forms() {
        for input in [
            "https://www.youtube.com/playlist?list=PL123",
            "https://www.youtube.com/@channel",
            "https://www.youtube.com/watch?v=too-short",
            "https://youtube.example/watch?v=AbC_12-xYz9",
            "https://youtu.be/AbC_12-xYz9/extra",
        ] {
            assert_eq!(
                parse_youtube_video_url(input).unwrap_err().kind,
                ErrorKind::UnsupportedSource,
                "{input}"
            );
        }
    }
}
