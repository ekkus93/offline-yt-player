use super::*;

#[test]
fn executes_real_transfer_promotes_item_and_honors_concurrency() {
    let data = b"worker fixture".repeat(32);
    let server = FixtureServer::start(data.clone());
    let store = LibraryStore::open_in_memory().unwrap();
    store
        .save_download_snapshot(&snapshot("job-a", DownloadState::Queued))
        .unwrap();
    store
        .save_download_snapshot(&snapshot("job-b", DownloadState::Queued))
        .unwrap();
    let root = tempfile::tempdir().unwrap();
    let worker = DownloadWorker::new(store.clone(), root.path(), DownloadPolicy::default(), 1);
    let report = worker
        .execute_ready_at(
            &[
                plan("job-a", format!("{}/a.mp4", server.address), data.len()),
                plan("job-b", format!("{}/b.mp4", server.address), data.len()),
            ],
            &AtomicBool::new(false),
            10_000,
        )
        .unwrap();
    assert_eq!(report.claimed, vec!["job-a"]);
    assert_eq!(report.completed, vec!["job-a"]);
    assert!(root.path().join("items/job-a/video.mp4").is_file());
    assert!(store.staged_job_ids().unwrap().is_empty());
    let item = store.get("job-a").unwrap().unwrap();
    assert!(item.completed);
    assert_eq!(item.duration_ms, Some(60_000));
    assert_eq!(item.assets[0].bytes, data.len() as u64);
    let jobs = store.load_download_snapshots().unwrap();
    assert_eq!(jobs[0].state, DownloadState::Completed);
    assert_eq!(jobs[0].bytes_downloaded, data.len() as u64);
    assert_eq!(jobs[1].state, DownloadState::Queued);
}

#[test]
fn worker_persists_chunk_progress_before_single_asset_completion() {
    let data = vec![0x42; 256 * 1024];
    let server = FixtureServer::start(data.clone());
    let store = LibraryStore::open_in_memory().unwrap();
    store
        .save_download_snapshot(&snapshot("chunk-progress", DownloadState::Queued))
        .unwrap();
    let root = tempfile::tempdir().unwrap();
    let worker = DownloadWorker::new(store.clone(), root.path(), DownloadPolicy::default(), 1);
    let report = worker
        .execute_ready_at(
            &[plan(
                "chunk-progress",
                format!("{}/chunk.mp4", server.address),
                data.len(),
            )],
            &AtomicBool::new(false),
            10_000,
        )
        .unwrap();
    assert_eq!(report.completed, vec!["chunk-progress"]);
    assert!(report.progress_events.iter().any(|event| {
        matches!(
            event,
            CoreEvent::DownloadProgress { bytes_downloaded, .. }
                if *bytes_downloaded > 0 && *bytes_downloaded < data.len() as u64
        )
    }));
    assert_eq!(
        store.load_download_snapshots().unwrap()[0].bytes_downloaded,
        data.len() as u64
    );
}

#[test]
fn worker_emits_real_progress_bytes_speed_and_eta_for_known_length_transfer() {
    let data = b"metric fixture".repeat(64);
    let server =
        FixtureServer::start_with_delay(data.clone(), std::time::Duration::from_millis(100));
    let store = LibraryStore::open_in_memory().unwrap();
    store
        .save_download_snapshot(&snapshot("metric-job", DownloadState::Queued))
        .unwrap();
    let root = tempfile::tempdir().unwrap();
    let worker = DownloadWorker::new(store.clone(), root.path(), DownloadPolicy::default(), 1);

    let report = worker
        .execute_ready_at(
            &[plan(
                "metric-job",
                format!("{}/metric.mp4", server.address),
                data.len(),
            )],
            &AtomicBool::new(false),
            50_000,
        )
        .unwrap();

    let CoreEvent::DownloadProgress {
        job_id,
        bytes_downloaded,
        total_bytes,
        metrics,
    } = report.progress_events.last().unwrap()
    else {
        panic!("worker emitted a non-progress event");
    };
    assert_eq!(job_id, "metric-job");
    assert_eq!(*bytes_downloaded, data.len() as u64);
    assert_eq!(*total_bytes, Some(data.len() as u64));
    // With a real network delay, measured speed is positive but does not
    // pretend that each transfer took precisely one synthetic second.
    assert!(metrics.bytes_per_second.is_some_and(|speed| speed > 0));
    assert_eq!(metrics.eta_seconds, Some(0));
}

