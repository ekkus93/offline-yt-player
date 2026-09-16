use offline_yt_core::{
    DownloadEngine, DownloadPolicy, ErrorKind, FailureClass, TransferRequest, classify_error,
};
use std::io::{Read, Write};
use std::net::TcpListener;
use std::sync::atomic::AtomicBool;
use std::thread;
use std::time::Duration;
use tiny_http::{Response, Server, StatusCode};

fn request(url: String, expected_bytes: Option<u64>) -> TransferRequest {
    TransferRequest {
        url,
        relative_path: "items/fault/video.mp4".into(),
        expected_bytes,
        expected_sha256: None,
    }
}

#[test]
fn incorrect_expected_length_is_integrity_failure() {
    let server = Server::http("127.0.0.1:0").unwrap();
    let address = format!("http://{}", server.server_addr());
    let handle = thread::spawn(move || {
        let incoming = server.recv().unwrap();
        incoming
            .respond(Response::from_data(b"short".to_vec()))
            .unwrap();
    });
    let temp = tempfile::tempdir().unwrap();
    let engine = DownloadEngine::new(temp.path(), DownloadPolicy::default()).unwrap();
    let error = engine
        .transfer(
            &request(format!("{address}/media"), Some(99)),
            &AtomicBool::new(false),
        )
        .unwrap_err();
    handle.join().unwrap();
    assert_eq!(error.kind, ErrorKind::IntegrityFailure);
}

#[test]
fn request_timeout_is_bounded_and_retryable() {
    let server = Server::http("127.0.0.1:0").unwrap();
    let address = format!("http://{}", server.server_addr());
    let handle = thread::spawn(move || {
        let incoming = server.recv().unwrap();
        thread::sleep(Duration::from_millis(250));
        let _ = incoming.respond(Response::from_data(b"late".to_vec()));
    });
    let temp = tempfile::tempdir().unwrap();
    let policy = DownloadPolicy {
        connect_timeout: Duration::from_millis(100),
        request_timeout: Duration::from_millis(50),
        ..DownloadPolicy::default()
    };
    let engine = DownloadEngine::new(temp.path(), policy).unwrap();
    let error = engine
        .transfer(
            &request(format!("{address}/slow"), None),
            &AtomicBool::new(false),
        )
        .unwrap_err();
    handle.join().unwrap();
    assert_eq!(error.kind, ErrorKind::NetworkTimeout);
    assert_eq!(classify_error(&error.kind), FailureClass::Retryable);
}

#[test]
fn retryable_http_failure_is_classified_for_retry_orchestration() {
    let server = Server::http("127.0.0.1:0").unwrap();
    let address = format!("http://{}", server.server_addr());
    let handle = thread::spawn(move || {
        let incoming = server.recv().unwrap();
        incoming.respond(Response::empty(StatusCode(503))).unwrap();
    });
    let temp = tempfile::tempdir().unwrap();
    let engine = DownloadEngine::new(temp.path(), DownloadPolicy::default()).unwrap();
    let error = engine
        .transfer(
            &request(format!("{address}/busy"), None),
            &AtomicBool::new(false),
        )
        .unwrap_err();
    handle.join().unwrap();
    assert_eq!(error.kind, ErrorKind::HttpStatus);
    assert!(error.retryable);
    assert_eq!(classify_error(&error.kind), FailureClass::Retryable);
}

#[test]
fn disconnect_with_truncated_declared_body_never_completes() {
    let listener = TcpListener::bind("127.0.0.1:0").unwrap();
    let address = listener.local_addr().unwrap();
    let handle = thread::spawn(move || {
        let (mut stream, _) = listener.accept().unwrap();
        let mut request_bytes = [0_u8; 1024];
        let _ = stream.read(&mut request_bytes);
        stream
            .write_all(b"HTTP/1.1 200 OK\r\nContent-Length: 100\r\nConnection: close\r\n\r\nshort")
            .unwrap();
    });
    let temp = tempfile::tempdir().unwrap();
    let engine = DownloadEngine::new(temp.path(), DownloadPolicy::default()).unwrap();
    let error = engine
        .transfer(
            &request(format!("http://{address}/truncated"), None),
            &AtomicBool::new(false),
        )
        .unwrap_err();
    handle.join().unwrap();
    assert!(matches!(
        error.kind,
        ErrorKind::IntegrityFailure | ErrorKind::NetworkUnavailable
    ));
    assert!(!temp.path().join("items/fault/video.mp4").exists());
}
