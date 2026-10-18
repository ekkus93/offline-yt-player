use super::*;

#[test]
fn orphan_cleanup_handles_deep_library_tree_without_recursion() {
    let root = tempfile::tempdir().unwrap();
    let mut directory = root.path().join("items");
    for _ in 0..256 {
        directory.push("d");
    }
    fs::create_dir_all(&directory).unwrap();
    let partial = directory.join(".video.mp4.partial");
    let sidecar = resume_metadata_path(&partial);
    let completed = directory.join("video.mp4");
    fs::write(&partial, b"incomplete").unwrap();
    fs::write(&sidecar, b"stale metadata").unwrap();
    fs::write(&completed, b"completed asset").unwrap();

    let engine = DownloadEngine::new(root.path(), DownloadPolicy::default()).unwrap();
    assert_eq!(engine.cleanup_orphan_partials().unwrap(), 1);
    assert!(!partial.exists());
    assert!(!sidecar.exists());
    assert_eq!(fs::read(&completed).unwrap(), b"completed asset");
    assert_eq!(engine.cleanup_orphan_partials().unwrap(), 0);
}

#[test]
fn orphan_cleanup_streams_wide_sibling_directories_and_preserves_media() {
    let root = tempfile::tempdir().unwrap();
    let items = root.path().join("items");
    fs::create_dir_all(&items).unwrap();
    for index in 0..512 {
        let directory = items.join(format!("item-{index}"));
        fs::create_dir(&directory).unwrap();
        fs::write(directory.join(".clip.partial"), b"pending").unwrap();
        fs::write(directory.join(".clip.partial.resume.json"), b"resume").unwrap();
        fs::write(directory.join("complete.mp4"), b"preserve").unwrap();
    }
    let engine = DownloadEngine::new(root.path(), DownloadPolicy::default()).unwrap();
    assert_eq!(engine.cleanup_orphan_partials().unwrap(), 512);
    for index in 0..512 {
        let directory = items.join(format!("item-{index}"));
        assert!(!directory.join(".clip.partial").exists());
        assert!(!directory.join(".clip.partial.resume.json").exists());
        assert_eq!(
            fs::read(directory.join("complete.mp4")).unwrap(),
            b"preserve"
        );
    }
    assert_eq!(engine.cleanup_orphan_partials().unwrap(), 0);
}

#[test]
fn retry_backoff_is_bounded() {
    assert_eq!(retry_delay(1, 0), Duration::from_secs(1));
    assert_eq!(retry_delay(2, 100), Duration::from_millis(2_100));
    assert!(retry_delay(99, 999) <= Duration::from_secs(30));
}

#[test]
fn progress_callback_reports_intermediate_written_bytes() {
    let data = vec![0x41; COPY_BUFFER_BYTES * 3];
    let server = FixtureServer::start(data.clone());
    let temp = tempfile::tempdir().unwrap();
    let engine = DownloadEngine::new(temp.path(), DownloadPolicy::default()).unwrap();
    let request = TransferRequest {
        url: format!("{}/media", server.address),
        relative_path: "items/one/video.mp4".into(),
        expected_bytes: Some(data.len() as u64),
        expected_sha256: None,
    };
    let mut observed = Vec::new();
    let result = engine
        .transfer_with_progress(&request, &AtomicBool::new(false), |bytes| {
            observed.push(bytes);
            Ok(())
        })
        .unwrap();
    assert!(
        observed
            .iter()
            .any(|bytes| *bytes > 0 && *bytes < result.bytes)
    );
    assert_eq!(observed.last(), Some(&result.bytes));
    assert!(observed.windows(2).all(|window| window[0] <= window[1]));
}

