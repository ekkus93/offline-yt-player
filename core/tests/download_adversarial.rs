use offline_yt_core::{DownloadEngine, DownloadPolicy, ErrorKind, TransferRequest};
use std::io::{Read, Write};
use std::net::{TcpListener, TcpStream};
use std::sync::atomic::AtomicBool;
use std::thread;
use std::time::Duration;

fn serve_once(response: impl FnOnce(TcpStream) + Send + 'static) -> String {
    let listener = TcpListener::bind("127.0.0.1:0").unwrap();
    let address = listener.local_addr().unwrap();
    thread::spawn(move || {
        let (mut stream, _) = listener.accept().unwrap();
        let mut request = [0_u8; 2048];
        let _ = stream.read(&mut request);
        response(stream);
    });
    format!("http://{address}/media")
}

fn request(url: String) -> TransferRequest {
    TransferRequest {
        url,
        relative_path: "items/adversarial/video.mp4".into(),
        expected_bytes: None,
        expected_sha256: None,
    }
}

#[test]
fn request_timeout_is_bounded_and_retryable() {
    let url = serve_once(|mut stream| {
        thread::sleep(Duration::from_millis(250));
        let _ = stream.write_all(b"HTTP/1.1 200 OK\r\nContent-Length: 2\r\nConnection: close\r\n\r\nok");
    });
    let root = tempfile::tempdir().unwrap();
    let policy = DownloadPolicy {
        request_timeout: Duration::from_millis(50),
        ..DownloadPolicy::default()
    };
    let engine = DownloadEngine::new(root.path(), policy).unwrap();
    let error = engine.transfer(&request(url), &AtomicBool::new(false)).unwrap_err();
    assert!(matches!(error.kind, ErrorKind::NetworkTimeout | ErrorKind::NetworkUnavailable));
    assert!(error.retryable);
}

#[test]
fn truncated_declared_body_never_completes() {
    let url = serve_once(|mut stream| {
        stream.write_all(b"HTTP/1.1 200 OK\r\nContent-Length: 100\r\nConnection: close\r\n\r\nshort").unwrap();
    });
    let root = tempfile::tempdir().unwrap();
    let engine = DownloadEngine::new(root.path(), DownloadPolicy::default()).unwrap();
    let error = engine.transfer(&request(url), &AtomicBool::new(false)).unwrap_err();
    assert!(matches!(error.kind, ErrorKind::Internal | ErrorKind::NetworkUnavailable | ErrorKind::IntegrityFailure));
    assert!(!root.path().join("items/adversarial/video.mp4").exists());
}

#[test]
fn abrupt_disconnect_never_promotes_partial_content() {
    let url = serve_once(|mut stream| {
        stream.write_all(b"HTTP/1.1 200 OK\r\nContent-Length: 4096\r\nConnection: close\r\n\r\npartial").unwrap();
    });
    let root = tempfile::tempdir().unwrap();
    let engine = DownloadEngine::new(root.path(), DownloadPolicy::default()).unwrap();
    assert!(engine.transfer(&request(url), &AtomicBool::new(false)).is_err());
    assert!(!root.path().join("items/adversarial/video.mp4").exists());
}
