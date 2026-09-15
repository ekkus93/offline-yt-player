use offline_yt_core::{
    DownloadEngine, DownloadPolicy, ErrorKind, TransferRequest, execute_with_retry,
};
use std::fs;
use std::io::{Read, Write};
use std::net::{TcpListener, TcpStream};
use std::sync::atomic::AtomicBool;
use std::thread;
use std::time::Duration;

fn read_headers(stream: &mut TcpStream) -> String {
    stream
        .set_read_timeout(Some(Duration::from_secs(2)))
        .unwrap();
    let mut bytes = Vec::new();
    let mut buffer = [0_u8; 512];
    while !bytes.windows(4).any(|window| window == b"\r\n\r\n") {
        let count = stream.read(&mut buffer).unwrap();
        if count == 0 {
            break;
        }
        bytes.extend_from_slice(&buffer[..count]);
        assert!(
            bytes.len() < 16 * 1024,
            "request headers exceeded fixture bound"
        );
    }
    String::from_utf8_lossy(&bytes).into_owned()
}

fn request_for(address: &str, expected_bytes: Option<u64>) -> TransferRequest {
    TransferRequest {
        url: format!("http://{address}/media"),
        relative_path: "items/test/video.mp4".into(),
        expected_bytes,
        expected_sha256: None,
    }
}

fn short_policy(request_timeout: Duration) -> DownloadPolicy {
    DownloadPolicy {
        connect_timeout: Duration::from_millis(100),
        request_timeout,
        ..DownloadPolicy::default()
    }
}

#[test]
fn timeout_is_bounded_and_classified() {
    let listener = TcpListener::bind("127.0.0.1:0").unwrap();
    let address = listener.local_addr().unwrap().to_string();
    let handle = thread::spawn(move || {
        let (mut stream, _) = listener.accept().unwrap();
        let _ = read_headers(&mut stream);
        thread::sleep(Duration::from_millis(250));
        let _ = stream.write_all(b"HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n");
    });

    let temp = tempfile::tempdir().unwrap();
    let engine = DownloadEngine::new(temp.path(), short_policy(Duration::from_millis(50))).unwrap();
    let error = engine
        .transfer(&request_for(&address, None), &AtomicBool::new(false))
        .unwrap_err();
    assert_eq!(error.kind, ErrorKind::NetworkTimeout);
    assert!(error.retryable);
    handle.join().unwrap();
}

#[test]
fn disconnect_before_response_is_retryable_network_failure() {
    let listener = TcpListener::bind("127.0.0.1:0").unwrap();
    let address = listener.local_addr().unwrap().to_string();
    let handle = thread::spawn(move || {
        let (mut stream, _) = listener.accept().unwrap();
        let _ = read_headers(&mut stream);
        drop(stream);
    });

    let temp = tempfile::tempdir().unwrap();
    let engine = DownloadEngine::new(temp.path(), short_policy(Duration::from_secs(1))).unwrap();
    let error = engine
        .transfer(&request_for(&address, None), &AtomicBool::new(false))
        .unwrap_err();
    assert!(matches!(
        error.kind,
        ErrorKind::NetworkUnavailable | ErrorKind::NetworkTimeout
    ));
    assert!(error.retryable);
    handle.join().unwrap();
}

#[test]
fn incorrect_content_length_never_promotes_completed_asset() {
    let listener = TcpListener::bind("127.0.0.1:0").unwrap();
    let address = listener.local_addr().unwrap().to_string();
    let handle = thread::spawn(move || {
        let (mut stream, _) = listener.accept().unwrap();
        let _ = read_headers(&mut stream);
        stream
            .write_all(b"HTTP/1.1 200 OK\r\nContent-Length: 50\r\nConnection: close\r\n\r\nshort")
            .unwrap();
    });

    let temp = tempfile::tempdir().unwrap();
    let engine = DownloadEngine::new(temp.path(), short_policy(Duration::from_secs(1))).unwrap();
    let request = request_for(&address, Some(50));
    assert!(engine.transfer(&request, &AtomicBool::new(false)).is_err());
    assert!(!temp.path().join(&request.relative_path).exists());
    assert!(temp.path().join("items/test/.video.mp4.partial").exists());
    handle.join().unwrap();
}