#[test]
fn downloads_fixture_and_verifies_size() {
    let data = b"deterministic fixture media".repeat(100);
    let server = FixtureServer::start(data.clone());
    let temp = tempfile::tempdir().unwrap();
    let engine = DownloadEngine::new(temp.path(), DownloadPolicy::default()).unwrap();
    let request = TransferRequest {
        url: format!("{}/media", server.address),
        relative_path: "items/one/video.mp4".into(),
        expected_bytes: Some(data.len() as u64),
        expected_sha256: None,
    };
    let result = engine.transfer(&request, &AtomicBool::new(false)).unwrap();
    assert_eq!(result.bytes, data.len() as u64);
    assert!(!result.resumed);
    assert_eq!(
        fs::read(temp.path().join(&request.relative_path)).unwrap(),
        data
    );
    assert!(
        !resume_metadata_path(&partial_path(&temp.path().join(&request.relative_path))).exists()
    );
}

#[test]
fn resumes_existing_partial_only_with_matching_persisted_identity() {
    let data = b"range fixture".repeat(100);
    let server = FixtureServer::start(data.clone());
    let temp = tempfile::tempdir().unwrap();
    let final_path = temp.path().join("items/one/video.mp4");
    fs::create_dir_all(final_path.parent().unwrap()).unwrap();
    let partial = partial_path(&final_path);
    fs::write(&partial, &data[..100]).unwrap();
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
    let engine = DownloadEngine::new(temp.path(), DownloadPolicy::default()).unwrap();
    let request = TransferRequest {
        url,
        relative_path: "items/one/video.mp4".into(),
        expected_bytes: Some(data.len() as u64),
        expected_sha256: None,
    };
    let result = engine.transfer(&request, &AtomicBool::new(false)).unwrap();
    assert!(result.resumed);
    assert_eq!(fs::read(final_path).unwrap(), data);
    assert_eq!(load_resume_representation(&partial).unwrap(), None);
}

#[test]
fn partial_without_identity_restarts_from_zero() {
    let data = b"safe restart fixture".repeat(100);
    let server = FixtureServer::start(data.clone());
    let temp = tempfile::tempdir().unwrap();
    let final_path = temp.path().join("items/one/video.mp4");
    fs::create_dir_all(final_path.parent().unwrap()).unwrap();
    fs::write(partial_path(&final_path), b"unproven stale bytes").unwrap();
    let engine = DownloadEngine::new(temp.path(), DownloadPolicy::default()).unwrap();
    let request = TransferRequest {
        url: format!("{}/media", server.address),
        relative_path: "items/one/video.mp4".into(),
        expected_bytes: Some(data.len() as u64),
        expected_sha256: None,
    };
    let result = engine.transfer(&request, &AtomicBool::new(false)).unwrap();
    assert!(!result.resumed);
    assert_eq!(fs::read(final_path).unwrap(), data);
}

#[test]
fn malformed_response_does_not_reflect_signed_url_or_tokens() {
    let server = RawHttpFixtureServer::start(|mut stream| {
        drain_http_request(&mut stream);
        stream.write_all(b"not http at all\r\n\r\n").unwrap();
        let _ = stream.shutdown(Shutdown::Both);
    });
    let temp = tempfile::tempdir().unwrap();
    let engine = DownloadEngine::new(temp.path(), DownloadPolicy::default()).unwrap();
    let signed_url = format!(
        "{}/media?signature=SECRET_SIGNED_URL&token=BEARER_SECRET&X-Goog-Signature=COOKIE_SECRET",
        server.address
    );

    let error = engine
        .transfer(&transfer_from(signed_url), &AtomicBool::new(false))
        .unwrap_err();

    assert_eq!(error.kind, ErrorKind::NetworkUnavailable);
    assert!(error.retryable);
    assert_eq!(
        error.message,
        "Network request failed before a response was received"
    );
    for marker in [
        "SECRET_SIGNED_URL",
        "BEARER_SECRET",
        "COOKIE_SECRET",
        "signature=",
        "token=",
        "X-Goog-Signature",
    ] {
        assert!(
            !error.message.contains(marker),
            "diagnostic leaked secret marker {marker}: {}",
            error.message
        );
    }
}

