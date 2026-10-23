# Glass reference preview

This branch continues PR #6, using the user's supplied Home and Find sources screenshots as the design reference. It preserves the six Nothing-style source filters and shows only 4K/1080p releases.

- The floating taskbar uses a shared GPU backdrop through Haze 1.5.3, sampled at half resolution, with a translucent tint, restrained noise and glass edge highlights. Settings panels, search, Continue Watching and source cards share inexpensive glass surfaces. Sheets request Android 12+ compositor blur, with readable translucent fallbacks when the system cannot blur. Scrolling cards do not each allocate a blur pass.
- Home uses a larger artwork/logo hero and a two-column landscape Continue Watching rail. Resume minutes come from actual runtime/playback position. Next Up loads from the configured Jellyfin server; resumed series are deduplicated. A partial failure retains available results and shows an honest warning. Episode identity survives metadata/cache/backup serialization.
- Poster long-press opens a compact, focusable menu anchored to that poster, with existing detail/watchlist/watched actions.
- Source cards show the actual release filename, compact badge facts, size, inferred season-pack/episode counts, cache and seed/provider facts, expandable full details and a circular add/open action. The first exact filename match may be labelled suggested; this does not verify file contents or playback.
- The decorative dot field is rasterized off the main thread into one cached texture, replacing hundreds of per-dot gradients during redraws. Existing cancellable IO, background parsing, cached metadata and reduced-motion behavior remain intact.

`preview` is a non-debuggable, R8-optimized, resource-shrunk, profileable build, signed with the ephemeral debug certificate for testing. It is not the permanently signed release configuration. App APIs are kept for instrumentation while allowing optimization. CI runs the full functional tests and both viewport passes on debug and preview, retaining separate reports, captures and real Window frame measurements. The installable preview APK is the performance testing artifact.

Frame reports include total frame duration, UI layout/draw/animation/input work, unknown delay, buffer swaps, GPU time when supported, dropped metric reports and actual/requested display refresh. These measurements distinguish app work from emulator/driver delay. No frame-speed gate is bypassed: the recorder is diagnostic, not a 90 Hz pass certificate. Sustained 90 Hz requires an actual supported device, its system settings and physical profiling. Live-provider latency and remaining feature/accessibility/edge-state parity are not certified by fixtures.

Signing compatibility must be checked against the installed APK. CI's debug certificate can differ between runs; export a backup before replacing an incompatible installation. No signing keys, secrets or local.properties belong in commits.
