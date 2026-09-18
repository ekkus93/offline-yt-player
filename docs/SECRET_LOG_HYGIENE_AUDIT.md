# OYP-1802 Secret and Log Hygiene Audit

OYP-1802 requires diagnostics to avoid exposing sensitive request material.

`DiagnosticHygienePolicy` establishes an allow-by-name export boundary by excluding known sensitive request fields before diagnostic persistence, display, or sharing. The policy excludes authorization, cookie, credential, signature, and token material and preserves ordinary operational fields.

Unit tests prove exclusion and case-insensitive matching. Signed request locations should be omitted from diagnostic exports rather than emitted verbatim.