#[test]
fn mid_body_disconnect_is_retryable_remote_failure_not_storage() {
    let server = RawHttpFixtureServer::start(|mut stream| {
        drain_http_request(&mut stream);
        stream
            .write_all(
                b"HTTP/1.1 200 OK\r\nContent-Length: 1024\r\nConnection: close\r\n\r\npartial body",
            )
            .unwrap();
        let _ = stream.shutdown(Shutdown::Both);
    });
    let temp = tempfile::tempdir().unwrap();
    let engine = DownloadEngine::new(temp.path(), DownloadPolicy::default()).unwrap();

    let error = engine
        .transfer(
            &transfer_from(format!("{}/media", server.address)),
            &AtomicBool::new(false),
        )
        .unwrap_err();

    assert_ne!(error.kind, ErrorKind::Internal);
    assert!(
        matches!(
            error.kind,
            ErrorKind::IntegrityFailure | ErrorKind::NetworkUnavailable | ErrorKind::NetworkTimeout
        ),
        "unexpected error: {error:?}"
    );
    assert!(error.retryable);
    assert!(!error.message.starts_with("storage error:"));
}

#[test]
fn remote_body_timed_out_io_error_maps_to_network_timeout() {
    let error = remote_body_read_error(std::io::Error::new(
        IoErrorKind::TimedOut,
        "body read timed out",
    ));
    assert_eq!(error.kind, ErrorKind::NetworkTimeout);
    assert!(error.retryable);
    assert!(!error.message.starts_with("storage error:"));
}

#[test]
fn delayed_response_body_is_retryable_remote_failure_not_storage() {
    let server = RawHttpFixtureServer::start(|mut stream| {
        drain_http_request(&mut stream);
        stream
            .write_all(b"HTTP/1.1 200 OK\r\nContent-Length: 1024\r\n\r\n")
            .unwrap();
        thread::sleep(Duration::from_millis(500));
    });
    let temp = tempfile::tempdir().unwrap();
    let policy = DownloadPolicy {
        request_timeout: Duration::from_millis(75),
        ..DownloadPolicy::default()
    };
    let engine = DownloadEngine::new(temp.path(), policy).unwrap();

    let error = engine
        .transfer(
            &transfer_from(format!("{}/media", server.address)),
            &AtomicBool::new(false),
        )
        .unwrap_err();

    assert_ne!(error.kind, ErrorKind::Internal);
    assert!(
        matches!(
            error.kind,
            ErrorKind::IntegrityFailure | ErrorKind::NetworkUnavailable | ErrorKind::NetworkTimeout
        ),
        "unexpected error: {error:?}"
    );
    assert!(error.retryable);
    assert!(!error.message.starts_with("storage error:"));
}

#[test]
fn local_enospc_still_maps_to_insufficient_storage() {
    let error = io_error(std::io::Error::from_raw_os_error(28));
    assert_eq!(error.kind, ErrorKind::InsufficientStorage);
    assert!(!error.retryable);
    assert!(error.message.starts_with("storage error:"));
}

#[test]
fn write_time_enospc_preserves_partial_and_next_attempt_resumes_to_completion() {
    let data = b"write-time ENOSPC fixture".repeat(128);
    let server = FixtureServer::start(data.clone());
    let temp = tempfile::tempdir().unwrap();
    let request = TransferRequest {
        url: format!("{}/media", server.address),
        relative_path: "items/enospc/video.mp4".into(),
        expected_bytes: Some(data.len() as u64),
        expected_sha256: None,
    };
    let final_path = temp.path().join(&request.relative_path);
    let partial = partial_path(&final_path);

    let mut failing = DownloadEngine::new(temp.path(), DownloadPolicy::default()).unwrap();
    failing.test_write_failure_after_bytes = Some(17);
    let error = failing
        .transfer(&request, &AtomicBool::new(false))
        .unwrap_err();

    assert_eq!(error.kind, ErrorKind::InsufficientStorage);
    assert!(!error.retryable);
    assert!(
        !final_path.exists(),
        "write failure must not promote a final asset"
    );
    assert_eq!(
        fs::metadata(&partial).unwrap().len(),
        17,
        "real partial bytes must remain recoverable after ENOSPC",
    );
    assert!(
        load_resume_representation(&partial).unwrap().is_some(),
        "the retained partial must preserve its remote representation identity",
    );

    let retry = DownloadEngine::new(temp.path(), DownloadPolicy::default()).unwrap();
    let completed = retry.transfer(&request, &AtomicBool::new(false)).unwrap();

    assert!(
        completed.resumed,
        "retry should reuse the proven partial range"
    );
    assert_eq!(fs::read(&final_path).unwrap(), data);
    assert!(!partial.exists());
    assert_eq!(load_resume_representation(&partial).unwrap(), None);
}