#[test]
fn interrupted_transfer_resumes_with_range_request() {
    let data = b"0123456789abcdefghij".to_vec();
    let first_prefix = 7_usize;
    let listener = TcpListener::bind("127.0.0.1:0").unwrap();
    let address = listener.local_addr().unwrap().to_string();
    let server_data = data.clone();
    let handle = thread::spawn(move || {
        let (mut first, _) = listener.accept().unwrap();
        let _ = read_headers(&mut first);
        write!(
            first,
            "HTTP/1.1 200 OK\r\nContent-Length: {}\r\nConnection: close\r\n\r\n",
            server_data.len()
        )
        .unwrap();
        first.write_all(&server_data[..first_prefix]).unwrap();
        drop(first);

        let (mut second, _) = listener.accept().unwrap();
        let headers = read_headers(&mut second);
        assert!(
            headers
                .to_ascii_lowercase()
                .contains(&format!("range: bytes={first_prefix}-")),
            "resume request did not contain expected Range header: {headers}"
        );
        let remainder = &server_data[first_prefix..];
        write!(
            second,
            "HTTP/1.1 206 Partial Content\r\nContent-Length: {}\r\nContent-Range: bytes {}-{}/{}\r\nConnection: close\r\n\r\n",
            remainder.len(),
            first_prefix,
            server_data.len() - 1,
            server_data.len()
        )
        .unwrap();
        second.write_all(remainder).unwrap();
    });

    let temp = tempfile::tempdir().unwrap();
    let engine = DownloadEngine::new(temp.path(), short_policy(Duration::from_secs(1))).unwrap();
    let request = request_for(&address, Some(data.len() as u64));
    assert!(engine.transfer(&request, &AtomicBool::new(false)).is_err());
    let partial = temp.path().join("items/test/.video.mp4.partial");
    assert_eq!(fs::metadata(&partial).unwrap().len(), first_prefix as u64);

    let result = engine.transfer(&request, &AtomicBool::new(false)).unwrap();
    assert!(result.resumed);
    assert_eq!(result.bytes, data.len() as u64);
    assert_eq!(
        fs::read(temp.path().join(&request.relative_path)).unwrap(),
        data
    );
    handle.join().unwrap();
}

#[test]
fn retry_executor_recovers_after_retryable_http_failure() {
    let data = b"retry-success".to_vec();
    let listener = TcpListener::bind("127.0.0.1:0").unwrap();
    let address = listener.local_addr().unwrap().to_string();
    let server_data = data.clone();
    let handle = thread::spawn(move || {
        let (mut first, _) = listener.accept().unwrap();
        let _ = read_headers(&mut first);
        first
            .write_all(b"HTTP/1.1 503 Service Unavailable\r\nContent-Length: 0\r\nConnection: close\r\n\r\n")
            .unwrap();

        let (mut second, _) = listener.accept().unwrap();
        let _ = read_headers(&mut second);
        write!(
            second,
            "HTTP/1.1 200 OK\r\nContent-Length: {}\r\nConnection: close\r\n\r\n",
            server_data.len()
        )
        .unwrap();
        second.write_all(&server_data).unwrap();
    });

    let temp = tempfile::tempdir().unwrap();
    let engine = DownloadEngine::new(temp.path(), short_policy(Duration::from_secs(1))).unwrap();
    let request = request_for(&address, Some(data.len() as u64));
    let cancel = AtomicBool::new(false);
    let result = execute_with_retry(2, 0, &cancel, |_| engine.transfer(&request, &cancel)).unwrap();
    assert_eq!(result.bytes, data.len() as u64);
    assert_eq!(
        fs::read(temp.path().join(&request.relative_path)).unwrap(),
        data
    );
    handle.join().unwrap();
}
