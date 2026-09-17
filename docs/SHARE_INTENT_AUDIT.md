# Android Share Intent Audit

## Scope

OYP-1101 qualifies the Android text-share entry point and its untrusted-input boundary.

## Implementation

- `MainActivity` is registered for `ACTION_SEND` with the default category and `text/plain` MIME type.
- Shared text is read only from `Intent.EXTRA_TEXT` and passed through `ShareInput.parse` before reaching UI state.
- The parser rejects wrong actions, wrong MIME types, empty or oversized text, malformed URLs, non-HTTP(S) schemes, and URLs without a host.
- Accepted URLs are normalized through `java.net.URI` and returned as ASCII strings.

## Qualification

`ShareInputTest` covers valid, malformed, wrong-type, and oversized inputs. `ShareIntentPolicyTest` locks the manifest registration and the activity parsing boundary so untrusted shared text cannot bypass validation.
