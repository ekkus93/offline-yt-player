# Offline YT Player — Product and Engineering Specification

Status: Draft v1 foundation  
Repository: `ekkus93/offline-yt-player`  
Primary platform: Android  
Primary UI: Kotlin + Jetpack Compose  
Core: Rust  
Orientation: **portrait only**

## 1. Purpose

Offline YT Player is an Android-first offline media application for travelers and other users who expect intermittent or unavailable network access. The v1 product flow is intentionally narrow and reliable:

1. Add or share a supported video URL.
2. Resolve metadata and available downloadable formats.
3. Select quality and optional download settings.
4. Download the media with pause/resume/retry support.
5. Persist the item in an offline library.
6. Play the media fully offline.

The architecture must keep source-specific extraction logic isolated from the rest of the application so that the core library, downloader, database, and playback UX do not depend on one provider implementation.

## 2. Product principles

### 2.1 Offline first
Once an item is downloaded successfully, playback, metadata display, subtitles, thumbnails, queue browsing, and library management must work without network access.

### 2.2 Portrait only
The Android application supports **portrait orientation only**. Landscape layouts are explicitly out of scope. The manifest/activity configuration must lock the primary app experience to portrait, and the UI must be designed and tested solely for portrait phones and portrait tablet/foldable windows.

The player must therefore be designed around a full-width 16:9 video region within a portrait screen. No feature may depend on rotating the device.

### 2.3 Fixed functional regions
Primary UI controls must not be hidden off-screen and must not require the user to scroll merely to discover or reach an action.

The design rule is:

- fixed app bar where applicable;
- fixed bottom navigation on primary destinations;
- fixed primary action area on setup/detail screens;
- fixed playback controls on the player screen;
- bounded scrolling only for naturally unbounded collections such as the library, download history, search results, or long metadata/subtitle lists;
- no horizontally scrolling control bars, tab strips, carousels, or swipe-only action surfaces;
- when a screen cannot fit all controls comfortably, split it into tabs or subpages rather than pushing controls below the fold.

Absolute pixel positioning is not required; semantic regions must stay stable across supported portrait sizes, font scaling, and system insets.

### 2.4 Visible actions over hidden gestures
Gestures may be added as shortcuts but must never be the only way to perform an important action. Pause, resume, cancel, delete, quality selection, navigation, settings, subtitles, and playback controls must have visible UI affordances.

### 2.5 Calm visual hierarchy
The app should resemble a polished media library, not a clone of YouTube. Avoid visual clutter, excessive badges, gradients, oversized branding, or dense metadata walls.

## 3. Visual design system

Working palette name: **Midnight Transit**.

| Token | Hex | Purpose |
|---|---:|---|
| Background | `#0B0F17` | App background |
| Surface | `#141B25` | Cards and containers |
| Surface Elevated | `#1C2633` | Dialogs, selected/raised regions |
| Text Primary | `#F3F6FA` | Primary text |
| Text Secondary | `#A8B3C2` | Secondary metadata |
| Primary | `#7AA2FF` | Primary buttons, selection, focus |
| Accent | `#39D0C7` | Download/progress emphasis |
| Success | `#4FD1A1` | Completed/success state |
| Warning | `#F2B66D` | Recoverable warning |
| Error | `#FF6B7A` | Failure/destructive state |

Guidance:

- Default theme: dark.
- Light theme may be supported later but must use the same semantic token system.
- Primary actions use `Primary`.
- Active transfer/progress can use `Accent`.
- Destructive actions use `Error` only where necessary.
- Rounded corners: approximately 14–18 dp for prominent cards/containers.
- Touch targets: at least 48 dp.
- Avoid low-contrast gray-on-gray states.
- Use Material 3 typography and dynamic type compatibility, but do not use Material dynamic color as the default brand palette in v1.

## 4. Navigation model

The primary navigation is a fixed bottom navigation bar with four destinations:

1. **Library**
2. **Downloads**
3. **Add**
4. **Settings**

No navigation drawer is required. No primary function is placed exclusively in an overflow menu.

The app launches to Library.

## 5. Core user workflows

### 5.1 Add by paste

Library → Add → paste URL → Analyze → Download Setup → Download → Downloads → Library → Player.

### 5.2 Add through Android Share

External app → Android Share → Offline YT Player → Analyze/Download Setup → Download → Downloads.

If the incoming URL can be resolved immediately, the app should bypass the empty Add screen and open Download Setup directly after resolution.

### 5.3 Resume interrupted download

Downloads → failed/paused item → Resume → progress continues from retained state where supported.