#[test]
fn storage_preflight_rejects_insufficient_space() {
    let temp = tempfile::tempdir().unwrap();
    let engine = DownloadEngine::new(temp.path(), DownloadPolicy::default()).unwrap();
    let error = engine.preflight_space(Some(100), Some(99)).unwrap_err();
    assert_eq!(error.kind, ErrorKind::InsufficientStorage);
}

#[cfg(unix)]
#[test]
fn transfer_preflights_real_filesystem_space_before_network() {
    let temp = tempfile::tempdir().unwrap();
    let policy = DownloadPolicy {
        max_asset_bytes: u64::MAX,
        ..DownloadPolicy::default()
    };
    let engine = DownloadEngine::new(temp.path(), policy).unwrap();
    let request = TransferRequest {
        url: "http://127.0.0.1:1/preflight-must-run-first".into(),
        relative_path: "items/storage-preflight/video.mp4".into(),
        expected_bytes: Some(u64::MAX),
        expected_sha256: None,
    };

    let error = engine
        .transfer(&request, &AtomicBool::new(false))
        .unwrap_err();

    assert_eq!(error.kind, ErrorKind::InsufficientStorage);
    assert!(
        !temp
            .path()
            .join("items/storage-preflight/.video.mp4.partial")
            .exists()
    );
    assert!(
        !temp
            .path()
            .join("items/storage-preflight/video.mp4")
            .exists()
    );
}

#[cfg(unix)]
#[test]
fn stale_partial_requires_full_storage_capacity() {
    let root = tempfile::tempdir().unwrap();
    // A sparse partial consumes almost no storage. Concurrent CI work can
    // reclaim gigabytes between this observation and the real preflight.
    // Keep the shortage unambiguous across routine workspace churn.
    let expected = available_space_bytes(root.path())
        .unwrap()
        .saturating_add(8 * 1024 * 1024 * 1024);
    let relative_path = "items/storage/video.mp4";
    let final_path = root.path().join(relative_path);
    fs::create_dir_all(final_path.parent().unwrap()).unwrap();
    File::create(partial_path(&final_path))
        .unwrap()
        .set_len(expected.saturating_sub(1))
        .unwrap();
    let server = FixtureServer::start(b"fresh".to_vec());
    let engine = DownloadEngine::new(
        root.path(),
        DownloadPolicy {
            max_asset_bytes: u64::MAX,
            ..DownloadPolicy::default()
        },
    )
    .unwrap();
    let error = engine
        .transfer(
            &TransferRequest {
                url: format!("{}/media", server.address),
                relative_path: relative_path.into(),
                expected_bytes: Some(expected),
                expected_sha256: None,
            },
            &AtomicBool::new(false),
        )
        .unwrap_err();
    assert_eq!(error.kind, ErrorKind::InsufficientStorage);
    assert!(!final_path.exists());
}

#[test]
fn pre_canceled_transfer_returns_canceled_without_networking() {
    let temp = tempfile::tempdir().unwrap();
    let engine = DownloadEngine::new(temp.path(), DownloadPolicy::default()).unwrap();
    let request = TransferRequest {
        url: "http://127.0.0.1:1/unreachable".into(),
        relative_path: "items/one/video.mp4".into(),
        expected_bytes: None,
        expected_sha256: None,
    };
    let error = engine
        .transfer(&request, &AtomicBool::new(true))
        .unwrap_err();
    assert_eq!(error.kind, ErrorKind::Canceled);
    assert!(!temp.path().join("items/one/video.mp4").exists());
}