#[test]
fn worker_does_not_fabricate_eta_for_unknown_length_transfer() {
    let data = b"unknown metric fixture".repeat(32);
    let server =
        FixtureServer::start_with_delay(data.clone(), std::time::Duration::from_millis(100));
    let store = LibraryStore::open_in_memory().unwrap();
    store
        .save_download_snapshot(&snapshot("unknown-job", DownloadState::Queued))
        .unwrap();
    let root = tempfile::tempdir().unwrap();
    let worker = DownloadWorker::new(store.clone(), root.path(), DownloadPolicy::default(), 1);

    let report = worker
        .execute_ready_at(
            &[unknown_length_plan(
                "unknown-job",
                format!("{}/unknown.mp4", server.address),
            )],
            &AtomicBool::new(false),
            60_000,
        )
        .unwrap();

    let CoreEvent::DownloadProgress {
        bytes_downloaded,
        total_bytes,
        metrics,
        ..
    } = report.progress_events.last().unwrap()
    else {
        panic!("worker emitted a non-progress event");
    };
    assert_eq!(*bytes_downloaded, data.len() as u64);
    assert_eq!(*total_bytes, None);
    assert!(metrics.bytes_per_second.is_some_and(|speed| speed > 0));
    assert_eq!(metrics.eta_seconds, None);
}

#[test]
fn duplicate_work_ids_fail_before_any_durable_claim_or_transfer() {
    let store = LibraryStore::open_in_memory().unwrap();
    store
        .save_download_snapshot(&snapshot("duplicate-job", DownloadState::Queued))
        .unwrap();
    let root = tempfile::tempdir().unwrap();
    let worker = DownloadWorker::new(store.clone(), root.path(), DownloadPolicy::default(), 1);
    let first = plan("duplicate-job", "http://127.0.0.1:1/first".into(), 5);
    let second = plan("duplicate-job", "http://127.0.0.1:1/second".into(), 5);

    let error = worker
        .execute_ready_at(&[first, second], &AtomicBool::new(false), 10_000)
        .unwrap_err();
    assert_eq!(error.kind, ErrorKind::InvalidInput);
    let durable = store.load_download_snapshots().unwrap().remove(0);
    assert_eq!(durable.state, DownloadState::Queued);
    assert_eq!(durable.attempt, 0);
    assert!(store.get("duplicate-job").unwrap().is_none());
    assert!(store.staged_job_ids().unwrap().is_empty());
}

#[test]
fn blank_work_id_fails_before_claiming_other_valid_work() {
    let store = LibraryStore::open_in_memory().unwrap();
    store
        .save_download_snapshot(&snapshot("valid-job", DownloadState::Queued))
        .unwrap();
    let root = tempfile::tempdir().unwrap();
    let worker = DownloadWorker::new(store.clone(), root.path(), DownloadPolicy::default(), 1);
    let valid = plan("valid-job", "http://127.0.0.1:1/valid".into(), 5);
    let blank = plan("   ", "http://127.0.0.1:1/blank".into(), 5);

    let error = worker
        .execute_ready_at(&[valid, blank], &AtomicBool::new(false), 10_000)
        .unwrap_err();
    assert_eq!(error.kind, ErrorKind::InvalidInput);
    let durable = store.load_download_snapshots().unwrap().remove(0);
    assert_eq!(durable.state, DownloadState::Queued);
    assert_eq!(durable.attempt, 0);
}

#[test]
fn invalid_asset_identities_fail_before_network_transfer_or_staging() {
    for case in [
        "duplicate-id",
        "duplicate-path",
        "normalized-path",
        "blank-id",
        "blank-path",
    ] {
        let job_id = format!("guard-{case}");
        let store = LibraryStore::open_in_memory().unwrap();
        store
            .save_download_snapshot(&snapshot(&job_id, DownloadState::Queued))
            .unwrap();
        let root = tempfile::tempdir().unwrap();
        let worker = DownloadWorker::new(store.clone(), root.path(), DownloadPolicy::default(), 1);
        let mut work = plan(&job_id, "http://127.0.0.1:1/unreachable".into(), 10);
        let mut second = work.plan.assets[0].clone();
        second.asset_id = "secondary".into();
        second.relative_path = format!("items/{job_id}/second.mp4");
        match case {
            "duplicate-id" => second.asset_id = "combined".into(),
            "duplicate-path" => second.relative_path = work.plan.assets[0].relative_path.clone(),
            "normalized-path" => {
                second.relative_path = format!("items/{job_id}/./video.mp4");
            }
            "blank-id" => second.asset_id = "   ".into(),
            "blank-path" => second.relative_path = "  ".into(),
            _ => unreachable!(),
        }
        work.plan.assets.push(second);

        let report = worker
            .execute_ready_at(&[work], &AtomicBool::new(false), 10_000)
            .unwrap();
        assert_eq!(report.failed, vec![job_id.clone()], "{case}");
        assert!(report.completed.is_empty(), "{case}");
        assert!(report.retry_wait.is_empty(), "{case}");
        assert!(store.get(&job_id).unwrap().is_none(), "{case}");
        assert!(store.staged_job_ids().unwrap().is_empty(), "{case}");
        assert!(
            !root
                .path()
                .join(format!("items/{job_id}/video.mp4"))
                .exists()
        );
        let durable = store.load_download_snapshots().unwrap().remove(0);
        assert_eq!(durable.state, DownloadState::Failed, "{case}");
        assert_eq!(durable.attempt, 1, "{case}");
        assert_eq!(durable.bytes_downloaded, 0, "{case}");
        assert_eq!(
            durable.last_error.unwrap().kind,
            ErrorKind::InvalidInput,
            "{case}"
        );
    }
}

