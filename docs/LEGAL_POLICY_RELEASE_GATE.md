# Source-service legal and policy release gate

Status: **public distribution blocked pending explicit review**

Offline YT Player is an engineering project for saving media that a user is authorized to access and retain. The project does not treat technical ability to resolve or download a URL as evidence that downloading is permitted.

## Supported-use statement

The application is intended only for media that the user owns, has permission to download, or is otherwise legally entitled to retain for offline use. Users are responsible for complying with copyright law, contractual restrictions, source-service terms, and applicable local law. The project must not present itself as a mechanism for bypassing access controls, DRM, paywalls, authentication, geographic restrictions, or other technical protection measures.

## YouTube and source-service review

Before any public/app-store release that enables the YouTube adapter, a human release review must evaluate the then-current YouTube Terms of Service, applicable API/developer policies if any official API is used, copyright implications, and the distribution channel's policies. The review must use the terms and policies current at release time rather than relying on this repository snapshot.

The source adapter is deliberately replaceable. If a source's terms, technical controls, or distribution-channel rules make the adapter unsuitable for public distribution, that adapter can be disabled or excluded without changing the generic library/download architecture.

## App-store considerations

A release review must explicitly check at least:

- whether the store permits the advertised download/offline-media behavior;
- whether the application description and screenshots accurately state supported uses;
- whether source-service branding, trademarks, or metadata create additional requirements;
- whether any extractor or muxer dependency adds licensing or redistribution obligations;
- whether privacy disclosures cover URLs, metadata, diagnostics, and locally stored media.

## Technical policy constraints

The v1 implementation must not add DRM circumvention, credential theft, cookie harvesting, token exfiltration, or bypass logic for access restrictions. Diagnostics must redact cookies, authorization headers, tokens, and signed media URLs. Authentication support, if ever added, requires a separate security and policy review.

## Release gate

Public distribution remains blocked until a designated human reviewer records all of the following in a release-specific review artifact:

1. review date and candidate commit SHA;
2. source-service terms/policy documents reviewed and their effective dates;
3. distribution-channel policy documents reviewed;
4. extractor/muxer dependency license review;
5. approved supported-use wording;
6. an explicit `APPROVED`, `APPROVED_WITH_RESTRICTIONS`, or `REJECTED` decision.

Absence of that artifact is a hard release stop. CI/release automation must not interpret green engineering tests as legal or policy approval.
