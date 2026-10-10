# RMD-1802 — Nullable UniFFI error-property shape audit (2026-10-10)

Independent production mapping review identified two fail-open binding-drift paths. `DownloadPresentationGateway.titlesByJobId` treated an absent generated `error` member as a successful null error. `GeneratedUniffiLibraryMutationGateway.renameDisplayTitle` and `removeLibraryItem` likewise treated an absent `errorMessage` member as a successful null error. A missing generated member must not be equivalent to an explicitly nullable, present error member.

The change adds required-presence readers that allow explicit null, reject absent members, and reject wrong-typed mutation errors. JVM mapping tests cover present-null, present-error, absent, and wrong-typed shapes. Existing generated-service Android instrumentation remains the runtime compatibility gate. This finding does **not** close RMD-1802 or RMD-1803: require full exact-head CI, packaged Android smoke, and independent review before final reconciliation.
