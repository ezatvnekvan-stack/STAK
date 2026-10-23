# Motion, Home logos and source badges

This preview continues PR #5. The goal is short, consistent motion and less repeated loading while preserving the Capacitor layout. It does not promise instant live-provider responses or certify sustained 90 Hz on a physical device.

## Implementation

- Navigation uses a 230 ms entrance with a subtle vertical offset. Existing poster, button, icon, tab, switch, progress and sheet animations remain enabled. Settings tiles and source badge filters add layer-only press feedback; source lists animate insertions, removal and sorting; release details expand/collapse; Home title logos fade in.
- All new animation specifications respect the in-app reduced-motion option and Android's disabled animator setting. Animation durations use Compose's frame clock rather than a fixed 60 Hz timer. Optional ambient drift reads its clock in the graphics-layer phase, avoiding recomposition of the gradient and dot canvases every frame. Background lifecycle behavior remains paused.
- The activity requests a supported refresh rate near 90 Hz (or the nearest faster rate) at the current display resolution. It falls back to the fastest available lower rate and clears the preference when paused. Android, display settings, power mode and thermal conditions retain control.
- Home heroes display provider title logos, retaining a readable title when no logo exists, during loading and after failure. One adjacent hero page and its metadata are warmed; preloading is bounded rather than loading every title.
- Catalog reads share bounded per-key locks; matching shelf requests reuse the in-memory result. Public Cinemeta catalogs also use a five-minute disk cache; explicit refresh bypasses its age check. Matching public-metadata reads share a load. Existing credential checks and private artwork header restrictions are preserved.
- HTTP calls now use cancellable asynchronous delivery: leaving a screen cancels the underlying OkHttp call and closes a response delivered during cancellation. JSON/network work stays on the existing IO path; source badge/season-pack preparation moves to Default. Quality regexes compile once. Image requests are remembered and sized by Coil's layout constraints.
- Poster watchlist/watched membership is decoded once per collection update and passed from the active account, replacing per-poster Flow collection and repeated JSON scans. Watchlist decoding/filtering is remembered.

## Find sources

Movie and TV source cards use rounded monochrome panels, clean title identity and compact technical badges. Full provider release text, file index/hash and other inferred tags remain available in Release details; match warnings and TorBox cache/add actions remain visible.

Only inferred **4K and 1080p** releases are shown. The mutually exclusive resolution filters toggle off to show both. HDR, WEB-DL, Remux and Atmos filters combine with AND semantics; HDR includes HDR10/HDR10+/Dolby Vision. Clear filters restores the supported-resolution view, never 720p/unknown releases. Badges describe filename/provider labels, not verified file contents. Existing TV season/episode lookup and season-pack behavior is retained.

## Evidence and limits

`FrameTimingTest` uses the real Choreographer and Window FrameMetrics, without the synthetic Compose test clock. It records watchlist scrolling and repeated navigation with fixture data, including frame counts, median/p95/p99 total duration, counts above the 90/60 Hz budgets and observed display refresh. Evidence is exported as `Download/STAK-native-metrics/frame-timing.json` in the native validation artifact.

The baseline commit `b18cf7194ea7beceaa4c124abf9be88f98b61179` adds the same recorder to the previous implementation. Debug emulator timings are diagnostic; host variance, debug overhead and its display refresh prevent a sustained-90-Hz claim. Physical-device profiling and live provider/network verification remain required. No frame-duration assertions are relaxed to declare a passing performance result: the diagnostics have no speed gate, and functional assertions remain enabled.

The final handoff/PR records checks actually executed, CI links and APK identity. Additional memory/startup profiling, a profileable release benchmark and a physical 90/120 Hz device remain future validation work.
