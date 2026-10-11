# RMD-1802 — Download engine initialization and durable claim ordering

Independent review of `core/src/worker.rs::DownloadWorker::execute_ready_at` found that `claim_eligible` persisted `Resolving` state and incremented attempts before `DownloadEngine::new` initialized the HTTP client. If client initialization failed, the call returned an error with durable jobs stranded in `Resolving` until a later recovery pass.

The correction constructs the engine before claiming work. `engine_setup_failure_does_not_claim_durable_work` injects a deterministic initialization failure and checks that the queued state, attempt count, and staging records remain unchanged. Keep RMD-1802/RMD-1803 closeout unchecked until exact-head qualification and the remaining independent review finish.
