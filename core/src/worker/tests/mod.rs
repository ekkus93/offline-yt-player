mod settlement;
mod transfers;

use super::*;
use crate::{Compatibility, DownloadPlanAsset, MediaKind, QualityChoice, SourceIdentity};
use std::sync::Arc;
use std::thread;
use tiny_http::{Response as TinyResponse, Server};

struct FixtureServer {
    address: String,
    stop: Arc<AtomicBool>,
    handle: Option<thread::JoinHandle<()>>,
}
impl FixtureServer {
    fn start(body: Vec<u8>) -> Self {
        Self::start_with_delay(body, std::time::Duration::ZERO)
    }

    fn start_with_delay(body: Vec<u8>, response_delay: std::time::Duration) -> Self {
        let server = Server::http("127.0.0.1:0").unwrap();
        let address = format!("http://{}", server.server_addr());
        let stop = Arc::new(AtomicBool::new(false));
        let stop_thread = Arc::clone(&stop);
        let handle = thread::spawn(move || {
            while !stop_thread.load(Ordering::Relaxed) {
                let Ok(Some(request)) = server.recv_timeout(std::time::Duration::from_millis(50))
                else {
                    continue;
                };
                thread::sleep(response_delay);
                request
                    .respond(TinyResponse::from_data(body.clone()))
                    .unwrap();
            }
        });
        Self {
            address,
            stop,
            handle: Some(handle),
        }
    }
}
impl Drop for FixtureServer {
    fn drop(&mut self) {
        self.stop.store(true, Ordering::Relaxed);
        if let Some(handle) = self.handle.take() {
            handle.join().unwrap();
        }
    }
}
fn snapshot(job_id: &str, state: DownloadState) -> DurableDownloadSnapshot {
    DurableDownloadSnapshot {
        job_id: job_id.into(),
        state,
        bytes_downloaded: 0,
        total_bytes: None,
        attempt: 0,
        retry_at_epoch_ms: None,
        last_error: None,
    }
}
fn plan(job_id: &str, url: String, bytes: usize) -> DownloadWorkItem {
    DownloadWorkItem {
        job_id: job_id.into(),
        created_at_epoch_ms: 1_000,
        plan: DownloadPlan {
            source: SourceIdentity {
                provider: "fixture".into(),
                media_id: job_id.into(),
                canonical_url: Some(format!("https://fixture.invalid/{job_id}")),
            },
            title: format!("Video {job_id}"),
            duration_ms: Some(60_000),
            quality: QualityChoice {
                choice_id: "fixture".into(),
                label: "720p".into(),
                estimated_bytes: Some(bytes as u64),
                video_height: Some(720),
                audio_only: false,
                compatibility: Compatibility::Preferred,
            },
            assets: vec![DownloadPlanAsset {
                asset_id: "combined".into(),
                kind: MediaKind::Video,
                url,
                relative_path: format!("items/{job_id}/video.mp4"),
                expected_bytes: Some(bytes as u64),
                expected_sha256: None,
                mime_type: Some("video/mp4".into()),
            }],
        },
    }
}

fn unknown_length_plan(job_id: &str, url: String) -> DownloadWorkItem {
    let mut item = plan(job_id, url, 0);
    item.plan.quality.estimated_bytes = None;
    item.plan.assets[0].expected_bytes = None;
    item
}