### 5.4 Play offline

Library → video → Player. No network request is required for playback of a completed item.

### 5.5 Delete media

Library item overflow or Video Details → Remove from device → confirmation → remove associated media files and library record according to retention policy.

## 6. Screen specification

### 6.1 Library

Purpose: default home and offline collection browser.

Fixed regions:

- top app bar with app name, search, and optional filter/layout action;
- compact search/filter row if needed;
- fixed bottom navigation.

Scrollable region:

- library list/grid only.

Each item should display:

- 16:9 thumbnail;
- title, max two lines;
- duration;
- quality or media type;
- downloaded size or date as secondary metadata;
- visible overflow action.

Empty state:

- centered icon/illustration;
- “No offline videos yet”;
- “Add video” primary button.

### 6.2 Add Video

Purpose: accept a supported URL.

The screen should fit without scrolling.

Controls:

- title/app bar;
- URL text field;
- Paste button;
- Analyze button;
- brief supported-source text;
- bottom navigation.

Analyze remains disabled until input is plausibly valid.

### 6.3 Download Setup / Video Details

Purpose: show resolved metadata and select download quality.

The screen should fit without scrolling at standard font size. If advanced choices would overflow, move them to an Options subpage.

Visible content:

- compact 16:9 thumbnail;
- title, max two lines;
- duration and source metadata;
- estimated storage requirement where available;
- 3–4 curated quality choices, not a raw stream list;
- “Options” secondary action;
- fixed “Download” primary action.

Recommended quality choices:

- Best compatible;
- 1080p;
- 720p;
- 480p;
- audio-only only when explicitly enabled as a product feature.

### 6.4 Download Options

Purpose: contain advanced choices that would otherwise crowd Download Setup.

Candidate settings:

- subtitle language;
- audio track;
- audio-only mode if supported;
- preferred container/compatibility choice if user-facing;
- filename override only if justified.

This should be a focused subpage, not a bottom sheet.

### 6.5 Downloads

Purpose: show active, paused, failed, and completed transfers.

Fixed regions:

- app bar;
- status filter using a small fixed segmented control if it fits;
- bottom navigation.

Scrollable region:

- transfer list/history.

Active item:

- thumbnail;
- title;
- selected quality;
- progress bar;
- percentage and bytes/size;
- speed and ETA when reliable;
- visible Pause/Resume control;
- visible Cancel action or row menu.

Failed state must show a human-readable reason and Retry.

### 6.6 Player

Portrait only.

Fixed layout:

1. top app bar/back area;
2. full-width 16:9 video surface;
3. title, max two lines;
4. timeline and time labels;
5. main transport row;
6. secondary controls row.

Main transport row:

- skip back;
- play/pause;
- skip forward.

Secondary row:

- speed;
- subtitles;
- audio track when applicable;
- additional details/options.

There is no rotate-to-landscape or fullscreen-by-rotation requirement. If a fullscreen-style mode is ever added, it must remain portrait.

Playback must support:

- seek;
- pause/resume;
- resume position;
- selectable playback speed;
- subtitles when downloaded;
- audio track selection when present;
- MediaSession integration for lock-screen/Bluetooth controls.

### 6.7 Video Details / Item Actions

Purpose: expose item management without overloading Library cards.

Actions:

- Play;
- View metadata;
- Rename display title if supported;
- Remove from device;
- open/share local file only if platform policy and storage model permit.

### 6.8 Settings Hub

The Settings root should avoid becoming one long scrolling settings page.

Use fixed cards/rows that fit on one screen:

- Downloads
- Playback
- Storage
- Appearance
- About

Each opens a focused subpage. If a category grows beyond one screen, split it again rather than allowing important controls to disappear below the fold.

### 6.9 Settings — Downloads

Target controls:

- default quality;
- Wi-Fi-only downloads;
- maximum concurrent downloads;
- automatically download subtitles;
- default subtitle language;
- retry failed downloads.

### 6.10 Settings — Playback

Target controls:

- remember playback position;
- default playback speed;
- skip interval;
- autoplay next item, if queues/playlists become supported;
- default subtitle behavior;
- default audio track policy.

### 6.11 Settings — Storage

Target controls:

- current storage location;
- used/free storage summary;
- cache size;
- clear nonessential cache;
- manage downloaded files;
- delete orphaned/incomplete downloads;
- optional storage threshold warning.

### 6.12 Settings — Appearance

Target controls:

- theme: Dark / Light / System, with Dark as initial default;
- library layout: List / Grid;
- compact/comfortable density if useful.

### 6.13 Settings — About

