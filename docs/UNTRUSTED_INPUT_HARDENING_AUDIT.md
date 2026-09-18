# OYP-1801 Untrusted Input Hardening

The Android boundary now has a deterministic untrusted-input policy covering the four OYP-1801 requirements.

Source URLs are accepted only when they are HTTPS URLs with a host and no embedded credentials. Metadata is stripped of control characters and length-bounded. Relative paths reject absolute paths, empty segments, and parent traversal. Filenames are reduced to a bounded safe leaf name and cannot preserve directory traversal components.

JVM tests exercise valid input plus HTTP/file URLs, embedded credentials, control-character metadata, overlong metadata, absolute and parent-traversal paths, repeated path separators, and hostile filenames. These tests run in normal Android CI.
