# Portrait-only UI policy

Offline YT Player supports Android portrait windows only for v1.

- `MainActivity` is locked to `portrait` in the manifest.
- No `layout-land`, `values-land`, landscape navigation, rotate-to-landscape, or landscape-only feature may be introduced.
- Primary screens use a fixed top region and fixed bottom navigation. Primary actions remain in a fixed action region when present.
- Only naturally unbounded content such as library/download lists may scroll.
- If controls cannot fit on a supported portrait profile, move secondary controls to a focused subpage rather than requiring discovery scrolling.
- System safe-drawing insets are applied by the shared scaffold.
- The compact qualification baseline is 640 dp portrait height. Large-font policy checks use at least 1.30x font scale; later device/UI tests must add stronger accessibility-scale coverage.

The app's primary destinations are Library, Downloads, Add, and Settings, with Library as the launch destination. No navigation drawer, horizontally scrolling navigation, or gesture-only primary action is permitted.
