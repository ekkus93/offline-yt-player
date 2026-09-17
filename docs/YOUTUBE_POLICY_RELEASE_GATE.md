# YouTube source policy/legal release gate

This document defines the OYP-706 release gate. It is an engineering release-control document, not legal advice and not a conclusion that any particular use is permitted by YouTube, Google Play, copyright law, or another applicable rule.

## Supported-use statement

Offline YT Player is intended to operate only on media that the user is authorized to access and download. A public build must not represent the application as granting permission to copy, retain, redistribute, or circumvent restrictions on third-party media. Users remain responsible for having the rights or permission required for their use.

The application must not add features whose purpose is to bypass authentication, access controls, DRM, paywalls, geographic restrictions, or other technical restrictions. Credentials, cookies, bearer tokens, signed media URLs, and equivalent secrets must not be exposed through UI, logs, diagnostics, analytics, or support bundles.

Provider support remains replaceable behind `MediaSource`; a provider adapter is not a promise that every URL accepted by a provider's website is appropriate or permitted for offline acquisition.

## Service-terms considerations

YouTube is a third-party service with terms and technical behavior outside this project's control. Before any public release that enables the YouTube adapter, the release owner must review the then-current applicable YouTube/Google terms and policies and document whether the proposed extraction/download behavior is acceptable for the intended distribution and use cases.

That review must explicitly consider at least:

- whether programmatic extraction or downloading is permitted for the intended media/use case;
- whether the implementation uses an authorized API where one is required;
- whether playback/download behavior interferes with provider restrictions, advertising, attribution, or other service requirements;
- whether provider trademarks/naming and user-facing claims are appropriate; and
- whether the release needs to disable, narrow, or remove the YouTube adapter.

Because terms and policies can change independently of source code, an old review is not sufficient evidence for a materially later release.

## App-store considerations

Before a Google Play or other app-store submission, the release owner must separately review the store rules in force at submission time. The review must cover third-party content access/downloading, intellectual-property representations, privacy/data-safety declarations, foreground-service/notification behavior where applicable, and any policy specific to media downloading or external services.

Passing CI, device tests, or provider fixture tests does not satisfy this store-policy review.

## Release-blocking rule

**Public/app-store release with the YouTube adapter enabled is blocked until a human release owner records a dated policy/legal review for the intended release.**

The review record must identify the release/commit being reviewed, the relevant service/store policy versions or review date, the supported-use statement presented to users, and the disposition: approved as implemented, approved with restrictions, or not approved. If restrictions are required, they must be implemented and qualified before the gate is cleared.

Until that record exists:

- CI may build and test the adapter with deterministic fixtures;
- developers may continue implementation and private engineering qualification;
- documentation must continue to describe the repository as not yet a public-release build; and
- no TODO/release checklist may mark the human policy/legal review itself complete merely because this engineering gate document exists.

## Gate status

The engineering requirements of OYP-706 are now defined: service/app-store considerations are documented, the supported-use statement is defined, and public/app-store release is explicitly blocked pending review. The **human review remains intentionally open** and must be performed against the policies current at the actual release date.
