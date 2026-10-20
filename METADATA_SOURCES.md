# STAK v2.4.1 — Metadata sources

Settings → Metadata sources controls the Android metadata pipeline. It does not use the Linux frontend, an Emergent preview, or port 8085. Package ID (`app.dmmstudio.cinema`), Vault alias, watchlists, tracking lists and backup format are retained.

## Sources and defaults

| Purpose | Default | Choices/fallback |
|---|---|---|
| Search | TMDb | TMDb / explicitly selected Cinemeta; missing TMDb key uses Cinemeta with notice |
| Movies | TMDb | TMDb / Cinemeta |
| Series | TMDb | TMDb / Cinemeta |
| Metadata language | en-US | English only; no language selector |
| OMDb ratings | On | IMDb / Rotten Tomatoes / Metacritic, own OMDb key required |
| IMDb on lists | Off | Optional extra requests; daily quota warning |

TVDB provider integration is excluded as requested: no provider module, authentication, API key/PIN controls, season-order selection or TVDB host registration. Legacy proposed `series: tvdb` preferences safely normalize to TMDb; unrelated settings and any existing secure data are not erased. TVDB external identifiers remain passive identity fields resolved through TMDb, without contacting TVDB.

Search queries one selected provider, never a Cinemeta/TMDb result merge. TMDb multi-search excludes people, supports title/year matching and deduplicates IMDb/TMDb identities. Existing advanced regional provider/certification/IMDb filtering uses TMDb movie and TV search endpoints within that single provider. Provider/age/original-language filters report that they require TMDb when Cinemeta is selected. IMDb filtering requires enabled OMDb credentials, warns about quota and verifies the loaded page; unknown ratings are excluded. Labeled TMDb list scores require at least 20 votes and are never relabeled IMDb. IMDb/RT/Metacritic details and episode ratings still use OMDb.

## Identity and normalized metadata

`metadata/resolveIdentity.js` accepts `tt…`, `tmdb:ID`, `tvdb:ID`, or an object with external IDs and `type: movie|series`. TMDb `/find` and movie/TV `/external_ids` resolve canonical IMDb IDs. Bare TMDb IDs are ambiguous: both type spaces are checked and exactly one match is accepted; otherwise callers must supply the type. Results persist for 30 days. Legacy saved IDs are not bulk rewritten or deleted. Titles without IMDb IDs remain visible, with source lookup disabled and explained.

Provider modules normalize titles to `id, imdb_id, tmdb_id, tvdb_id, type, name, year, poster, background, overview, genres, rating:{tmdb,imdb,rt,mc}, videos`, retaining `imdbRating` and `tmdbRating` aliases. TMDb metadata requests always use `language=en-US`, including search, titles, episodes, genres, cast/crew, calendars and trailers. Legacy Hungarian or other metadata-language preferences normalize to English on read and persist as English on the next settings save, preserving source/rating preferences and credentials. Caller-supplied metadata language cannot override English. Title logos prefer English, then language-neutral artwork; a foreign-language logo is never used as a fallback. Redundant translation fallback requests were removed. Existing cached Hungarian responses are not read because the provider cache key includes the effective English request parameters. Saved watchlist/tracking names are user data and are not rewritten. Original-language content filters and regional availability remain independent from metadata language. If a provider lacks an English field, it may be empty or use the provider's original title; STAK does not invent translations. Cinemeta remains an explicit alternative or visible missing-key fallback. Existing TMDb cast, crew, logos, trailers, gallery and recommendations enrich missing presentation data; the chosen core metadata source keeps priority for year/status.

Calendar uses TMDb lightweight summaries (`next_episode_to_air` / `last_episode_to_air`) and only their current/next season dates. It never invokes full Details metadata per followed series. Later/unannounced seasons may be outside its coverage.

## Storage, caching and security

- Settings use the existing account namespace. New TMDb/OMDb credentials use `DMMVault` / Android Keystore, never public IndexedDB or plaintext localStorage. Browser previews retain newly entered credentials only in memory for the session.
- Existing native/global TMDb and OMDb credentials remain readable until replaced in account-scoped settings. No existing credentials or user data are reset.
- TMDb read tokens use `Authorization: Bearer`, without an API key query parameter. v3 keys remain accepted. OMDb requires its key as a query parameter; credential-bearing URLs/headers are not persisted as cache keys or logged.
- Public TMDb/Cinemeta details and OMDb ratings are cached in IndexedDB for 24 hours, identities for 30 days. Credential hashes isolate provider caches. Bounded record/storage limits and memory fallback handle unavailable IndexedDB. TTL-cached data can be stale until expiry.
- OMDb HTTP 429 and quota-error envelopes set a persistent 24-hour cooldown. Cached ratings remain readable and uncached ratings are not immediately retried.
- Transport caps concurrent requests at four and preserves cancellation and authenticated redirect/downgrade protections.
- Direct client access requires user-supplied keys; no shared secret is embedded in the APK. Device compromise remains outside Keystore guarantees.

Official TMDb references checked: https://developer.themoviedb.org/reference/find-by-id and https://developer.themoviedb.org/docs/authentication-application. Account-specific API permission, quota and artwork rights remain the account owner's responsibility.

## Build and validation

From `frontend`: `npm install --legacy-peer-deps`, `npm run lint`, `npm run test:ci`, `npm run build:android`, `npm run cap:sync`, `npm run android:prepare`, `npm run android:verify`. Then `cd android && ./gradlew assembleDebug` with Java 21 and Android SDK/platform/build-tools 35. Root checks: `node --experimental-vm-modules scripts/tests/adapters.test.mjs`, `node --expose-internals scripts/check-source.cjs`, `python3 scripts/verify_android.py`. Production Chromium checks: install Playwright as shown in `.github/workflows/android-validation.yml` and run `node scripts/browser-smoke.cjs` after the web build.

The CI workflow gates release publication on lint, all React/adapter tests, the production build, browser checks, Capacitor sync, Gradle debug compilation, APK asset/signature inspection and ZIP validation. It creates a **new** v2.4.1 release without replacing v2.4.0 or earlier releases or modifying main. Source and APK assets are generated by CI, not committed.

Debug signing is generated on the build runner unless the original private signing key is available. The release ZIP includes `validation/signing-compatibility.json` compared with v2.4.0. A mismatched certificate cannot update an installed APK in place. Do not uninstall or clear app data to bypass that error; preserve backups and use the original signing key for an upgrade-compatible build. No physical-device, live TMDb/OMDb/Cinemeta verification is claimed.