#[cfg(unix)]
#[test]
fn transfer_rejects_symlink_parent_escape_without_touching_outside_file() {
    use std::os::unix::fs::symlink;

    let root = tempfile::tempdir().unwrap();

    let outside = tempfile::tempdir().unwrap();
    fs::create_dir_all(root.path().join("items")).unwrap();
    symlink(outside.path(), root.path().join("items/one")).unwrap();
    let outside_file = outside.path().join(".video.mp4.partial");
    fs::write(&outside_file, b"outside bytes").unwrap();
    let engine = DownloadEngine::new(root.path(), DownloadPolicy::default()).unwrap();
    let request = TransferRequest {
        url: "http://127.0.0.1:1/unreachable".into(),
        relative_path: "items/one/video.mp4".into(),
        expected_bytes: None,
        expected_sha256: None,
    };

    let error = engine
        .transfer(&request, &AtomicBool::new(false))
        .unwrap_err();

    assert_eq!(error.kind, ErrorKind::InvalidInput);
    assert_eq!(fs::read(&outside_file).unwrap(), b"outside bytes");
    assert!(!outside.path().join("video.mp4").exists());
}

#[cfg(unix)]
#[test]
fn cleanup_orphan_partials_rejects_symlinked_directories_without_touching_outside() {
    use std::os::unix::fs::symlink;

    let root = tempfile::tempdir().unwrap();
    let outside = tempfile::tempdir().unwrap();
    fs::write(outside.path().join(".stolen.partial"), b"outside bytes").unwrap();
    symlink(outside.path(), root.path().join("items")).unwrap();
    let engine = DownloadEngine::new(root.path(), DownloadPolicy::default()).unwrap();

    let error = engine.cleanup_orphan_partials().unwrap_err();

    assert_eq!(error.kind, ErrorKind::InvalidInput);
    assert_eq!(
        fs::read(outside.path().join(".stolen.partial")).unwrap(),
        b"outside bytes"
    );
}

#[cfg(unix)]
#[test]
fn transfer_and_cleanup_reject_symlinked_library_root_without_touching_outside() {
    use std::os::unix::fs::symlink;

    let parent = tempfile::tempdir().unwrap();
    let outside = tempfile::tempdir().unwrap();
    let root = parent.path().join("library");
    symlink(outside.path(), &root).unwrap();
    let outside_partial = outside.path().join(".video.mp4.partial");
    fs::write(&outside_partial, b"outside bytes").unwrap();

    let engine = DownloadEngine::new(root.as_path(), DownloadPolicy::default()).unwrap();
    let request = TransferRequest {
        url: "http://127.0.0.1:1/unreachable".into(),
        relative_path: "video.mp4".into(),
        expected_bytes: None,
        expected_sha256: None,
    };
    let transfer_error = engine
        .transfer(&request, &AtomicBool::new(false))
        .unwrap_err();
    assert_eq!(transfer_error.kind, ErrorKind::InvalidInput);
    let cleanup_error = engine.cleanup_orphan_partials().unwrap_err();
    assert_eq!(cleanup_error.kind, ErrorKind::InvalidInput);
    assert_eq!(fs::read(&outside_partial).unwrap(), b"outside bytes");
    assert!(!outside.path().join("video.mp4").exists());
}

#[test]
fn transfer_and_cleanup_reject_non_directory_library_root() {
    let parent = tempfile::tempdir().unwrap();
    let root = parent.path().join("library");
    fs::write(&root, b"not a directory").unwrap();
    let engine = DownloadEngine::new(root.as_path(), DownloadPolicy::default()).unwrap();
    let request = TransferRequest {
        url: "http://127.0.0.1:1/unreachable".into(),
        relative_path: "video.mp4".into(),
        expected_bytes: None,
        expected_sha256: None,
    };
    let transfer_error = engine
        .transfer(&request, &AtomicBool::new(false))
        .unwrap_err();
    assert_eq!(transfer_error.kind, ErrorKind::InvalidInput);
    let cleanup_error = engine.cleanup_orphan_partials().unwrap_err();
    assert_eq!(cleanup_error.kind, ErrorKind::InvalidInput);
    assert_eq!(fs::read(&root).unwrap(), b"not a directory");
}
