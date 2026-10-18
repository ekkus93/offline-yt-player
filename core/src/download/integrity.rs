use super::*;
use reqwest::header::CONTENT_RANGE;
use sha2::{Digest, Sha256};

pub(super) fn complete_resume_range(response: &Response, expected_start: u64) -> Option<u64> {
    let value = response.headers().get(CONTENT_RANGE)?.to_str().ok()?;
    let (range, complete) = value.strip_prefix("bytes ")?.split_once('/')?;
    let (first, last) = range.split_once('-')?;
    let first = first.parse::<u64>().ok()?;
    let last = last.parse::<u64>().ok()?;
    let complete = complete.parse::<u64>().ok()?;
    if first != expected_start || last < first || last.checked_add(1)? != complete {
        return None;
    }
    // A 206 with a smaller range body cannot prove the promised tail.
    if let Some(length) = response.headers().get(CONTENT_LENGTH) {
        let declared = length.to_str().ok()?.parse::<u64>().ok()?;
        if declared != complete.checked_sub(expected_start)? {
            return None;
        }
    }
    Some(complete)
}

pub(super) fn sha256_file(path: &Path) -> Result<String, CoreError> {
    let mut file = File::open(path).map_err(io_error)?;
    let mut hasher = Sha256::new();
    let mut buffer = vec![0_u8; COPY_BUFFER_BYTES];
    loop {
        let count = file.read(&mut buffer).map_err(io_error)?;
        if count == 0 {
            break;
        }
        hasher.update(&buffer[..count]);
    }
    Ok(format!("{:x}", hasher.finalize()))
}
