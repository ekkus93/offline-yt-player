# RMD-1802 — Android golden evidence retrieval and Ralph Bridge diagnostic (2026-10-15)

**Status:** Diagnostic/source correction only; do not treat as screenshot approval, final independent review, or exact-head qualification. External YouTube/service-policy/legal release approval remains separate.

## Observed repository and Bridge identity

- Repository: `ekkus93/offline-yt-player`; source `master` at `17706971f9b2aebcaf98b2cc66f796c2852e3f95` before this correction.
- Ralph Bridge runtime: semantic version `0.6.2`; build `0acea4b23362e2ccecab8f6c9db4418b162426cf`; server instance `51c945dd809b4207800c3ba33151ad94`; started `2026-10-15T02:25:51.132200Z`; GitHub App authentication. These values identify the process but do not prove a root cause for earlier safety-policy rejections.
- Branch inventory: five bounded pages, 479 branch records; 44 branches reported open-PR metadata. No branch or PR created for this correction.
- The prior direct-to-master write fixing tag-only artifact governance succeeded as commit `17706971f9b2aebcaf98b2cc66f796c2852e3f95`. The preceding session reported write-policy rejection; this run did not reproduce it. Do not conflate CI failures with Bridge write failures.

## Exact-head CI on 17706971

The following seven workflows completed successfully on the same exact head:

| Workflow | Run ID |
| --- | --- |
| CI | 38723170147 |
| Android FGS timeout | 38723170035 |
| Supply chain | 38723170044 |
| CI evidence | 38723170060 |
| Deterministic E2E fixture | 38723170051 |
| Android cold start | 38723170110 |
| Android real network E2E | 38723170013 |

Android smoke `38723170040`, attempt 1, failed at `2026-10-15T11:29:33Z`, job `116382486151`, step 8 `Run device-side smoke`. The failure was **not** the two prior Settings raster hashes: the observed failure was `ProductionComposeGoldenTest.golden_add_resolved`, with Compose UI lifecycle exception:

```text
2026-10-15T11:26:26.5150098Z ANDROID_TEST_FAILURE com.ekkus.offlineytplayer.ui.ProductionComposeGoldenTest.golden_add_resolved
2026-10-15T11:26:26.5160881Z failure: java.lang.IllegalStateException: Must run runDetachLifecycle() once after runAttachLifecycle() and before markAsDetached()
2026-10-15T11:26:26.4940046Z > Task :app:connectedDebugAndroidTest FAILED
```

This is a genuine Android instrumentation failure, not evidence of a Ralph Bridge API failure. The Compose stack includes `Modifier$Node.runDetachLifecycle`, `NodeChain.runDetachLifecycle`, and `LayoutNode.detach`; cause is not yet established. A rerun was accepted using Ralph Bridge `rerun_failed_job` for job `116382486151`, operation ID `d50c905a7eb14a44a583c237a25a5793`, requested `2026-10-15T11:30:59.542376Z`. Observe the newer run attempt separately; acceptance is not a pass.

The previous Android smoke run `38697664453` failed two raster hashes (not the same exception):

- `settings_hub`: actual `67419aab65dd201a5a81a9740caa4c4f1b91be650d096b09e79bbefb511f2420`, expected `907224d90bc0a87418bed06661427bc4dfc6aa9871a64348fc03ba238fac0052`.
- `large_font_settings`: actual `ff51e69e5f07c165ede55813fb04a479dcb00e184638a0f236afe27ec0bb2743`, expected `4c6185c13f3576d592a4dac8934b750c360cfa9c6e0e1b4181cac96423115611`.

Prior diagnostic artifact `11956871220` belongs to run `38697664453`; its artifact metadata reports 55,330 bytes, expires `2026-10-18T07:16:38Z`. Metadata alone does **not** prove the PNGs are present or visually correct. Never update pinned hashes without screenshot review.

## Evidence retrieval defect and correction

Both failing runs logged:

```text
Golden raster SHA-256 manifest:
cat: app/build/reports/androidGoldens/golden-raster-sha256.txt: No such file or directory
```

The original workflow only tried `adb pull /sdcard/Download/offline-yt-player-goldens/.` **if** pulling the app-scoped directory failed. If the first pull succeeds but does not contain the manifest, the fallback is skipped. The app-scoped directory may also disappear during instrumentation teardown; the exact failure mechanism is not proven without the `golden-pull.log`.

The workflow now attempts **both** app-scoped and stable `/sdcard/Download` evidence locations independently, retains both results in `golden-pull.log`, and prints that log to the CI job console. This is diagnostic hardening, not a raster-baseline change. Tag-only artifact upload remains enforced (`failure() && github.ref_type == 'tag'`) to preserve repository governance. Untagged failed runs still have job-console diagnostics, but no automatic artifact.

## Qualification and follow-up

1. Inspect the newer Android smoke rerun attempt and distinguish Compose lifecycle failures from screenshot-hash failures.
2. Inspect the new golden-pull log to determine whether the shell export path exists, and whether PNGs and the manifest are retrievable.
3. Visually inspect the prior PNG evidence before considering any baseline updates; never accept observed hashes merely because tests produced them.
4. Require a fresh eight-workflow exact-head pass for the correction and complete independent Rust/Android review, canonical TODO reconciliation, final closeout and external release approval as separate gates.
