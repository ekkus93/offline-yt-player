use offline_yt_core::{DownloadEngine, DownloadPolicy, TransferRequest, execute_with_retry};
use std::fs;
use std::sync::atomic::{AtomicBool, AtomicUsize, Ordering};
use std::sync::{Arc, Mutex};
use std::thread;
use std::time::Duration;
use tiny_http::{Response, Server, StatusCode};

struct FixtureServer {
    address: String,
    stop: Arc<AtomicBool>,
    handle: Option<thread::JoinHandle<()>>,
}

impl FixtureServer {
    fn start(
        handler: impl Fn(usize, bool) -> Response<std::io::Cursor<Vec<u8>>> + Send + Sync + 'static,
    ) -> Self {
        let server = Server::http("127.0.0.1:0").unwrap();
        let address = format!("http://{}", server.server_addr());
        let stop = Arc::new(AtomicBool::new(false));
        let stop_thread = Arc::clone(&stop);
        let handler = Arc::new(handler);
        let calls = Arc::new(AtomicUsize::new(0));
        let handle = thread::spawn(move || {
            while !stop_thread.load(Ordering::Relaxed) {
                let Ok(Some(request)) = server.recv_timeout(Duration::from_millis(50)) else {
                    continue;
                };
                let call = calls.fetch_add(1, Ordering::Relaxed) + 1;
                let has_range = request
                    .headers()
                    .iter()
                    .any(|header| header.field.equiv("Range"));
                request.respond(handler(call, has_range)).unwrap();
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

fn request(server: &FixtureServer, expected_bytes: usize) -> TransferRequest {
    TransferRequest {
        url: format!("{}/media", server.address),
        relative_path: "items/fixture/video.mp4".into(),
        expected_bytes: Some(expected_bytes as u64),
        expected_sha256: None,
    }
}

#[test]
fn server_ignoring_range_restarts_from_zero() {
    let body = b"range fallback fixture".repeat(64);
    let served = body.clone();
    let server = FixtureServer::start(move |_, _| Response::from_data(served.clone()));
    let temp = tempfile::tempdir().unwrap();
    let final_path = temp.path().join("items/fixture/video.mp4");
    fs::create_dir_all(final_path.parent().unwrap()).unwrap();
    fs::write(final_path.with_file_name(".video.mp4.partial"), &body[..37]).unwrap();
    let engine = DownloadEngine::new(temp.path(), DownloadPolicy::default()).unwrap();

    let result = engine
        .transfer(&request(&server, body.len()), &AtomicBool::new(false))
        .unwrap();

    assert!(!result.resumed);
    assert_eq!(fs::read(final_path).unwrap(), body);
}

#[test]
fn retry_policy_recovers_from_fixture_http_500() {
    let body = b"retry fixture".repeat(32);
    let served = body.clone();
    let seen = Arc::new(Mutex::new(Vec::new()));
    let seen_server = Arc::clone(&seen);
    let server = FixtureServer::start(move |call, has_range| {
        seen_server.lock().unwrap().push((call, has_range));
        if call == 1 {
            Response::from_data(Vec::new()).with_status_code(StatusCode(500))
        } else {
            Response::from_data(served.clone())
        }
    });
    let temp = tempfile::tempdir().unwrap();
    let engine = DownloadEngine::new(temp.path(), DownloadPolicy::default()).unwrap();
    let transfer = request(&server, body.len());
    let cancel = AtomicBool::new(false);

    let result =
        execute_with_retry(2, 0, &cancel, |_| engine.transfer(&transfer, &cancel)).unwrap();

    assert_eq!(result.bytes, body.len() as u64);
    assert_eq!(seen.lock().unwrap().len(), 2);
}
