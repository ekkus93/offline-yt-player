# OYP-1604 Storage Settings Audit

The storage settings policy exposes a compact storage-location summary, used/free byte counts, cache usage, and separate orphan/incomplete byte counts.

Cleanup is explicit rather than implicit: clear cache, remove orphaned assets, and remove incomplete transfers are separate bounded actions. Reclaimable space is computed from only those cleanup categories; ordinary library media is not included.

`StorageSettingsPolicyTest` qualifies the location/capacity surface and the complete cleanup-action set.