#[test]
fn retry_replayed_asset_does_not_double_count_prior_progress() {
    let data = b"retry fixture".repeat(64);
    let server = FixtureServer::start(data.clone());
    let store = LibraryStore::open_in_memory().unwrap();
    let mut prior = snapshot("retry-progress", DownloadState::RetryWait);
    prior.bytes_downloaded = data.len() as u64;
    prior.total_bytes = Some(data.len() as u64);
    prior.attempt = 1;
    prior.retry_at_epoch_ms = Some(10_000);
    store.save_download_snapshot(&prior).unwrap();
    let root = tempfile::tempdir().unwrap();
    let worker = DownloadWorker::new(store.clone(), root.path(), DownloadPolicy::default(), 1);

    let report = worker
        .execute_ready_at(
            &[plan(
                "retry-progress",
                format!("{}/retry.mp4", server.address),
                data.len(),
            )],
            &AtomicBool::new(false),
            10_000,
        )
        .unwrap();
    assert_eq!(report.completed, vec!["retry-progress"]);
    let completed = store.load_download_snapshots().unwrap().remove(0);
    assert_eq!(completed.state, DownloadState::Completed);
    assert_eq!(completed.attempt, 2);
    assert_eq!(completed.bytes_downloaded, data.len() as u64);
    let CoreEvent::DownloadProgress {
        bytes_downloaded,
        total_bytes,
        ..
    } = report.progress_events.last().unwrap()
    else {
        panic!("expected progress event");
    };
    assert_eq!(*bytes_downloaded, data.len() as u64);
    assert_eq!(*total_bytes, Some(data.len() as u64));
}

#[test]
fn retryable_failure_enters_retry_wait_with_attempt_and_error() {
    let store = LibraryStore::open_in_memory().unwrap();
    store
        .save_download_snapshot(&snapshot("job-fail", DownloadState::Queued))
        .unwrap();
    let root = tempfile::tempdir().unwrap();
    let policy = DownloadPolicy {
        max_attempts: 2,
        request_timeout: std::time::Duration::from_millis(100),
        ..DownloadPolicy::default()
    };
    let worker = DownloadWorker::new(store.clone(), root.path(), policy, 2);
    let report = worker
        .execute_ready_at(
            &[plan(
                "job-fail",
                "http://127.0.0.1:1/missing.mp4".into(),
                10,
            )],
            &AtomicBool::new(false),
            5_000,
        )
        .unwrap();
    assert_eq!(report.retry_wait, vec!["job-fail"]);
    let snapshot = store.load_download_snapshots().unwrap().remove(0);
    assert_eq!(snapshot.state, DownloadState::RetryWait);
    assert_eq!(snapshot.attempt, 1);
    assert!(snapshot.retry_at_epoch_ms.unwrap() > 5_000);
    assert!(snapshot.last_error.unwrap().retryable);
}

#[test]
fn interrupted_claims_are_repaired_after_process_death() {
    let store = LibraryStore::open_in_memory().unwrap();
    store
        .save_download_snapshot(&snapshot("resolving", DownloadState::Resolving))
        .unwrap();
    store
        .save_download_snapshot(&snapshot("downloading", DownloadState::Downloading))
        .unwrap();
    store
        .save_download_snapshot(&snapshot("queued", DownloadState::Queued))
        .unwrap();
    let root = tempfile::tempdir().unwrap();
    let worker = DownloadWorker::new(store.clone(), root.path(), DownloadPolicy::default(), 2);
    let report = worker.repair_interrupted_claims_at(42_000).unwrap();
    assert_eq!(report.repaired, vec!["downloading", "resolving"]);
    let snapshots = store.load_download_snapshots().unwrap();
    assert_eq!(
        snapshots
            .iter()
            .filter(|snapshot| snapshot.state == DownloadState::RetryWait)
            .count(),
        2
    );
    assert_eq!(
        snapshots
            .iter()
            .find(|snapshot| snapshot.job_id == "queued")
            .unwrap()
            .state,
        DownloadState::Queued
    );
}

