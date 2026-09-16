# Policy and Legal Release Gate

This document implements the engineering gate in OYP-706. It is not legal advice and does not declare that downloading any particular content is lawful or permitted by a service.

## Release state

**Public distribution and app-store release are BLOCKED pending explicit policy/legal review.** Development builds and deterministic fixture testing may continue. Removing this block requires a reviewed decision recorded in the repository; technical completion of the YouTube adapter is not sufficient.

## Service-terms and platform considerations

Before public distribution, the reviewer must assess the then-current YouTube/service terms applicable to access, downloading, API or non-API extraction, circumvention restrictions, account/authentication behavior, and permitted user actions. The review must also cover the then-current distribution rules for each intended app store or channel, including policies concerning media downloading, intellectual-property infringement, executable-code updates, privacy disclosures, and user-generated URLs.

Because service and store policies can change independently of this repository, this project must not encode a permanent claim that a historical review remains valid. The release record must identify the review date, intended distribution channel/jurisdiction where relevant, and any restrictions that the product must enforce or disclose.

## Supported-use statement

The intended supported use is offline playback of media that the user is authorized to download and retain. The application must not represent itself as granting rights to content, bypassing access controls, or authorizing conduct prohibited by applicable law, content licenses, service terms, or distribution-channel rules. Users remain responsible for ensuring they have the necessary rights or permission for the media they request.

The product must not require account-cookie/token collection as part of the v1 supported flow. Diagnostics must not expose signed media URLs, cookies, authorization data, or extractor tokens. Any future authenticated-source feature requires a separate security/privacy/policy review before implementation or release.

## Engineering constraints tied to this gate

- YouTube/provider logic remains behind `MediaSource`; generic download and playback code must not silently broaden provider behavior.
- Extractor updates ship through the reviewed application release path; v1 does not download executable extractor code at runtime.
- No extractor or muxer dependency may be added without license and supply-chain review.
- Failures caused by provider changes must fail closed and surface a sanitized `SourceChanged`-style diagnostic rather than attempting unknown workarounds.
- User-facing documentation must preserve the authorized-content limitation and must not promise compatibility with every YouTube URL or protected/restricted content.

## Required release-review record

A future release candidate may unblock public distribution only when the repository records all of the following:

1. reviewer/decision owner and review date;
2. distribution channels covered by the review;
3. current service-terms assessment for the implemented extraction path;
4. copyright/authorization and anti-circumvention considerations applicable to supported use;
5. app-store policy assessment where an app-store build is intended;
6. privacy/security assessment of provider requests and diagnostics;
7. dependency/license inventory confirmation;
8. final supported-use and user-facing legal/source-service notice;
9. explicit disposition: approved, approved with restrictions, or blocked.

Until that record exists with an approved disposition, CI/release documentation and OYP-2305 must continue to describe public distribution as blocked.
