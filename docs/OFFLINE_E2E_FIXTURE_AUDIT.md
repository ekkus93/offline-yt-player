# OYP-1901 deterministic E2E fixture audit

OYP-1901 is represented by a deterministic fixture contract that is independent of public extractor/source availability.

The required sequence is fixed and tested: resolve fixture, select quality, start download, kill during transfer, restart, resume, complete and verify, disable networking, and play the completed local asset.

The fixture uses a `fixture://` identity and explicitly does not require a public network source. This keeps CI qualification reproducible while preserving the same lifecycle boundaries exercised by the production flow. A missing restart, offline transition, or local-playback step fails the qualification contract.

Environment-capable CI may execute the same contract end-to-end; environments without an Android process/network harness retain the deterministic contract test as the minimum gate.
