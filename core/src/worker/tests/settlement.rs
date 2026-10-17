use super::*;

#[test]
fn stale_error_and_cancel_handlers_preserve_user_pause_and_cancel() {
    for state in [DownloadState::Paused, DownloadState::Canceled] {
        let store = LibraryStore::open_in_memory().unwrap();
        let root = tempfile::tempdir().unwrap();
        let worker = DownloadWorker::new(store.clone(), root.path(), DownloadPolicy::default(), 1);
        let mut claim = snapshot("stale-stop", DownloadState::Downloading);
        claim.attempt = 2;
        store.save_download_snapshot(&claim).unwrap();
        let mut updated = claim.clone();
        updated.state = state;
        updated.bytes_downloaded = 81;
        store.save_download_snapshot(&updated).unwrap();
        assert!(
            !worker
                .finish_failed_or_retry(
                    &mut claim,
                    CoreError::new(ErrorKind::NetworkTimeout, "old failure", true),
                    true,
                    42,
                )
                .unwrap()
        );
        assert!(!worker.finish_canceled(&mut claim).unwrap());
        assert_eq!(store.load_download_snapshots().unwrap(), vec![updated]);
        assert!(worker.durable_stop_for_claim(&claim).unwrap().is_some());
    }
}

#[test]
fn reclaimed_attempt_cannot_receive_stale_settlement_or_stop_report() {
    let store = LibraryStore::open_in_memory().unwrap();
    let root = tempfile::tempdir().unwrap();
    let worker = DownloadWorker::new(store.clone(), root.path(), DownloadPolicy::default(), 1);
    let mut claim = snapshot("reclaimed", DownloadState::Downloading);
    claim.attempt = 1;
    store.save_download_snapshot(&claim).unwrap();
    let mut newer = claim.clone();
    newer.attempt = 2;
    newer.bytes_downloaded = 123;
    store.save_download_snapshot(&newer).unwrap();
    assert!(
        !worker
            .finish_failed_or_retry(
                &mut claim,
                CoreError::new(ErrorKind::NetworkUnavailable, "old", false),
                false,
                30,
            )
            .unwrap()
    );
    assert!(!worker.finish_canceled(&mut claim).unwrap());
    assert!(worker.durable_stop_for_claim(&claim).unwrap().is_none());
    assert_eq!(
        store.load_download_snapshots().unwrap(),
        vec![newer.clone()]
    );
    newer.state = DownloadState::Paused;
    store.save_download_snapshot(&newer).unwrap();
    assert!(worker.durable_stop_for_claim(&claim).unwrap().is_none());
}

#[test]
fn settling_failure_or_cancel_keeps_latest_progress_and_total_bytes() {
    for outcome in [
        DownloadState::Failed,
        DownloadState::RetryWait,
        DownloadState::Canceled,
    ] {
        let store = LibraryStore::open_in_memory().unwrap();
        let root = tempfile::tempdir().unwrap();
        let worker = DownloadWorker::new(store.clone(), root.path(), DownloadPolicy::default(), 1);
        let mut claim = snapshot("progress", DownloadState::Downloading);
        claim.attempt = 3;
        claim.bytes_downloaded = 5;
        store.save_download_snapshot(&claim).unwrap();
        let mut persisted = claim.clone();
        persisted.bytes_downloaded = 49;
        persisted.total_bytes = Some(128);
        store.save_download_snapshot(&persisted).unwrap();
        let success = if outcome == DownloadState::Canceled {
            worker.finish_canceled(&mut claim).unwrap()
        } else {
            worker
                .finish_failed_or_retry(
                    &mut claim,
                    CoreError::new(ErrorKind::NetworkTimeout, "fixture", true),
                    outcome == DownloadState::RetryWait,
                    20_000,
                )
                .unwrap()
        };
        assert!(success);
        let durable = store.load_download_snapshots().unwrap().remove(0);
        assert_eq!(durable.state, outcome);
        assert_eq!(durable.bytes_downloaded, 49);
        assert_eq!(durable.total_bytes, Some(128));
        assert_eq!(durable.attempt, 3);
        assert_eq!(durable, claim);
    }
}

