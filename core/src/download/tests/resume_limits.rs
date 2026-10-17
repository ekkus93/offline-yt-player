use super::*;

#[test]
fn declared_body_over_expected_size_fails_before_creating_partial() {
    let server = RawHttpFixtureServer::start(|mut stream| {
        drain_http_request(&mut stream);
        let _ = stream.write_all(
            b"HTTP/1.1 200 OK\r\nContent-Length: 12\r\nConnection: close\r\n\r\n0123456789ab",
        );
    });
    let root = tempfile::tempdir().unwrap();
    let engine = DownloadEngine::new(root.path(), DownloadPolicy::default()).unwrap();
    let final_path = root.path().join("items/one/video.mp4");
    let mut request = transfer_from(format!("{}/media", server.address));
    request.expected_bytes = Some(4);
    let error = engine
        .transfer(&request, &AtomicBool::new(false))
        .unwrap_err();
    assert_eq!(error.kind, ErrorKind::IntegrityFailure);
    assert!(!partial_path(&final_path).exists());
    assert!(!final_path.exists());
}

#[test]
fn streamed_chunked_body_aborts_before_writing_over_expected_size() {
    let server = RawHttpFixtureServer::start(|mut stream| {
        drain_http_request(&mut stream);
        let _ = stream.write_all(
                b"HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\nConnection: close\r\n\r\na\r\n0123456789\r\n0\r\n\r\n",
            );
    });
    let root = tempfile::tempdir().unwrap();
    let engine = DownloadEngine::new(root.path(), DownloadPolicy::default()).unwrap();
    let final_path = root.path().join("items/one/video.mp4");
    let mut request = transfer_from(format!("{}/media", server.address));
    request.expected_bytes = Some(4);
    let error = engine
        .transfer(&request, &AtomicBool::new(false))
        .unwrap_err();
    assert_eq!(error.kind, ErrorKind::IntegrityFailure);
    let partial = partial_path(&final_path);
    assert!(partial.exists());
    assert_eq!(fs::metadata(partial).unwrap().len(), 0);
    assert!(!final_path.exists());
}

#[test]
fn resumed_declared_tail_over_expected_size_preserves_existing_partial() {
    let data = b"0123456789".to_vec();
    let server = FixtureServer::start(data.clone());
    let root = tempfile::tempdir().unwrap();
    let relative_path = "items/one/video.mp4";
    let final_path = root.path().join(relative_path);
    fs::create_dir_all(final_path.parent().unwrap()).unwrap();
    let partial = partial_path(&final_path);
    fs::write(&partial, &data[..3]).unwrap();
    let url = format!("{}/media", server.address);
    save_resume_representation(
        &partial,
        &ResumeRepresentation {
            url: url.clone(),
            etag: Some("\"fixture-v1\"".into()),
            last_modified: None,
            total_bytes: Some(data.len() as u64),
        },
    )
    .unwrap();
    let engine = DownloadEngine::new(root.path(), DownloadPolicy::default()).unwrap();
    let error = engine
        .transfer(
            &TransferRequest {
                url,
                relative_path: relative_path.into(),
                expected_bytes: Some(8),
                expected_sha256: None,
            },
            &AtomicBool::new(false),
        )
        .unwrap_err();
    assert_eq!(error.kind, ErrorKind::IntegrityFailure);
    assert_eq!(fs::read(&partial).unwrap(), &data[..3]);
    assert!(!final_path.exists());
}

