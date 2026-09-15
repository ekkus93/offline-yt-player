# ADR 0005: Portrait-only fixed-region Android UI

Status: accepted for v1

## Decision

Offline YT Player supports Android portrait orientation only for v1. The main Activity is locked to portrait and no landscape-specific resource or alternate navigation model is maintained.

Primary screens use fixed regions: a fixed top application/control region, a bounded content region, fixed primary actions when present, and the fixed four-destination bottom navigation (`Library`, `Downloads`, `Add`, `Settings`). Primary actions must be discoverable and reachable without scrolling. Only naturally unbounded collections or long detail content may scroll.

If a set of controls cannot fit the supported compact portrait profile, it must be split into a focused subpage/tab rather than placed below the fold. There is no horizontal control carousel, navigation drawer, rotate/fullscreen-landscape action, or gesture-only important action.

## Accessibility and insets

Interactive targets are at least 48 dp. System safe-drawing insets are applied by the scaffold. Text scaling is part of compact-profile qualification; large text must not make primary actions inaccessible. Controls require semantic/TalkBack labels and state may not be communicated by color alone.

## Rationale

A single orientation reduces layout permutations and makes the product's fixed-control rule testable. It also matches the intended phone-first offline listening/viewing workflow while avoiding hidden controls that require exploratory scrolling.

## Consequences

- New screens must use/reuse fixed-region layout primitives.
- UI tests/goldens target compact and large portrait profiles, including text scaling.
- Landscape resources/features are a regression unless this ADR is explicitly superseded.
- Playback remains portrait; a fullscreen-landscape affordance is intentionally absent.