#[test]
fn engine_initialization_failure_does_not_claim_durable_work() {
    let store = LibraryStore::open_in_memory().unwrap();
    store
        .save_download_snapshot(&snapshot("init-failure", DownloadState::Queued))
        .unwrap();
    let root = tempfile::tempdir().unwrap();
    let worker = DownloadWorker::new(store.clone(), root.path(), DownloadPolicy::default(), 1);
    let work = plan("init-failure", "http://127.0.0.1/unused".into(), 16);

    // Inject the fallible engine-construction outcome without depending on
    // process-global proxy variables or ambient network configuration.
    let failure = CoreError::new(
        ErrorKind::NetworkUnavailable,
        "synthetic HTTP client initialization failure",
        true,
    );
    let error = worker
        .execute_ready_with_engine_at(&[work], &AtomicBool::new(false), 10_000, Err(failure))
        .unwrap_err();

    assert_eq!(error.kind, ErrorKind::NetworkUnavailable);
    let durable = store.load_download_snapshots().unwrap().remove(0);
    assert_eq!(durable.state, DownloadState::Queued);
    assert_eq!(durable.attempt, 0);
    assert_eq!(durable.retry_at_epoch_ms, None);
    assert!(durable.last_error.is_none());
    assert!(store.get("init-failure").unwrap().is_none());
}

#[test]
fn pause_after_claim_prevents_stale_worker_from_starting_transfer() {
    let store = LibraryStore::open_in_memory().unwrap();
    let mut claimed = snapshot("pause-after-claim", DownloadState::Resolving);
    claimed.attempt = 1;
    store.save_download_snapshot(&claimed).unwrap();
    let root = tempfile::tempdir().unwrap();
    let worker = DownloadWorker::new(store.clone(), root.path(), DownloadPolicy::default(), 1);
    let mut paused = claimed.clone();
    paused.state = DownloadState::Paused;
    store.save_download_snapshot(&paused).unwrap();
    let engine = DownloadEngine::new(root.path(), DownloadPolicy::default()).unwrap();
    let item = plan(
        "pause-after-claim",
        "http://127.0.0.1:1/no-transfer".into(),
        64,
    );
    let error = worker
        .execute_one(
            &engine,
            &item,
            &mut claimed,
            &AtomicBool::new(false),
            &AtomicBool::new(false),
            10_000,
        )
        .unwrap_err();
    assert_eq!(error.kind, ErrorKind::Canceled);
    assert_eq!(store.load_download_snapshots().unwrap(), vec![paused]);
    assert!(store.staged_job_ids().unwrap().is_empty());
    assert!(store.get("pause-after-claim").unwrap().is_none());
    assert!(
        !root
            .path()
            .join("items/pause-after-claim/video.mp4")
            .exists()
    );
}

#[test]
fn empty_plan_fails_without_promoting_an_unplayable_library_item() {
    let store = LibraryStore::open_in_memory().unwrap();
    store
        .save_download_snapshot(&snapshot("empty-plan", DownloadState::Queued))
        .unwrap();
    let root = tempfile::tempdir().unwrap();
    let worker = DownloadWorker::new(store.clone(), root.path(), DownloadPolicy::default(), 1);
    let mut work = plan("empty-plan", "http://127.0.0.1/unused".into(), 0);
    work.plan.assets.clear();

    let report = worker
        .execute_ready_at(&[work], &AtomicBool::new(false), 10_000)
        .unwrap();
    assert_eq!(report.failed, vec!["empty-plan"]);
    assert!(report.completed.is_empty());
    assert!(store.get("empty-plan").unwrap().is_none());
    let snapshot = store.load_download_snapshots().unwrap().remove(0);
    assert_eq!(snapshot.state, DownloadState::Failed);
    assert_eq!(snapshot.bytes_downloaded, 0);
    assert_eq!(
        snapshot.last_error.unwrap().kind,
        ErrorKind::NoCompatibleFormat
    );
}

#[test]
fn thumbnail_only_plan_cannot_complete_as_playable_media() {
    let store = LibraryStore::open_in_memory().unwrap();
    store
        .save_download_snapshot(&snapshot("thumbnail-only", DownloadState::Queued))
        .unwrap();
    let root = tempfile::tempdir().unwrap();
    let worker = DownloadWorker::new(store.clone(), root.path(), DownloadPolicy::default(), 1);
    let mut work = plan("thumbnail-only", "http://127.0.0.1/unused".into(), 100);
    work.plan.assets[0].kind = MediaKind::Thumbnail;

    let report = worker
        .execute_ready_at(&[work], &AtomicBool::new(false), 10_000)
        .unwrap();
    assert_eq!(report.failed, vec!["thumbnail-only"]);
    assert!(store.get("thumbnail-only").unwrap().is_none());
    let snapshot = store.load_download_snapshots().unwrap().remove(0);
    assert_eq!(snapshot.state, DownloadState::Failed);
    assert_eq!(
        snapshot.last_error.unwrap().kind,
        ErrorKind::NoCompatibleFormat
    );
}
