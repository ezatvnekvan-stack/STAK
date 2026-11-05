# STAK Android 1.2.0 / code60 — 2026-11-03

## Delivered

- Discover → Lists: public Trakt Popular/Trending/search, paginated mixed movie/show titles in rank order, bounded visible-card collage requests, local saved lists included in encrypted cloud backup. Uses the existing Settings → Metadata & ratings → Trakt Client ID; no additional login.
- Details shows clicked artwork immediately, loads provider core before TMDb seasons/OMDb ratings, requests only the selected season, retains loaded seasons and offers retry. Canceled loads cannot overwrite a new title/season. Full metadata loading for background tracking remains available.
- Home and Trakt rows publish independently and progressively, with stable loading slots. Search retains genuinely matching candidates during debounce. Smaller gallery/cast/episode thumbnails, shared Coil artwork caching and credential-hashed public TMDb response caching.
- Metadata cache budgets: 192 entries, 16 MiB memory, 24 MiB disk, 15-minute TTL. Coil artwork: 20% app memory budget, 160 MiB disk. No private service, source/acquisition or watched-state responses enter the new metadata cache.
- Compact floating Find Sources pill, transparent surrounding footer, reserved content space, 48dp+ touch target. Shared touch/navigation timings120/220ms; existing reduced-motion and lifecycle/scroll gates retained.

## Verified

- 466 debug JVM tests and 466 preview JVM tests, no failures/errors. Thirteen new protocol/cache/progressive-loading checks.
- Seven API35 emulator tests: mixed list opening/saving with blocked artwork, actual local-image collage, Details before seasons, season failure/retry/cancellation, floating action geometry/tap, shared artwork navigation, nav selection and rank typography.
- Rendered PNGs inspected for collage, Details and compact action. Tests use synthetic credentials/local images; no owner TorBox mutation or private service calls.
- Optimized preview build, lintVital, original signer and package verified by build-update.ps1; cloud deployment injected.

APK: distribution/STAK-current.apk
Application ID: app.stak.nativeapp.parity
Version: 1.2.0 / 60
SHA-256: 03b71395295cff63aac73267f078927cf1a84df703d52fc4f1f9dada94cd353e
Certificate SHA-256: bd9c33ebf4a4a62fc034cbb363635ed8d4d55f081ce43fcaff59706d2230c430

Signed optimized APK also installed with adb -r on API35 and cold-launched successfully; process stayed alive with no AndroidRuntime crash. This is an emulator smoke check, not a physical-device speed measurement.

## Practical limits

No physical-device frame-rate or first-load network timing claims. Public Trakt APIs were checked against official documentation and offline contract fixtures; the owner's live Client ID/account was not used. Initial network requests still depend on provider availability. No owner-phone upgrade or cloud restore was performed. Original identity/signing/data schema remain unchanged so this is distributed as an in-place update; do not uninstall first. STAK Server1.2.2 does not need an update.
