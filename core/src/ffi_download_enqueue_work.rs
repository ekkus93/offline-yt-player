use crate::{CoreError, DurableDownloadWorkItem, ErrorKind, MediaSource, SourceRegistry};
use futures::executor::block_on;
use std::time::{SystemTime, UNIX_EPOCH};

/// Build provider-neutral executable work for supported production source URLs.
///
/// Non-URL queue identifiers and unsupported source URLs remain valid durable queue identifiers for
/// tests and legacy control paths, but they intentionally do not produce executable worker plans.
pub fn build_download_work_if_supported_source_url(
    job_id: &str,
) -> Result<Option<DurableDownloadWorkItem>, CoreError> {
    build_download_work_if_supported_source_url_with_choice(job_id, None)
}

pub fn build_download_work_if_supported_source_url_with_choice(
    job_id: &str,
    choice_id: Option<&str>,
) -> Result<Option<DurableDownloadWorkItem>, CoreError> {
    build_download_work_if_supported_source_url_with_options(job_id, choice_id, None, None)
}

pub fn build_download_work_if_supported_source_url_with_options(
    job_id: &str,
    choice_id: Option<&str>,
    subtitle_track_id: Option<&str>,
    audio_format_id: Option<&str>,
) -> Result<Option<DurableDownloadWorkItem>, CoreError> {
    let registry = SourceRegistry::production();
    match registry.select(job_id) {
        Ok(source) => build_download_work_with_source_and_options(
            job_id,
            source.as_ref(),
            choice_id,
            subtitle_track_id,
            audio_format_id,
        )
        .map(Some),
        Err(error) => {
            if matches!(
                error.kind,
                ErrorKind::InvalidInput | ErrorKind::UnsupportedSource
            ) {
                Ok(None)
            } else {
                Err(error)
            }
        }
    }
}

pub(crate) fn build_download_work_with_source_and_options(
    job_id: &str,
    source: &dyn MediaSource,
    choice_id: Option<&str>,
    subtitle_track_id: Option<&str>,
    audio_format_id: Option<&str>,
) -> Result<DurableDownloadWorkItem, CoreError> {
    let media = block_on(source.resolve(job_id))?;
    let choices = block_on(source.choices(&media))?;
    let choice = match choice_id {
        Some(requested) => choices
            .into_iter()
            .find(|choice| choice.choice_id == requested)
            .ok_or_else(|| {
                CoreError::new(
                    ErrorKind::NoCompatibleFormat,
                    "requested download quality is not available",
                    false,
                )
            })?,
        None => choices.into_iter().next().ok_or_else(|| {
            CoreError::new(
                ErrorKind::NoCompatibleFormat,
                "source did not provide an executable download choice",
                false,
            )
        })?,
    };
    let plan = block_on(source.download_plan_with_options(
        &media,
        &choice.choice_id,
        subtitle_track_id,
        audio_format_id,
    ))?;
    Ok(DurableDownloadWorkItem {
        job_id: job_id.to_owned(),
        plan,
        created_at_epoch_ms: current_epoch_ms()?,
    })
}

fn current_epoch_ms() -> Result<u64, CoreError> {
    let duration = SystemTime::now().duration_since(UNIX_EPOCH).map_err(|_| {
        CoreError::new(
            ErrorKind::Internal,
            "system clock is before the Unix epoch",
            true,
        )
    })?;
    u64::try_from(duration.as_millis()).map_err(|_| {
        CoreError::new(
            ErrorKind::Internal,
            "current time exceeds supported epoch range",
            false,
        )
    })
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::{DirectFixtureSource, ErrorKind, FixtureMedia};

    fn fixture_source() -> (String, DirectFixtureSource) {
        let source_url = "https://fixture.invalid/watch/one".to_owned();
        let source = DirectFixtureSource::with_entries([(
            source_url.clone(),
            FixtureMedia {
                media_id: "one".into(),
                title: "Fixture One".into(),
                duration_ms: 42_000,
                media_url: "https://fixture.invalid/media/one.mp4".into(),
                thumbnail_url: None,
                bytes: Some(1_024),
            },
        )]);
        (source_url, source)
    }

    #[test]
    fn explicit_choice_id_is_persisted_into_the_executable_plan() {
        let (source_url, source) = fixture_source();
        let work = build_download_work_with_source_and_options(
            &source_url,
            &source,
            Some("fixture-720p"),
            None,
            None,
        )
        .unwrap();

        assert_eq!(work.plan.quality.choice_id, "fixture-720p");
        assert_eq!(work.plan.quality.label, "720p");
    }

    #[test]
    fn unavailable_explicit_choice_fails_closed() {
        let (source_url, source) = fixture_source();
        let error = build_download_work_with_source_and_options(
            &source_url,
            &source,
            Some("missing-choice"),
            None,
            None,
        )
        .unwrap_err();

        assert_eq!(error.kind, ErrorKind::NoCompatibleFormat);
        assert_eq!(error.message, "requested download quality is not available");
    }
}
