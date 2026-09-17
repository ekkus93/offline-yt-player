# Portrait Player Screen Audit

## Scope

This note covers OYP-902: the portrait-only player screen structure and deterministic layout policy.

It does not claim OYP-903 lifecycle persistence, OYP-904 MediaSession integration, OYP-905 device playback E2E, or OYP-1900 full offline acceptance.

## Implementation

`PlayerScreen` defines a portrait player surface with:

- a fixed full-width 16:9 video region;
- bounded title and timeline text regions;
- visible skip-back, play/pause, and skip-forward transport controls;
- visible speed, subtitles, and audio secondary controls;
- no rotate/fullscreen-landscape action; and
- no scroll container around primary playback controls.

The screen uses the Midnight Transit spacing and minimum-touch-target tokens from the existing Android shell/theme layer.

## Qualification

`PlayerScreenLayoutPolicyTest` verifies the deterministic parts of the screen contract:

- fixed 16:9 video aspect ratio;
- explicit transport and secondary control labels/counts;
- absence of a landscape action;
- no scroll-dependent primary controls; and
- compact portrait budget coverage at normal and large font scale.

Exact-head CI must pass before this milestone is reconciled in the canonical TODO.
