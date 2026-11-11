# Android 1.5.5 / code 80 — cinematic refinement

Implements the eight approved refinements to 1.5.4. Server stays 1.2.3.

1. **Home hero and classification**: Home grows from roughly 44% to 52% of screen height (320–500dp bounds). Its rectangular artwork stage clips transformed pager layers and masks the lower image/scrim to transparency, exposing the actual Current/Glyph background instead of an opaque flat cutoff. Shared age badges retain compact size and native serif lettering but use transparent fill and off-white outline everywhere.
2. **Details facts/actions**: centered genres precede a wrapping facts/age/compact IMDb line, with a subtle divider and the existing small red rating dot. Sources shares the transparent fill, outline color and corner treatment of Trailer/Watchlist. Find Sources retains its previous full width and 48dp minimum height; only the distinct fill/outline styling changes. Separate action rows and accessible large-text stacking remain.
3. **Sources**: adjacent 7dp-spaced HDR/Atmos/WEB-DL/Remux quick filters stay visible; 4K/1080P use the same format-mark treatment with counts. Minimum 44dp targets remain. Resolution controls wrap beside provider/settings actions. Result cards start with actual quality badges and size; duplicate large quality headings are removed. Filename, recommendation, cached state, expand/inspect, explicit Add and added-files actions remain.
4. **High-resolution art**: Home and Details request TMDb `original` URLs for selected images/logos, with distinct URLs/cache identities from old thumbnail entries. Coil still decodes for the displayed surface to bound memory. Poster grids keep their existing size-appropriate URLs.
5. **Details blend**: a longer multi-stop alpha mask gradually exposes the underlying atmosphere, including under light images. No artificial network delay is introduced.
6. **Movie library status**: hide the duplicate video-height/HDR subtitle on movies. Keep truthful library presence, Media Quality, playback progress and read-only watched state.
7. **Composition**: top-center crop alignment on both cinematic surfaces; remove steady zoom and Details parallax, and limit Home transition-only depth to 2% with top-center transform origin. This preserves the available top of the original; it cannot restore content already absent from an uploaded image.
8. **Textless preference**: independently select neutral-language portrait art for the tall stages, then neutral backdrops, then the existing image. Within each eligible kind, choose highest pixel area, then provider rating/votes. Fetch original resolution; keep logo separate. Selected-image load failure falls back to the original-resolution conventional image. This changes neither grid posters nor the user's metadata-provider preference.

## Artwork limits

TMDb's null/empty image-language tag is a textless **hint**, not OCR proof. Availability/composition varies by title; the app cannot guarantee textless imagery when the provider has none. Cinemeta metadata may optionally receive TMDb artwork enrichment when a TMDb key and matching identity exist; that enrichment cannot replace metadata or fail the title page. Without TMDb configuration, retain the existing provider image. No BetterPosters/new service, subscription, credential or fabricated artwork is added.

## Preserved behavior

No Room migration, account/cloud recreation, server update, manual watched control, automatic acquisition consent or backend change. Original Tracking/Discover content, Current/Glyph switching, passive Jellyfin tracking, private-source metadata, account scopes, cached-only confirmation and uncertain-add safeguards remain.

## Verification

Managed Gradle workflow: `12717838d3cac6003315d5721ec53191`.

- "Do cinematic artwork selection, existing metadata fallbacks and responsive geometry pass their focused JVM tests?" — PASS, 18 focused cases (8 new artwork-selection cases, 7 metadata cases, 3 geometry cases).
- "Do the refined Home, Details and Sources layouts render correctly and preserve their existing interactions?" — PASS, final 36-case emulator suite, 0 failures/errors/skips. Includes Home carousel selection/reduced motion, film/series Details, source cached-only button gates, Glyph and passive tracking. The 5 new cases also passed separately after fixing painter replacement and supplying isolated Jellyfin settings to the quality-action fixture.
- "Does the official 1.5.5 update pass full JVM regression and build with the preserved signing and cloud configuration?" — PASS: `:app:testDebugUnitTest :app:testPreviewUnitTest :app:assemblePreview` via the designated launcher and managed wrapper. 512 debug + 512 preview JVM cases, zero failures/errors/skips. The old 500px hero-logo expectation was updated to the approved original-resolution URL.

Rendered local fixtures were reviewed for Home top preservation/transparent fade, film/series metadata and action spacing, outline age marks, large text and compact Sources cards. The failed painter is disposed before the replacement mounts, preventing outgoing failure callbacks from interfering with the fallback.

Tests use synthetic local images and isolated presentation/data fixtures; they do not prove live owner-service connectivity or textless availability for every title. No real owner-service acquisition, deletion or playback mutation was used.


## Delivered APK

- Android 1.5.5 / versionCode80, package `app.stak.nativeapp.parity`.
- Original certificate SHA-256: `bd9c33ebf4a4a62fc034cbb363635ed8d4d55f081ce43fcaff59706d2230c430`.
- APK SHA-256: `cdb937ed0e8523096b3ee26c8632e841bcd7e3419dc241cb4531a75bac448c38`.
- Private portable development copy and its `distribution/STAK-current.apk` updated. APKs/signing/cloud configuration stay outside Git.
- Original cloud URL and publishable-key presence verified in both the preceding 1.5.4 and optimized 1.5.5 APK, without exposing values.
- In-place emulator installation (`adb install -r`) and cold launch succeeded, without uninstalling. The user's phone update and real provider-artwork coverage are not claimed by these checks.
- Managed build run9 completed with exit status0. After closing the isolated build shell, `finish` could not remove its own 0008.log because of Windows file locking (WinError32). No unrelated process was terminated and no forced cleanup was attempted. The tests and APK are unaffected; do not describe the workflow as fully cleaned.
