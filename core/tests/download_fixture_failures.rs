use offline_yt_core::{DownloadEngine, DownloadPolicy, ErrorKind, TransferRequest};
use std::io::{Read, Write};
use std::net::{TcpListener, TcpStream};
use std::sync::Arc;
use std::sync::atomic::{AtomicBool, Ordering};
use std::thread;
use std::time::Duration;

struct RawFixture {
    url: String,
    handle: Option<thread::JoinHandle<()>>,
}

impl RawFixture {
    fn once(handler: impl FnOnce(TcpStream) + Send + 'static) -> Self {
        let listener = TcpListener::bind("127.0.0.1:0").unwrap();
        let address = listener.local_addr().unwrap();
        let handle = thread::spawn(move || {
            let (mut stream, _) = listener.accept().unwrap();
            let mut request = [0_u8; 2048];
            let _ = stream.read(&mut request);
            handler(stream);
        });
        Self {
            url: format!("http://{address}/media"),
            handle: Some(handle),
        }
    }
}

impl Drop for RawFixture {
    fn drop(&mut self) {
        if let Some(handle) = self.handle.take() {
            handle.join().unwrap();
        }
    }
}

fn request(url: String) -> TransferRequest {
    TransferRequest {
        url,
        relative_path: "items/fixture/video.mp4".into(),
        expected_bytes: None,
        expected_sha256: None,
    }
}

fn short_policy() -> DownloadPolicy {
    DownloadPolicy {
        connect_timeout: Duration::from_millis(100),
        request_timeout: Duration::from_millis(150),
        ..DownloadPolicy::default()
    }
}

#[test]
fn timeout_is_typed_and_never_promotes_partial_content() {
    let server = RawFixture::once(|mut stream| {
        thread::sleep(Duration::from_millis(400));
        let _ = stream.write_all(b"HTTP/1.1 200 OK\r\nContent-Length: 2\r\n\r\nok");
    });
    let temp = tempfile::tempdir().unwrap();
    let engine = DownloadEngine::new(temp.path(), short_policy()).unwrap();
    let error = engine
        .transfer(&request(server.url.clone()), &AtomicBool::new(false))
        .unwrap_err();
    assert_eq!(error.kind, ErrorKind::NetworkTimeout);
    assert!(!temp.path().join("items/fixture/video.mp4").exists());
}

#[test]
fn disconnect_does_not_promote_an_incomplete_asset() {
    let server = RawFixture::once(|mut stream| {
        stream
            .write_all(b"HTTP/1.1 200 OK\r\nContent-Length: 20\r\n\r\nshort")
            .unwrap();
    });
    let temp = tempfile::tempdir().unwrap();
    let engine = DownloadEngine::new(temp.path(), short_policy()).unwrap();
    assert!(
        engine
            .transfer(&request(server.url.clone()), &AtomicBool::new(false))
            .is_err()
    );
    assert!(!temp.path().join("items/fixture/video.mp4").exists());
}

#[test]
fn incorrect_content_length_is_rejected_before_promotion() {
    let server = RawFixture::once(|mut stream| {
        stream
            .write_all(b"HTTP/1.1 200 OK\r\nContent-Length: 3\r\n\r\nabcdef")
            .unwrap();
    });
    let temp = tempfile::tempdir().unwrap();
    let engine = DownloadEngine::new(temp.path(), short_policy()).unwrap();
    assert!(
        engine
            .transfer(&request(server.url.clone()), &AtomicBool::new(false))
            .is_err()
    );
    assert!(!temp.path().join("items/fixture/video.mp4").exists());
}

#[test]
fn interruption_retains_partial_without_promoting_it() {
    let server = RawFixture::once(|mut stream| {
        stream
            .write_all(b"HTTP/1.1 200 OK\r\nContent-Length: 12\r\nETag: \"fixture-v1\"\r\n\r\nfirst-")
            .unwrap();
        stream.flush().unwrap();
        thread::sleep(Duration::from_millis(120));
        let _ = stream.write_all(b"second");
    });
    let temp = tempfile::tempdir().unwrap();
    let mut policy = short_policy();
    policy.request_timeout = Duration::from_secs(2);
    let engine = DownloadEngine::new(temp.path(), policy).unwrap();
    let cancel = Arc::new(AtomicBool::new(false));
    let cancel_thread = Arc::clone(&cancel);
    let trigger = thread::spawn(move || {
        thread::sleep(Duration::from_millis(60));
        cancel_thread.store(true, Ordering::Relaxed);
    });
    let error = engine
        .transfer(&request(server.url.clone()), cancel.as_ref())
        .unwrap_err();
    trigger.join().unwrap();
    assert_eq!(error.kind, ErrorKind::Canceled);
    assert!(!temp.path().join("items/fixture/video.mp4").exists());
    assert!(temp.path().join("items/fixture/.video.mp4.partial").exists());
}