Target content:

- version/build;
- open-source licenses;
- privacy statement;
- diagnostics/export logs if implemented;
- project/source links;
- legal/source-service notice.

## 7. Responsive portrait behavior

Supported UI must account for compact phones, large phones, tablets, and foldables operating in portrait windows.

Rules:

- important actions remain visible;
- system bars/insets are respected;
- bounded collection regions may shrink and scroll;
- text may reflow, but primary actions may not be pushed off-screen;
- when accessibility font scaling makes a focused screen no longer fit, secondary content should collapse, reflow, or move to a subpage rather than requiring discovery scrolling for primary controls;
- tests should include at least one compact portrait phone and one large portrait phone profile.

## 8. Technical architecture

### 8.1 High-level layers

```text
Android application
├── Kotlin + Jetpack Compose presentation
├── ViewModels / application services
├── Media3 / ExoPlayer playback
├── MediaSession / Android lifecycle integration
├── foreground download service integration
└── Rust binding layer
    └── Rust core
        ├── source abstraction
        ├── metadata resolver
        ├── format selection
        ├── download engine
        ├── persistence/library domain
        ├── integrity/recovery
        └── source adapters
            └── YouTube adapter
```

### 8.2 Rust responsibilities

Rust owns portable domain logic:

- URL/source recognition;
- source adapter interface;
- metadata model;
- format model and quality-selection policy;
- download planning;
- HTTP transfer logic where practical;
- chunk/resume/retry state;
- download state machine;
- checksums/integrity metadata;
- persistent library schema and migrations;
- media/subtitle/thumbnail inventory;
- source-specific extraction/resolution;
- error taxonomy;
- portable tests/fixtures.

### 8.3 Android responsibilities

Android/Kotlin owns:

- Compose UI;
- navigation;
- permissions/system pickers;
- lifecycle;
- foreground-service requirements;
- notifications;
- connectivity constraints;
- MediaSession;
- Media3/ExoPlayer playback;
- Android share intents;
- platform storage integration;
- platform accessibility behavior.

### 8.4 Playback boundary

Do not implement video decoding in Rust. Android playback uses Media3/ExoPlayer and platform codecs.

Where source media arrives as separate audio/video streams, v1 should investigate playing coordinated local assets directly when feasible before introducing FFmpeg-based muxing. Muxing may be added only where compatibility requires it and after binary size/licensing implications are evaluated.

### 8.5 FFI

Preferred approach: UniFFI unless implementation constraints show JNI/manual bindings are materially better.

FFI should be coarse-grained. Example operations:

- `resolve_url`
- `list_download_choices`
- `enqueue_download`
- `pause_download`
- `resume_download`
- `cancel_download`
- `list_library`
- `get_item`
- `delete_item`

Avoid chatty per-frame or high-frequency cross-FFI calls.

## 9. Source abstraction

Source-specific behavior must be replaceable.

Conceptual interface:

```rust
trait MediaSource {
    async fn can_handle(&self, url: &str) -> bool;
    async fn resolve(&self, url: &str) -> Result<MediaInfo>;
    async fn formats(&self, media: &MediaInfo) -> Result<Vec<MediaFormat>>;
    async fn download_plan(&self, selection: &MediaSelection) -> Result<DownloadPlan>;
}
```

The YouTube implementation must not leak provider-specific concepts into the general library schema unless represented through optional source metadata.

## 10. YouTube integration strategy

YouTube extraction is expected to be the most volatile subsystem. Therefore:

- isolate it behind the source adapter;
- keep extractor fixtures and regression tests separate;
- preserve enough diagnostic data to identify resolver failures without exposing secrets;
- support rapid adapter replacement/update;
- do not make the player dependent on live YouTube APIs after media has been downloaded.

The project must account for applicable YouTube terms, content permissions, copyright, distribution policy, and app-store policy before public release. Engineering capability must not be treated as legal authorization to download arbitrary content.

## 11. Download engine

Required states:

- queued;
- resolving;
- downloading;
- paused;
- retry-wait;
- failed;
- verifying;
- completed;
- canceled.

Requirements:

- resumable downloads where the source supports byte ranges or equivalent continuation;
- durable transfer state across process death/reboot when practical;
- bounded retries with backoff;
- explicit user-visible failure reason;
- temp/incomplete files must never masquerade as completed media;
- atomic promotion from incomplete to completed library state;
- cancel removes or retains partial data according to a documented policy;
- safe cleanup of abandoned partial downloads.

## 12. Storage and library model

Use SQLite for structured metadata unless an alternative proves materially better.

