# RMD-1802 — Download engine initialization and durable claim ordering

Independent review of `core/src/worker.rs::DownloadWorker::execute_ready_at` found that `claim_eligible` persists `Resolving` state and increments attempts before `DownloadEngine::new` initializes the HTTP client. If initialization fails, the call returns an error with durable jobs left in `Resolving` until recovery.

**Status: open, production correction not merged.** An attempted correction introduced an injectable engine factory and a deterministic regression test, but CI found a formatting issue, and the subsequent source rewrite accidentally truncated the test module. The complete prior worker source was restored from `f8b39d1b225c3695d866787117f886c9545748b8` at `b377fa2a01569bfe572743b94fa1d945f5b5cb56`. A subsequent corrected source write was rejected by tool safety checks. No production fix or passing qualification is claimed.

Required correction: initialize the client before claiming durable work; add a deterministic initialization-failure regression that verifies QUEUED state, unchanged attempt count, and no staged assets. Keep RMD-1802/RMD-1803 and final closeout unchecked until the fix is committed and qualified on an exact master SHA.
