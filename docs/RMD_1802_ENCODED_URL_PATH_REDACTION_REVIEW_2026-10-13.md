# RMD-1802 encoded URL path review

`core/src/security.rs` now rejects diagnostic URLs whose paths embed encoded query or fragment delimiters. Commit: `e40ccb09119ae786692aff975bf3c3ea8a02e128`. Regression: `encoded_query_and_fragment_delimiters_in_url_paths_fail_closed`. Full exact-head CI and independent review remain open.
