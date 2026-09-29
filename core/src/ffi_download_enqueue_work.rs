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
    let registry = SourceRegistry::production();
    match registry.select(job_id) {
        Ok(source) => build_download_work_with_source(job_id, source.as_ref()).map(Some),
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
