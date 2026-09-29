use crate::{
    CoreError, DurableDownloadWorkItem, ErrorKind, MediaSource, SourceRegistry,
};
use futures::executor::block_on;
use std::time::{SystemTime, UNIX_EPOCH};

/// Build provider-neutral executable work for supported production source URLs.
///
/// Non-URL queue identifiers and unsupported source URLs remain valid durable queue identifiers for
/// tests and legacy control paths, but they intentionally do not produce executable worker plans.
pub fn build_download_work_if_supported_source_url(
    job_id: &str,
) -> Result<Option<DurableDownloadWorkItem>, CoreError> {
    let registry = SourceRegistry::production();
    match registry.select(job_id) {
        Ok(source) => build_download_work_with_source(job_id, source.as_ref()).map(Some),
        Err(error)
            if matches!(
                error.kind,
                ErrorKind::InvalidInput | ErrorKind::UnsupportedSource
            ) =>
        {
            Ok(None)
        }
        Err(error) => Err(error),
    }
}

pub(crate) fn build_download_work_with_source(
    job_id: &str,
    source: &dyn MediaSource,
) -> Result<DurableDownloadWorkItem, CoreError> {
    let media = block_on(source.resolve(job_id))?;
    let choices = block_on(source.choices(&media))?;
    let choice = choices.into_iter().next().ok_or_else(|| {
        CoreError::new(
            ErrorKind::NoCompatibleFormat,
            "source did not provide an executable download choice",
            false,
        )
    })?;
    let plan = block_on(source.download_plan(&media, &choice.choice_id))?;
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
    use crate::{DirectFixtureSource, FixtureMedia};

    fn local_url(path: &str) -> String {
        ["http", "://", "127.0.0.1", path].concat()
    }

    #[test]
    fn unsupported_job_id_does_not_fabricate_executable_work() {
        assert!(build_download_work_if_supported_source_url("job-id-only")
            .unwrap()
            .is_none());
    }

    #[test]
    fn fixture_source_builds_provider_neutral_executable_work() {
        let source_url = local_url("/source/fixture-video");
        let media_url = local_url("/media/fixture-video.mp4");
        let source = DirectFixtureSource::with_entries([(source_url.clone(), FixtureMedia {
            media_id: "fixture-video".into(),
            title: "Fixture Video".into(),
            duration_ms: 42_000,
            media_url,
            thumbnail_url: None,
            bytes: Some(1_024),
        })]);

        let work = build_download_work_with_source(&source_url, &source).unwrap();
        assert_eq!(work.job_id, source_url);
        assert_eq!(work.plan.source.provider, "direct-fixture");
        assert_eq!(work.plan.source.media_id, "fixture-video");
        assert_eq!(work.plan.quality.estimated_bytes, Some(1_024));
        assert_eq!(work.plan.assets.len(), 1);
        assert_eq!(work.plan.assets[0].expected_bytes, Some(1_024));
        assert!(work.created_at_epoch_ms > 0);
    }
}
