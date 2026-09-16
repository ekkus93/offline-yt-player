use offline_yt_core::{DownloadEngine, DownloadPolicy, ErrorKind, TransferRequest};
use std::io::{Read, Write};
use std::net::TcpListener;
use std::sync::Arc;
use std::sync::atomic::{AtomicBool, Ordering};
use std::thread;
use std::time::Duration;

fn raw_server(response: Vec<u8>) -> (String, thread::JoinHandle<()>) {
    let listener = TcpListener::bind("127.0.0.1:0").unwrap();
    let address = format!("http://{}", listener.local_addr().unwrap());
    let handle = thread::spawn(move || {
        let (mut stream, _) = listener.accept().unwrap();
        let mut request = [0_u8; 2048];
        let _ = stream.read(&mut request);
        stream.write_all(&response).unwrap();
        stream.flush().unwrap();
    });
    (address, handle)
}

fn transfer_request(address: &str, expected_bytes: Option<u64>) -> TransferRequest {
    TransferRequest {
        url: format!("{address}/media"),
        relative_path: "items/fixture/video.mp4".into(),
        expected_bytes,
        expected_sha256: None,
    }
}

#[test]
fn disconnect_with_incorrect_content_length_never_promotes_partial_content() {
    let response = b"HTTP/1.1 200 OK\r\nContent-Length: 64\r\nConnection: close\r\n\r\nshort".to_vec();
    let (address, server) = raw_server(response);
    let temp = tempfile::tempdir().unwrap();
    let engine = DownloadEngine::new(temp.path(), DownloadPolicy::default()).unwrap();

    let error = engine
        .transfer(&transfer_request(&address, Some(64)), &AtomicBool::new(false))
        .unwrap_err();

    server.join().unwrap();
    assert!(matches!(
        error.kind,
        ErrorKind::IntegrityFailure | ErrorKind::NetworkUnavailable | ErrorKind::Internal
    ));
    assert!(!temp.path().join("items/fixture/video.mp4").exists());
    assert!(temp
        .path()
        .join("items/fixture/.video.mp4.partial")
        .exists());
}

#[test]
fn cooperative_cancel_interrupts_a_slow_body_without_promoting_it() {
    let listener = TcpListener::bind("127.0.0.1:0").unwrap();
    let address = format!("http://{}", listener.local_addr().unwrap());
    let server = thread::spawn(move || {
        let (mut stream, _) = listener.accept().unwrap();
        let mut request = [0_u8; 2048];
        let _ = stream.read(&mut request);
        stream
            .write_all(b"HTTP/1.1 200 OK\r\nContent-Length: 1048576\r\nConnection: close\r\n\r\n")
            .unwrap();
        let chunk = vec![b'x'; 16 * 1024];
        for _ in 0..64 {
            if stream.write_all(&chunk).is_err() {
                break;
            }
            let _ = stream.flush();
            thread::sleep(Duration::from_millis(10));
        }
    });

    let temp = tempfile::tempdir().unwrap();
    let root = temp.path().to_path_buf();
    let engine = DownloadEngine::new(&root, DownloadPolicy::default()).unwrap();
    let cancel = Arc::new(AtomicBool::new(false));
    let worker_cancel = Arc::clone(&cancel);
    let transfer = transfer_request(&address, Some(1_048_576));
    let worker = thread::spawn(move || engine.transfer(&transfer, &worker_cancel));

    thread::sleep(Duration::from_millis(45));
    cancel.store(true, Ordering::Relaxed);
    let error = worker.join().unwrap().unwrap_err();
    server.join().unwrap();

    assert_eq!(error.kind, ErrorKind::Canceled);
    assert!(!root.join("items/fixture/video.mp4").exists());
    assert!(root.join("items/fixture/.video.mp4.partial").exists());
}
