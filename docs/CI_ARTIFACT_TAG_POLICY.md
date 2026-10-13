# GitHub Actions artifact policy

Actions **workflow artifacts** are uploaded only for tagged builds. Branch and
pull-request CI still executes normally and retains GitHub's standard job logs,
but no routine CI job invokes an artifact upload.

- Every `actions/upload-artifact` step checks `github.ref_type == 'tag'`.
  Even failure-evidence uploads are skipped for branch/PR runs.
- Workflows that upload artifacts also accept pushed Git tags via
  `push.tags: ['**']`; regular branch/PR triggers remain unchanged.
- The deterministic Rust fixture workflow always writes its test summary to
  the job log and step summary. On tagged runs it may upload the compact summary.
  Uploading its large compiled dependencies requires an explicit
  `workflow_dispatch` using a **tag ref** with
  `upload_full_fixture_evidence=true`.
- `tests/test_actions_artifact_tag_policy.py`, invoked by CI governance,
  scans all workflow YAML files to prevent new unguarded upload actions.

GitHub Actions job logs, dependency caches (`actions/cache`), and GitHub
Release assets are distinct from workflow artifacts and are not deleted or
disabled by this policy. Existing artifacts must expire or be removed
separately; this policy only prevents future non-tag uploads.