#[test]
fn durable_pause_is_not_reclassified_as_terminal_cancel() {
    let data = vec![7_u8; 4 * 1024 * 1024];
    let server =
        FixtureServer::start_with_delay(data.clone(), std::time::Duration::from_millis(100));
    let store = LibraryStore::open_in_memory().unwrap();
    store
        .save_download_snapshot(&snapshot("pause-job", DownloadState::Queued))
        .unwrap();
    let root = tempfile::tempdir().unwrap();
    let worker = DownloadWorker::new(store.clone(), root.path(), DownloadPolicy::default(), 1);
    let work = plan(
        "pause-job",
        format!("{}/pause.mp4", server.address),
        data.len(),
    );
    let control_store = store.clone();
    let controller = thread::spawn(move || {
        for _ in 0..100 {
            let mut current = control_store.load_download_snapshots().unwrap().remove(0);
            if current.state == DownloadState::Downloading {
                let mut machine = DownloadStateMachine::new(current.state);
                machine.transition(DownloadState::Paused).unwrap();
                current.state = machine.state();
                control_store.save_download_snapshot(&current).unwrap();
                return;
            }
            thread::sleep(std::time::Duration::from_millis(5));
        }
        panic!("worker never entered downloading state");
    });
    let report = worker
        .execute_ready_at(&[work], &AtomicBool::new(false), 10_000)
        .unwrap();
    controller.join().unwrap();
    assert_eq!(report.paused, vec!["pause-job"]);
    let durable = store.load_download_snapshots().unwrap().remove(0);
    assert_eq!(durable.state, DownloadState::Paused);
    assert!(report.canceled.is_empty());
}

#[test]
fn platform_stop_before_claim_keeps_durable_work_queued() {
    let store = LibraryStore::open_in_memory().unwrap();
    store
        .save_download_snapshot(&snapshot("platform-preclaim", DownloadState::Queued))
        .unwrap();
    let root = tempfile::tempdir().unwrap();
    let worker = DownloadWorker::new(store.clone(), root.path(), DownloadPolicy::default(), 1);
    let stop = AtomicBool::new(true);
    let report = worker
        .execute_ready_with_platform_stop_at(
            &[plan(
                "platform-preclaim",
                "http://127.0.0.1:1/unused".into(),
                12,
            )],
            &AtomicBool::new(false),
            &stop,
            10_000,
        )
        .unwrap();
    assert!(report.claimed.is_empty());
    assert!(report.canceled.is_empty());
    let durable = store.load_download_snapshots().unwrap().remove(0);
    assert_eq!(durable.state, DownloadState::Queued);
    assert_eq!(durable.attempt, 0);
}

#[test]
fn platform_stop_during_transfer_settles_retryable_not_canceled() {
    let data = vec![8_u8; 4 * 1024 * 1024];
    let server =
        FixtureServer::start_with_delay(data.clone(), std::time::Duration::from_millis(200));
    let store = LibraryStore::open_in_memory().unwrap();
    store
        .save_download_snapshot(&snapshot("platform-transfer", DownloadState::Queued))
        .unwrap();
    let root = tempfile::tempdir().unwrap();
    let worker = DownloadWorker::new(store.clone(), root.path(), DownloadPolicy::default(), 1);
    let stop = Arc::new(AtomicBool::new(false));
    let controller_stop = Arc::clone(&stop);
    let control_store = store.clone();
    let controller = thread::spawn(move || {
        for _ in 0..200 {
            let current = control_store.load_download_snapshots().unwrap().remove(0);
            if current.state == DownloadState::Downloading {
                controller_stop.store(true, Ordering::Release);
                return;
            }
            thread::sleep(std::time::Duration::from_millis(5));
        }
        panic!("platform stop fixture never entered Downloading");
    });
    let report = worker
        .execute_ready_with_platform_stop_at(
            &[plan(
                "platform-transfer",
                format!("{}/video.mp4", server.address),
                data.len(),
            )],
            &AtomicBool::new(false),
            &stop,
            10_000,
        )
        .unwrap();
    controller.join().unwrap();
    assert_eq!(report.retry_wait, vec!["platform-transfer"]);
    assert!(report.canceled.is_empty());
    assert!(report.completed.is_empty());
    let durable = store.load_download_snapshots().unwrap().remove(0);
    assert_eq!(durable.state, DownloadState::RetryWait);
    assert_eq!(durable.retry_at_epoch_ms, Some(10_000));
    assert!(durable.last_error.unwrap().retryable);
    assert!(store.get("platform-transfer").unwrap().is_none());
}
