# RMD-1802 — Android playback library-root symlink containment (2026-10-13)

## Independent production finding

`MainActivity.kt::safeLocalPlaybackPath` rejected absolute paths, traversal, and symlink components **below** a canonical library root, but called `libraryRoot.canonicalFile` without checking whether the supplied root itself was a symbolic link. Canonicalization would follow that link and treat its external destination as the authorized root. Thus, a direct symlink used as the Library root could allow a playable asset to resolve outside the intended storage boundary.

## Correction

Before canonicalizing and using the Library root, reject a direct symlink and any non-directory root. Retain the existing component-wise symlink refusal, traversal rejection, and canonical subpath containment. This defense is specific to the direct library-root path and does not indiscriminately reject system-managed symlink ancestors of Android app-private directories.

## Behavioral host JVM evidence

`LibraryIdentityMappingTest.symlinkedLibraryRootCannotResolveExternalPlaybackAssets` constructs a linked root pointing to a separate external temp directory, attempts a real CoreGatewayResult-to-LibraryScreenState mapping through `safeLocalPlaybackPath`, and requires a failed state and preservation of external file bytes. `regularFileCannotServeAsPlaybackLibraryRoot` rejects a non-directory root. Existing tests cover valid nested paths and owned-file/parent symlink rejection.

This implementation and host regression must pass exact-head CI and all required Android smoke/FGS/cold-start/network/full gates before being regarded as qualified; RMD-1802 independent review, final RMD-1803 freeze/closeout, RMD-1805 engineering definition of done, and the distinct external service/legal release gate remain open.

## Missing-media and directory-as-file follow-up (2026-10-13; exact-head CI pending)

Independent review of `core/src/ffi_library_playback.rs::playback_asset` found that its `playable=true` decision uses completed persisted metadata and syntactically valid relative paths, without verifying that the underlying file still exists. Android `MainActivity.safeLocalPlaybackPath` rejected traversals and symlinks but likewise returned contained missing paths and directories, so stale metadata after external file deletion could render a Play action for nonexistent or non-file media. The Android mapper now requires the final asset to be an existing regular file using `Files.isRegularFile(..., NOFOLLOW_LINKS)` before exposing it as playable. `LibraryIdentityMappingTest.missingPersistedPlaybackFileFailsClosed` and `directoryCannotBePresentedAsPlayableMedia` exercise actual mapper fail-closed behavior; previously path-only positive tests now create real files. This is implementation and executable host-JVM evidence, not exact-head qualification. The all-eight Android/CI matrix, independent review and RMD-1803/1805/global closeout remain open, as does external service/legal approval.
