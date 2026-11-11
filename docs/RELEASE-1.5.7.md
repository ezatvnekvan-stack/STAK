# Android 1.5.7 / code82 — Compact Home and Continue Watching

The owner approved two follow-up changes to 1.5.6. The same package, original signing identity, existing cloud configuration, encrypted data, schema4 and account scopes are preserved. No server update is needed.

## 1. Continue Watching

English-language branded movie/show backdrops are preferred at original resolution, ranked by pixel area and provider quality. Episodes retain their playback identity, position and duration, but their artwork comes from the series. When the row lacks public show identifiers, a bounded lazy read resolves the explicitly identified Jellyfin parent series; only its validated public identity is used. The request is read-only and account-scoped. No title search, name-based merge or episode-provider identity is substituted.

The fallback for a Jellyfin episode uses its explicit parent show's backdrop instead of the episode's primary image. Thumbnail resize parameters and episode image tags are omitted from that parent request. Missing English art retains a usable series image with a separate logo/title on the image; no provider can guarantee English branded art for every title.

Only episode/time information appears beneath the image, with no repeated title. The image has no lower blur or information overlay. The short Nothing progress bar stays within the image. One card plus approximately half the next remains visible; long-press actions and opening the original playback row are preserved. No status badges, percentages or three-dot button are added. Large text can wrap the separate information line without covering artwork.

## 2. Home hero

At normal text size, Home hero is half the screen height. Small landscape windows retain a bounded minimum and large text gets a small bounded allowance. The smooth fade into Current/Glyph, original-resolution textless artwork, centered logo/facts, cyclic dissolve, explicit paging and lifecycle/reduced-motion guards remain. Header controls and synopsis stay removed. Details geometry is unchanged by this follow-up.

## Included prior refinements

This release includes the previously completed 1.5.5 and 1.5.6 source changes, which had not yet been published on GitHub: outlined age badges and source action, high-resolution textless Home/Details images, taller Details artwork beneath metadata, larger single-frame source filters, compact recommended cards and acknowledged plus/check action, Library sorting and compact Watchlist totals. See the historical release notes for their implementation and fixture verification.

## Verification

Managed workflow `00cd1d3c0de9d88e8f94d04d67fca48c` used `gradle_run.py` and the designated `gradlew-local.cmd`, with `--no-daemon`. After the final passing run, `finish` succeeded and removed only wrapper-owned temporary logs.

- "Helyes a sorozatháttér és a félképernyős hero?" — PASS: 12 focused JVM cases (`:app:testDebugUnitTest`, ContinueArtworkTest and CinematicGeometryTest), plus Android fixture compilation. Covers explicit parent artwork/identity, rejection of unrelated parents, no-key/no-network behavior, original English selection and half-screen geometry.
- "Megfelelő az új Home és Continue Watching felület?" — PASS: 22 emulator cases (`:app:connectedDebugAndroidTest`, Cinematic154/155/156UiTest and PremiumHomeUiTest). Includes metadata below the image, progress inside it, half-next-card geometry, large text, English-art title suppression, half-height Home, fallback images, seamless fade, reduced motion and pager interactions, with retained compact-source/Library/Watchlist fixtures.
- "Sikeres a teljes regresszió és az aláírt frissítés buildje?" — PASS: `:app:testDebugUnitTest :app:testPreviewUnitTest :app:assemblePreview`; 521 debug and 521 preview JVM tests, zero failures/errors/skips. Original signing identity and existing cloud build-input presence were verified without displaying configuration values.

Rendered local synthetic fixtures were inspected for the half-height Home and regular/large-text Continue Watching. The optimized APK installed in place (`adb install -r`) and cold-launched successfully without uninstalling. These checks do not establish live owner-phone performance or live artwork/provider availability. No owner-service acquisition/deletion/playback mutation was used as a test.


APK SHA-256: `8106901f81e5a1da6fc5ab54bb68cf7ed907a83a9434632e030e4d4d9c181868`. Original certificate SHA-256: `bd9c33ebf4a4a62fc034cbb363635ed8d4d55f081ce43fcaff59706d2230c430`. Package: `app.stak.nativeapp.parity`; versionName `1.5.7`, versionCode `82`.
