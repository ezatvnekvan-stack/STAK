# Android 14.3.0 — Nothing menus, service catalogs and trailers

This update builds on the complete 14.2 source and retains its integrations, secure storage, tracking, package identity and data. Nothing design, theme/accent settings and system/app reduced motion remain active.

| Request | Implementation |
|---|---|
| Replace large grey system selection pages throughout the app | `NothingSelect` uses existing Radix Select primitives: anchored, bounded, theme-aware menus, keyboard/typeahead, selection marks, disabled entries, focus restoration and Android Back dismissal before the containing sheet. Discovery, Home settings, source season/episode/sort/size, torrent/watchlist sorting and remote-device selectors use it. No visible native HTML select controls remain. |
| Hero: remove Trending this week and Movie/Series labels | Removed. The hero shows actual title genres and US age certification (e.g. PG-13/TV-MA) from supported TMDb endpoints; missing classifications remain absent. Ratings keep their real source and visibility settings. |
| Beautiful swipe and automatic hero transitions | Directional crossfade/short slide and subtle scaling, animated selection dots, vertical-scroll-friendly pointer gestures and suppressed accidental navigation after swiping. Transitions/automatic cycling respect reduced motion; background cycling is skipped. |
| Discovery: only Apple TV, Netflix, HBO Max, Disney Plus, Hulu and Paramount Plus | Exactly six curated brand choices. Their actual region/type provider IDs and logos come from TMDb. Relevant native subscription/ad-plan variants are combined with the supported OR provider parameter. Unsupported brands such as Prime are excluded. Unavailable brands are disabled truthfully, not assigned invented availability. |
| Browse all available movies/shows on a service | Selecting a service opens a grid over the official TMDb discover endpoint, with Load more, region/type switching and existing filters. No trending/popularity vote threshold excludes normal catalog entries. Coverage depends on TMDb/JustWatch regional metadata and its API pagination cap of 500 pages; this is not a claim to index every title the service offers or prove subscription entitlement. |
| Remove watched 0/7 etc. from Add to Library | Watched totals removed from source-season options. Existing watched state and episode controls on Details remain. |
| Resolution visible rather than dropdown | All/4K/1080p/720p/SD/Other are visible wrapping Nothing chips with actual result counts; no native select or resolution dropdown. Other appropriate source selectors remain compact custom menus. |
| Watch trailer near top of Details, playing in app | Restored a compact Nothing button below the hero. Official valid YouTube trailer keys open a responsive privacy-enhanced embedded dialog, with loading, retry, provider errors, fullscreen where supported, background pause and clean closure. External YouTube fallback remains available. No VLC or general-purpose playback engine. |
| Compact gallery | Thumbnail height reduced from 112px to 80px with tighter spacing; horizontal rail and fullscreen artwork retained. |
| Behind the Camera only 3–4 people | At most four distinct real people, combined accurate roles, directors/creators/showrunners prioritized. Fewer are shown when fewer are supplied; no names/roles are fabricated. |

## Verification

Local adapter regressions: 62 passed. Source/import and Android configuration checks run before publishing. Full Actions verification installs dependencies, lints, runs Jest and adapter tests, compiles standalone React, runs four production Chromium layouts, synchronizes Capacitor, builds Gradle debug APK and verifies assets/native plugins/signing and ZIP extraction. Generated `CI_TEST_RESULTS.md` records actual outcomes for the artifact. Final PR metadata identifies the verified head and artifact URL.

Updated tests cover six-brand ID grouping, unranked regional catalog pagination, four-person crew compaction, trusted YouTube frame events and malformed/unavailable trailer recovery. Production browser fixtures additionally verify custom menus in a source dialog, resolution chips, absence of watched labels in source options, service grid pagination/region selection, real metadata binding, trailer-frame creation/error/closure and no visible native selection controls. Fixtures are test-only; no live-provider or device success is claimed.

## Trailer security and runtime limits

The latest user instruction explicitly requests in-app trailer playback, superseding the earlier external-only trailer request. CSP now permits frames only from `https://www.youtube-nocookie.com` and `https://www.youtube.com`; it does not allow arbitrary frames or remote scripts in the app. Video identifiers are validated. Player events must come from the current iframe window and an official HTTPS YouTube origin. A bounded timeout offers recovery if playback cannot be confirmed.

YouTube can block embeds because of uploader restrictions, age/region restrictions, network failures or Android WebView identity/referrer requirements (including error 153). These cannot be reliably bypassed by the app. Error messages and an explicit external fallback cover these cases. Fullscreen and actual playback still require Android/provider testing. Chromium tests use an isolated blocked-embed fixture, not a live movie trailer. No physical Android/emulator/live credential tests were performed.

See `BUILD_ANDROID.md`. Original signing keys are required for an in-place update to existing installs; do not uninstall or clear storage to bypass a signature mismatch. Do not commit keys, credentials, dependencies, caches or generated APKs. The Actions artifact contains the compiled APK only after a successful build.
