# YouTube source and public-release policy gate

Status: **public/app-store distribution blocked pending explicit review**.

Offline YT Player's YouTube adapter is an engineering capability, not permission to download arbitrary material. The project must not represent extraction success as authorization to copy, retain, redistribute, or bypass access controls.

## Supported-use statement

The application is intended only for media the user is legally and contractually permitted to download and store, such as the user's own material, material whose owner has granted download rights, or material otherwise permitted by applicable law and service terms. The application must not advertise circumvention of paywalls, DRM, authentication, geographic restrictions, or other access controls.

## Service and store considerations

Before any public binary, app-store submission, or broadly promoted release, a human release review must examine the then-current YouTube Terms of Service and applicable developer/API terms, the extraction mechanism actually shipped, and the distribution channel's policies. Store policy and service terms can change independently of the code, so this review cannot be permanently satisfied by a historical snapshot.

The review must specifically determine whether the shipped behavior and product messaging are compatible with restrictions on downloading, automated access, circumvention, and use of YouTube content. It must also review licenses and redistribution obligations for every extractor or muxer dependency included in the distributed artifact.

## Engineering constraints

- YouTube remains behind the replaceable `MediaSource` boundary.
- No credentials, cookies, signed media URLs, authorization headers, or extractor secrets may appear in diagnostics.
- The initial implementation must not add authentication/cookie import or access-control bypass as an incidental feature.
- External extractor or muxer dependencies require an explicit license and packaging review before inclusion.
- Provider breakage is an extractor/source-change failure, not a reason to weaken input validation or secret handling.

## Release gate

Public/app-store distribution remains **BLOCKED** until a named human reviewer records all of the following for the candidate release:

1. review date and candidate commit SHA;
2. YouTube/service terms and distribution-channel policy state reviewed at that date;
3. shipped extraction approach and dependency/license inventory;
4. confirmation that supported-use messaging is present in user-facing documentation/About UI;
5. an explicit `APPROVED` or `REJECTED` decision with rationale.

Development, CI fixtures, and private engineering qualification may continue while this gate is blocked. A passing CI run never overrides this policy gate.

## Review record

No public-release approval has been recorded yet. Until one is added here (or in a release-specific document linked here), release automation and documentation must continue to describe public distribution as blocked.
