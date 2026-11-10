# STAK 1.5.3 — Optional Glyph background

Android versionCode 78; next distributable code79+. Install over the existing app with the original signer; do not uninstall.

## Approved changes

Details no longer shows the Series Center shortcut or its wrapping row. Auto-add remains. IMDb wordmark, border and digits use brighter off-white without enlarging the score, moving it or adding /10. Existing Trailer/watchlist spacing remains.

Dolby Vision and Atmos use the same Doto typography, label color, border and surface as the other shared FormatBadge components. Their native monochrome icon is smaller and uses the same subdued ink. Find Sources filters remain directly accessible.

Settings → Appearance → Background offers Current and Glyph previews. Current is the default for old installs, absent preferences and unknown values. The stable background_style preference is included in the existing encrypted cloud snapshot; no schema migration, server update or account setup is needed. Atmosphere switches decoration for either choice; disabling it leaves a plain dark surface.

Glyph is drawn with a few vector strokes over matte black. Home has large broken off-edge arcs, Details has a segmented side line, and the other pages share the same geometry. Artwork remains colorful. The background does not intercept touch input. Route changes and newly acknowledged source-add outcomes produce a brief restrained off-white light signal. Opening a sheet with already-added releases does not emit a fake success. A signal waits behind a modal or active scroll, active motion stops when blocked, and completed events do not replay on resume. Reduced motion disables pulses entirely. There are no loops, timers, blur passes or image downloads in the Glyph renderer. Glass samples only the background layer, never a new whole-screen capture.

## Preserved behavior

The eight production files restored from 1.4.0 in 1.5.2 remain identical to that baseline. Tracking remains read-only. Cached-only acquisition guards, account scopes, explicit consent, unknown-outcome protection, existing history, Room schema, cloud configuration and original package identity remain intact. No STAK Server changes.

## Verification

Managed workflow 9a72b7c0f3a7ccbb03926d37041e9acf. Question: "Do optional Glyph settings, motion, restored pages and source/detail presentation preserve existing behavior?" PASS: 501 debug + 501 preview JVM tests, 21 focused emulator tests, zero failures/errors/skips. The initial device-test compile needed the existing shared-transition opt-in annotation; corrected before the passing run. Tests cover persisted/default/unknown preferences, backup restore, both selector directions, one-shot signal completion and non-replay, reduced motion, source cached-only Add behavior, passive tracking, IMDb metadata/action spacing, and removal of the Series Center shortcut while retaining Auto-add. Settings/metadata/badges, source cards and series Details screenshots were visually reviewed. All eight restored 1.4 production files compare unchanged against the original baseline. Official signed package verification is recorded in the GitHub release notes. Emulator/offline fixtures do not establish physical-phone performance or live owner-service connectivity. No live TorBox mutation is used for testing.