Core entities should include:

- library item;
- source identity;
- media asset;
- subtitle asset;
- thumbnail asset;
- download job;
- download segment/continuation state if needed;
- playback progress;
- settings/preferences where appropriate.

Each completed item must be playable using only local paths/URIs and local metadata.

The storage layer must support schema migration from the beginning.

## 13. Android background execution

Long-running downloads must respect Android background-execution rules.

Expected implementation:

- foreground service for active user-visible long downloads;
- notification channel and progress notification;
- WorkManager only where appropriate for deferred/retry work, not as a substitute for a properly managed foreground download;
- network constraints for Wi-Fi-only mode;
- resume state after app process recreation.

## 14. Security and privacy

- No analytics in v1 unless explicitly added later.
- No account/login requirement in v1.
- Do not log auth tokens, cookies, signed media URLs, or sensitive headers.
- Sanitize filenames and paths.
- Prevent path traversal.
- Validate external intent input.
- Treat remote metadata as untrusted input.
- Apply bounded network timeouts and response-size limits where appropriate.
- Keep diagnostics locally unless the user explicitly exports them.

## 15. Accessibility

- minimum 48 dp targets;
- meaningful content descriptions;
- TalkBack-compatible controls;
- focus order matching visual order;
- no color-only state communication;
- text scaling validation;
- sufficient contrast;
- visible progress labels in addition to progress bars.

## 16. Error UX

Errors must tell the user what happened and what they can do next.

Examples:

- unsupported URL;
- metadata resolution failed;
- source changed and resolver requires update;
- no compatible format;
- insufficient storage;
- network unavailable;
- download interrupted;
- local media missing/corrupt;
- subtitle unavailable.

Do not expose raw stack traces in the main UI.

## 17. Testing strategy

### Rust

- unit tests for domain models/state transitions;
- source adapter contract tests;
- parser/format-selection tests;
- downloader resume/retry tests with a controllable HTTP test server;
- persistence migration tests;
- corruption/recovery tests.

### Android

- ViewModel/unit tests;
- Compose UI tests for all primary screens;
- screenshot/golden tests for key portrait profiles;
- Media3 local playback tests where practical;
- share-intent tests;
- process-death/recreation tests for download state;
- accessibility checks.

### End to end

At minimum:

1. resolve fixture/source URL;
2. create download plan;
3. download assets;
4. restart app/process;
5. confirm library reconstruction;
6. disable network;
7. play completed item locally.

## 18. CI and build reproducibility

CI should eventually cover:

- Rust format/lint/test;
- Kotlin format/lint/unit tests;
- Android assemble;
- Compose/UI tests where feasible;
- FFI generation/build verification;
- license checks;
- dependency vulnerability checks;
- release artifact generation;
- deterministic version metadata.

Pin important toolchain versions and avoid silently floating critical build dependencies.

## 19. v1 scope

### In scope

- Android portrait-only app;
- Kotlin/Compose UI;
- Rust core;
- supported URL input;
- Android Share entry point;
- metadata/format resolution;
- curated quality selection;
- robust downloads;
- persistent offline library;
- thumbnails;
- subtitles where available;
- local Media3 playback;
- resume position;
- download/playback/storage/appearance/about settings;
- CI and automated qualification.

### Out of scope for v1

- landscape UI;
- account login;
- comments;
- recommendations;
- social features;
- YouTube browsing/home feed clone;
- arbitrary web browser;
- cloud sync;
- casting;
- Android TV;
- iOS/desktop UI implementation;
- public app-store release until policy/legal review is complete.

## 20. Cross-platform future

The Rust core should remain portable enough to support later shells:

- iOS: Swift/SwiftUI + AVPlayer;
- desktop: native/Tauri/etc. shell + platform media backend;
- other platforms as justified.

Platform-specific playback and presentation remain outside the Rust core.

## 21. Mockups

Initial visual direction is stored under `docs/markups/`.

The mockups are design references rather than pixel-perfect implementation mandates. Where a generated mockup conflicts with this written specification, this specification wins—especially the portrait-only rule, fixed functional-region rule, and no-hidden-primary-controls rule.

## 22. Definition of done for v1

v1 is engineering-complete when:

- every in-scope TODO item is reconciled;
- primary workflows work on supported portrait phone profiles;
- no primary action requires off-screen discovery scrolling;
- downloads recover correctly from ordinary interruption/process recreation;
- completed media plays with networking disabled;
- CI is green at the exact release commit;
- release documentation and known limitations are current;
- source-adapter volatility does not compromise the rest of the application architecture.