#[test]
fn resumed_chunked_tail_aborts_without_appending_over_expected_size() {
    let server = RawHttpFixtureServer::start(|mut stream| {
        drain_http_request(&mut stream);
        let _ = stream.write_all(
                b"HTTP/1.1 206 Partial Content\r\nContent-Range: bytes 3-9/10\r\nETag: \"fixture-v1\"\r\nTransfer-Encoding: chunked\r\nConnection: close\r\n\r\n7\r\n3456789\r\n0\r\n\r\n",
            );
    });
    let root = tempfile::tempdir().unwrap();
    let relative_path = "items/one/video.mp4";
    let final_path = root.path().join(relative_path);
    fs::create_dir_all(final_path.parent().unwrap()).unwrap();
    let partial = partial_path(&final_path);
    fs::write(&partial, b"012").unwrap();
    let url = format!("{}/media", server.address);
    save_resume_representation(
        &partial,
        &ResumeRepresentation {
            url: url.clone(),
            etag: Some("\"fixture-v1\"".into()),
            last_modified: None,
            total_bytes: Some(10),
        },
    )
    .unwrap();
    let engine = DownloadEngine::new(root.path(), DownloadPolicy::default()).unwrap();
    let error = engine
        .transfer(
            &TransferRequest {
                url,
                relative_path: relative_path.into(),
                expected_bytes: Some(5),
                expected_sha256: None,
            },
            &AtomicBool::new(false),
        )
        .unwrap_err();
    assert_eq!(error.kind, ErrorKind::IntegrityFailure);
    assert_eq!(fs::read(&partial).unwrap(), b"012");
    assert!(!final_path.exists());
}

#[test]
fn oversized_existing_partial_is_rejected_before_any_network_request() {
    let root = tempfile::tempdir().unwrap();
    let relative_path = "items/oversized/video.mp4";
    let final_path = root.path().join(relative_path);
    fs::create_dir_all(final_path.parent().unwrap()).unwrap();
    let partial = partial_path(&final_path);
    fs::write(&partial, b"too large").unwrap();
    let engine = DownloadEngine::new(
        root.path(),
        DownloadPolicy {
            max_asset_bytes: 4,
            ..DownloadPolicy::default()
        },
    )
    .unwrap();
    let error = engine
        .transfer(
            &TransferRequest {
                url: "http://127.0.0.1:1/unreachable".into(),
                relative_path: relative_path.into(),
                expected_bytes: None,
                expected_sha256: None,
            },
            &AtomicBool::new(false),
        )
        .unwrap_err();
    assert_eq!(error.kind, ErrorKind::InvalidInput);
    assert_eq!(fs::read(&partial).unwrap(), b"too large");
    assert!(!final_path.exists());
}

#[test]
fn resumed_response_over_asset_limit_is_rejected_before_appending() {
    let data = b"0123456789".to_vec();
    let server = FixtureServer::start(data.clone());
    let root = tempfile::tempdir().unwrap();
    let relative_path = "items/one/video.mp4";
    let final_path = root.path().join(relative_path);
    fs::create_dir_all(final_path.parent().unwrap()).unwrap();
    let partial = partial_path(&final_path);
    fs::write(&partial, &data[..3]).unwrap();
    let url = format!("{}/media", server.address);
    save_resume_representation(
        &partial,
        &ResumeRepresentation {
            url: url.clone(),
            etag: Some("\"fixture-v1\"".into()),
            last_modified: None,
            total_bytes: Some(data.len() as u64),
        },
    )
    .unwrap();
    let engine = DownloadEngine::new(
        root.path(),
        DownloadPolicy {
            max_asset_bytes: 6,
            ..DownloadPolicy::default()
        },
    )
    .unwrap();
    let error = engine
        .transfer(
            &TransferRequest {
                url,
                relative_path: relative_path.into(),
                expected_bytes: None,
                expected_sha256: None,
            },
            &AtomicBool::new(false),
        )
        .unwrap_err();
    assert_eq!(error.kind, ErrorKind::InvalidInput);
    assert_eq!(fs::read(&partial).unwrap(), &data[..3]);
    assert!(!final_path.exists());
}

