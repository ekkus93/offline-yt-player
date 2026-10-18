mod resume_limits;
mod transfer;

use super::*;
use crate::resume::{ResumeRepresentation, load_resume_representation, resume_metadata_path};
use std::net::{Shutdown, TcpListener, TcpStream};
use std::sync::Arc;
use std::thread;
use tiny_http::{Header, Response as TinyResponse, Server, StatusCode};

struct FixtureServer {
    address: String,
    stop: Arc<AtomicBool>,
    handle: Option<thread::JoinHandle<()>>,
}

impl FixtureServer {
    fn start(body: Vec<u8>) -> Self {
        let server = Server::http("127.0.0.1:0").unwrap();
        let address = format!("http://{}", server.server_addr());
        let stop = Arc::new(AtomicBool::new(false));
        let stop_thread = Arc::clone(&stop);
        let handle = thread::spawn(move || {
            while !stop_thread.load(Ordering::Relaxed) {
                let Ok(Some(request)) = server.recv_timeout(Duration::from_millis(50)) else {
                    continue;
                };
                let range = request
                    .headers()
                    .iter()
                    .find(|header| header.field.equiv("Range"))
                    .map(|header| header.value.as_str().to_string());
                if let Some(range) = range {
                    let start = range
                        .strip_prefix("bytes=")
                        .and_then(|value| value.strip_suffix('-'))
                        .and_then(|value| value.parse::<usize>().ok())
                        .unwrap_or(0)
                        .min(body.len());
                    let slice = body[start..].to_vec();
                    let mut response =
                        TinyResponse::from_data(slice).with_status_code(StatusCode(206));
                    response.add_header(
                        Header::from_bytes(
                            b"Content-Range".as_slice(),
                            format!(
                                "bytes {start}-{}/{}",
                                body.len().saturating_sub(1),
                                body.len()
                            ),
                        )
                        .unwrap(),
                    );
                    response.add_header(
                        Header::from_bytes(b"ETag".as_slice(), b"\"fixture-v1\"".as_slice())
                            .unwrap(),
                    );
                    request.respond(response).unwrap();
                } else {
                    let mut response = TinyResponse::from_data(body.clone());
                    response.add_header(
                        Header::from_bytes(b"ETag".as_slice(), b"\"fixture-v1\"".as_slice())
                            .unwrap(),
                    );
                    request.respond(response).unwrap();
                }
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

struct RawHttpFixtureServer {
    address: String,
    handle: Option<thread::JoinHandle<()>>,
}

impl RawHttpFixtureServer {
    fn start(handler: impl FnOnce(TcpStream) + Send + 'static) -> Self {
        let listener = TcpListener::bind("127.0.0.1:0").unwrap();
        let address = format!("http://{}", listener.local_addr().unwrap());
        let handle = thread::spawn(move || {
            let (stream, _) = listener.accept().unwrap();
            handler(stream);
        });
        Self {
            address,
            handle: Some(handle),
        }
    }
}

impl Drop for RawHttpFixtureServer {
    fn drop(&mut self) {
        if let Some(handle) = self.handle.take() {
            handle.join().unwrap();
        }
    }
}

fn drain_http_request(stream: &mut TcpStream) {
    stream
        .set_read_timeout(Some(Duration::from_secs(2)))
        .unwrap();
    let mut request = Vec::new();
    let mut buffer = [0_u8; 256];
    while !request.windows(4).any(|window| window == b"\r\n\r\n") {
        match stream.read(&mut buffer) {
            Ok(0) => break,
            Ok(count) => request.extend_from_slice(&buffer[..count]),
            Err(error)
                if matches!(
                    error.kind(),
                    IoErrorKind::WouldBlock | IoErrorKind::TimedOut | IoErrorKind::Interrupted
                ) =>
            {
                break;
            }
            Err(error) => panic!("failed to read fixture request: {error}"),
        }
    }
}

fn transfer_from(url: String) -> TransferRequest {
    TransferRequest {
        url,
        relative_path: "items/one/video.mp4".into(),
        expected_bytes: None,
        expected_sha256: None,
    }
}
