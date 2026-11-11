# Android 1.5.6 / code81 — six approved refinements

Optional STAK Server stays 1.2.3. No deployment update is required.

## 1. Home poster stage

The Home hero follows a 2:3 portrait canvas, capped at 85% of the screen in landscape/tablet/large-text conditions. Top-aligned original-resolution textless artwork, separate logo, compact metadata and pagination remain. Its lower edge dissolves into the real Current/Glyph background. Home has no STAK wordmark, app search/settings/status controls or synopsis; the Android system status bar is unchanged. Search remains in Discover/Search and Settings remains in bottom navigation. Other Home shelves and their customization are retained.

## 2. Continue Watching

One full card and approximately half of the next share the rail after its 12dp gap. Cards use a 16:10 artwork stage, with bounded extra space for large text. Visible/lazily composed rows request public artwork through a three-request gate, using explicit provider identities only. Prefer the highest-resolution English-language TMDb backdrop and `original` URL. If unavailable, use a neutral backdrop with separate English/neutral logo or the title text. No fuzzy title matching, playback write or account mutation is involved. Original Jellyfin episode/progress/position data remains authoritative.

Remove visible Next Up/In Progress badges, percent labels and three-dot menus. Retain the existing long-press context actions. The lower information band shows known season/episode and remaining time; the short segmented progress indicator spans roughly 28% of the card (60–100dp bounds). A small cached Coil transformation blurs only a downsampled artwork copy (at most 160px per edge), masked into the bottom band. It works on API26+ without live screen capture, repeated CPU work per frame or animated blur radius.

English language classification is a provider hint, not text recognition. If the provider offers no English branded image, the app cannot manufacture one. Missing/failed enrichment retains usable artwork and title identity. Home/Details keep their separate textless artwork policy.

## 3. Details image and metadata

The image stage is 20% taller than 1.5.5, including proportional phone/tablet bounds. The image layer now covers the logo **and metadata** within the same masked composition. A longer fade and subtle lower darkening retain readability. One-shot artwork/logo/copy reveals respect reduced motion; no steady zoom crops the top. Find Sources/Trailer/Watchlist sizes and the transparent outline treatment are preserved.

## 4. Find Sources

Quick filters have one larger, readable 44dp-minimum selectable surface: no nested small format mark inside a second selected frame. Selection animates the single border/text with a restrained fill. HDR/Atmos/WEB-DL/Remux remain visible beside the resolution controls.

Cards remove the Recommended text row. A subtle stronger edge and small red marker identify the recommended release; accessibility retains that meaning. Actual file size moves to the upper-right in a slightly brighter compact surface, using whole-torrent size for packs as before. The action is a 48dp outline plus button, with an existing busy animation while submitting. Only an acknowledged `added` state morphs the plus into a check; errors/uncertain outcomes cannot show success. The check opens the original added-release file flow. Cached-only eligibility, exact private material, explicit action, account scopes and duplicate/unknown-outcome guards are unchanged.

## 5. Library

The main Jellyfin row shows Sort by beside Scan Library: Recently added, Title A–Z, Title Z–A and Release year. Sorting is local over the existing scoped loaded library, with stable tie-breaks; it never triggers a scan or acquisition. Libraries/collections selection moves inside Library filters and opens the same collection sheet. Existing movie/series/status/search/view/versions and scan-permission flows remain.

## 6. Watchlist

Large separated counters become one left-aligned, 11sp Space Mono line, e.g. `3 movies · 1 series`. Reduced spacing brings the search/poster content upward. Counts remain based on actual saved items and update with them. Watchlist filtering, ordering, cache checks and item actions are unchanged.

## Verification

Managed Gradle workflow `d82429fb1a98d251de0742c8a867e3ec`, designated `gradlew-local.cmd`, bounded-output `gradle_run.py` lifecycle. Runs used `--no-daemon`; after the last successful validation and closing the isolated build shell, `finish` succeeded and removed only wrapper-owned temporary logs.

- "Helyes a képválasztás, a rendezés és az elrendezés?" — PASS: 30 focused JVM cases for artwork choice, explicit identity/fallbacks, library sorting and geometry.
- "Működik és jól jelenik meg mind a hat módosítás?" — PASS: 50-case emulator suite, then 7-case focused follow-up after the narrow/large-text Continue Watching adjustment. There are 51 distinct passing device cases across those runs. Includes reduced motion/carousel selection, image fallback, Source acknowledgement/disabled gates/files, larger filters, right-aligned size, compact totals, collection selection via filters, scan permissions, Glyph, passive tracking and injected TorBox management fixtures.
- "Sikeres az eredeti aláírású frissítés teljes regressziója?" — PASS: final `:app:testDebugUnitTest :app:testPreviewUnitTest :app:assemblePreview`; 518 debug + 518 preview JVM cases, zero failures/errors/skips.

Rendered fixtures reviewed: taller header-free Home, portrait Details with metadata over the extended artwork, English-branded half-next-card Continue rail, narrow large text, selected source filters, compact source card, confirmed check, library sort/collection flow and Watchlist totals. An initial missing source-button wiring and Windows text-decoding issue were corrected before the successful suite. All edited Kotlin text is UTF-8.

APK: Android1.5.6 / code81, package `app.stak.nativeapp.parity`. Original signer SHA-256 `bd9c33ebf4a4a62fc034cbb363635ed8d4d55f081ce43fcaff59706d2230c430` verified by the existing export script. APK SHA-256 `6e9b1a5288e916b90795a4050cbfb8d4479e815f5690221bff74f6b7fbe2a5de`. Original cloud URL/publishable-key presence verified in the optimized APK without printing values. In-place emulator installation (`adb install -r`) and cold launch passed without uninstalling. The private desktop development copy and `distribution/STAK-current.apk` were updated; keys/configuration/APKs remain outside Git.

Device fixtures use local/synthetic content and injected clients; they do not prove owner-phone performance, live English-artwork availability or real provider/account connectivity. No owner-service acquisition/deletion/playback mutation was used as an automated test.