#[test]
fn malformed_resume_range_restarts_without_appending_unproven_bytes() {
    let data = b"0123456789".to_vec();
    let server = Server::http("127.0.0.1:0").unwrap();
    let address = format!("http://{}", server.server_addr());
    let served = data.clone();
    let server_thread =
        thread::spawn(move || {
            let ranged = server
                .recv_timeout(Duration::from_secs(5))
                .unwrap()
                .unwrap();
            assert!(ranged.headers().iter().any(|header| {
                header.field.equiv("Range") && header.value.as_str() == "bytes=3-"
            }));
            let mut response =
                TinyResponse::from_data(served[3..].to_vec()).with_status_code(StatusCode(206));
            // The body has seven bytes, but the server falsely advertises
            // only bytes 3..=5. Accepting just the start corrupts an append.
            response.add_header(Header::from_bytes(b"Content-Range", b"bytes 3-5/10").unwrap());
            response.add_header(Header::from_bytes(b"ETag", b"\"fixture-v1\"").unwrap());
            ranged.respond(response).unwrap();

            let fresh = server
                .recv_timeout(Duration::from_secs(5))
                .unwrap()
                .unwrap();
            assert!(
                !fresh
                    .headers()
                    .iter()
                    .any(|header| header.field.equiv("Range"))
            );
            let mut response = TinyResponse::from_data(served);
            response.add_header(Header::from_bytes(b"ETag", b"\"fixture-v1\"").unwrap());
            fresh.respond(response).unwrap();
        });

    let root = tempfile::tempdir().unwrap();
    let final_path = root.path().join("items/one/video.mp4");
    fs::create_dir_all(final_path.parent().unwrap()).unwrap();
    let partial = partial_path(&final_path);
    fs::write(&partial, &data[..3]).unwrap();
    let url = format!("{address}/media");
    save_resume_representation(
        &partial,
        &ResumeRepresentation {
            url: url.clone(),
            etag: Some("\"fixture-v1\"".into()),
            last_modified: None,
            total_bytes: Some(data.len() as u64),
        },
    )
    .unwrap();
    let engine = DownloadEngine::new(root.path(), DownloadPolicy::default()).unwrap();
    let result = engine
        .transfer(
            &TransferRequest {
                url,
                relative_path: "items/one/video.mp4".into(),
                expected_bytes: Some(data.len() as u64),
                expected_sha256: None,
            },
            &AtomicBool::new(false),
        )
        .unwrap();
    server_thread.join().unwrap();
    assert!(!result.resumed);
    assert_eq!(fs::read(final_path).unwrap(), data);
}

#[test]
fn range_416_for_completed_partial_restarts_from_zero() {
    let body = b"0123456789".to_vec();
    let server = Server::http("127.0.0.1:0").unwrap();
    let url = format!("http://{}/media", server.server_addr());
    let served = body.clone();
    let server_thread =
        thread::spawn(move || {
            let ranged = server
                .recv_timeout(Duration::from_secs(5))
                .unwrap()
                .unwrap();
            assert!(ranged.headers().iter().any(|header| {
                header.field.equiv("Range") && header.value.as_str() == "bytes=10-"
            }));
            let mut response =
                TinyResponse::from_data(Vec::<u8>::new()).with_status_code(StatusCode(416));
            response.add_header(Header::from_bytes(b"Content-Range", b"bytes */10").unwrap());
            ranged.respond(response).unwrap();

            let full = server
                .recv_timeout(Duration::from_secs(5))
                .unwrap()
                .unwrap();
            assert!(
                !full
                    .headers()
                    .iter()
                    .any(|header| header.field.equiv("Range"))
            );
            let mut response = TinyResponse::from_data(served);
            response.add_header(Header::from_bytes(b"ETag", b"\"fixture-v1\"").unwrap());
            full.respond(response).unwrap();
        });
    let root = tempfile::tempdir().unwrap();
    let path = root.path().join("items/one/video.mp4");
    fs::create_dir_all(path.parent().unwrap()).unwrap();
    let partial = partial_path(&path);
    fs::write(&partial, &body).unwrap();
    save_resume_representation(
        &partial,
        &ResumeRepresentation {
            url: url.clone(),
            etag: Some("\"fixture-v1\"".into()),
            last_modified: None,
            total_bytes: Some(body.len() as u64),
        },
    )
    .unwrap();
    let engine = DownloadEngine::new(root.path(), DownloadPolicy::default()).unwrap();
    let result = engine
        .transfer(
            &TransferRequest {
                url,
                relative_path: "items/one/video.mp4".into(),
                expected_bytes: Some(body.len() as u64),
                expected_sha256: None,
            },
            &AtomicBool::new(false),
        )
        .unwrap();
    server_thread.join().unwrap();
    assert!(!result.resumed);
    assert_eq!(fs::read(path).unwrap(), body);
}
