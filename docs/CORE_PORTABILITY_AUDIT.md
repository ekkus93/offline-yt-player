# Offline YT Player — Core Portability Audit

This audit qualifies OYP-2201 against the v1 Rust core and its public UniFFI boundary.

## Android type isolation

The portable `core` crate owns domain models, source selection, downloads, persistence, recovery, and the coarse UniFFI records. Android `Context`, `Uri`, `Intent`, `Service`, Compose, Media3, and other platform presentation/lifecycle types remain in the Android application. The crate root explicitly documents that Android presentation, lifecycle, notifications, and playback stay outside the core.

Portable persisted records use filesystem paths/relative asset identities rather than Android content-URI identities. The companion `PORTABLE_LIBRARY_FORMAT.md` defines the library-root and relative-asset contract.

## Filesystem and network assumptions

The v1 core deliberately uses portable Rust filesystem and HTTP abstractions. Platform code supplies/owns the library root; the database and asset records do not encode an Android storage URI. Network/source behavior is expressed through the generic `MediaSource` and download-plan contracts rather than Android connectivity classes. Android-specific network preferences, foreground-service behavior, permissions, and notifications remain platform responsibilities.

Future platforms may choose their own application-support/library root and platform network-policy integration while reusing the portable database, source, transfer, and recovery semantics.

## FFI suitability

The UniFFI boundary exports records/enums composed of strings, integers, booleans, options, and stable error categories. It does not expose Kotlin/Android classes or provider-specific response objects. Long-running operations are designed around coarse operations and durable job identifiers; blocking work is required to run off the caller's UI thread.

Those types are intentionally suitable for generated Swift or future desktop bindings. A future platform binding may add language-specific convenience wrappers, but must not change the portable domain semantics.

## Qualification conclusion

OYP-2201 is satisfied for the v1 architecture:

- no Android-specific types are part of the portable Rust domain or public FFI records;
- platform filesystem/network policy is separated from portable library/source/download semantics; and
- the coarse UniFFI data boundary is suitable for future Swift/desktop bindings.

This is a portability-readiness claim, not a claim that iOS or desktop UI/build targets currently exist.
