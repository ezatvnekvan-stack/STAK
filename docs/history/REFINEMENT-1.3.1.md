# Android 1.3.1 / code65 — IMDb, streaming picks and progressive sources

## Changes

- Only valid IMDb scores are displayed. Details first tries the configured OMDb provider, then exact-identity Cinemeta IMDb metadata; missing scores stay unavailable. TMDb ratings never masquerade as IMDb. Alternate rating badges and TMDb rating sorting were removed.
- Trailer has an explicit labelled button; narrow layouts move it to a second row rather than squeezing controls. Existing trailer and watchlist actions remain.
- Continue Watching uses consistent compact landscape cards (148dp high, at most276dp wide), two-line title space, aligned playback/episode information, restrained progress and visible next-card peek. It replaces the variable-width thumbnail layout.
- Discover For tonight queries popular streaming films in the configured region, then verifies a past local digital release and actual subscription/free/ad-supported provider metadata. Theatrical-only, rental-only, future and unknown availability are excluded. Up to3 confirmed picks retain popularity order. Series mode uses aired titles with regional streaming metadata. This is catalog availability, not a guarantee of subscription entitlement.
- For tonight and the first Home Trending Movies spotlight use title logos when available, with a stable text fallback if absent or unavailable. IMDb-based trending entries resolve to TMDb by exact provider identity, never title guessing. Home publishes the row before awaiting branding.
- Find Sources starts hash/cache and torrent-metadata verification as individual providers respond. Confirmed results appear without waiting for slower providers or metadata jobs. At most8 automatic metadata checks total per search; explicit Check more retains its bounded batches. Existing global3/per-indexer1 private metadata fetch limits remain.
- Completed searches can be reopened from account/configuration/title/episode-scoped RAM for30 seconds. Explicit refresh bypasses that reuse. Every acquisition still performs fresh cache confirmation; original private torrent data, identity checks and account guards remain unchanged.

## Compatibility and verification

Original application ID/signing key, encrypted settings, Room schema4, cloud configuration and read-only Jellyfin tracking remain. Optional server1.2.3 is unchanged; no Linux update required.

- 478 debug and478 preview JVM tests passed after the source changes: provider streaming dates/region checks, exact IMDb rating/branding identities, partial arrivals and duplicate raw batches included.
- 23 focused API35 emulator checks passed after fixing stale remaining-count presentation on cache failure. Final logo/Home initialization checks and signed-build verification are recorded below.
- UI captures use isolated settings, local synthetic images and controlled provider responses. No live owner-account acquisition, private-tracker download, or physical-phone performance measurement was performed. Provider response time and network image downloads remain external limitations.

Official streaming query reference: https://developer.themoviedb.org/reference/discover-movie

## Final delivery verification

- All23 focused API35 checks passed; all7 refinement checks passed again after final Home initialization and actual Discover-card logo coverage (24 distinct checks overall).
- Official optimized APK built with existing cloud configuration, original signature, R8/resource shrinking and vital lint. Debug478 and preview478 JVM checks passed; debug repeated in the signed build.
- In-place APK update and cold launch on API35 succeeded; no AndroidRuntime crash recorded.
- Version1.3.1/code65, applicationId app.stak.nativeapp.parity.
- APK SHA-256: 7b3427787306d150a5a42367fbcb7a9b78c9b4e917f06d4d9e4c453d96311b4e
- Certificate SHA-256: bd9c33ebf4a4a62fc034cbb363635ed8d4d55f081ce43fcaff59706d2230c430
- Existing users install as an update, without uninstalling/clearing data. Physical phone/live services remain unverified.
