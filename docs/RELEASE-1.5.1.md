# STAK 1.5.1 — Activity diary and cinematic Discover

Android versionCode 76; next distributable code77+. Built on 1.5.0, retaining Following, cached-only source additions and Auto-add Coming Up. No Linux/server update, account migration or new backend.

## Activity
The existing Watching preference key now displays Activity. The main Tracking page is a month-selectable, date-grouped poster diary, with a compact month/count/estimated-runtime header. Selecting the summary opens statistics for that same month. Same-series episodes on one day expand directly into episode links, without another season accordion. Search filters entries without changing the full-month summary. Only dated, non-future, passive individual observations are counted. Full History, earlier records, statistics, calendar and refresh remain accessible. Continue Watching stays on Home; the activity heatmap is not rendered on the diary page. Existing history is not deleted or rewritten.

## Discover
Square full-bleed service tiles retain selected outline/red dot and gain a readable selected-service/Clear row. Service selection scopes all curated shelves and uses region/provider checks for streaming picks; selected-service failures never fall back to global recommendations. Cache and in-flight identities include media type, region and provider. Old-scope content is hidden immediately during switching.

For Tonight has shorter cinematic cards and optional runtime metadata. Genres use small photographic tiles, selecting artwork only from matching known genres (neutral fallback otherwise). Trending and Worth Discovering remove titles already shown in earlier shelves. Community lists have compact triptych covers and separate 44dp bookmark actions. Global lists are not falsely presented as service-filtered: that section is shown in All services mode. Browse in the header opens the complete filterable catalog; list and shelf headers use View all. Existing search, filter/sort and list-detail actions remain.

## Verification
Managed workflow: 5dd038cba0ed9faa97cea6ec576ae8e0.

- "Do the monthly diary and provider-aware Discover compile with the existing navigation and metadata clients?" PASS (:app:compileDebugKotlin).
- "Do diary month boundaries/grouping, service-scoped discovery, deduplication and the redesigned screen interactions pass regression?" PASS after correcting two new test-only compilation errors. :app:testDebugUnitTest and :app:testPreviewUnitTest: 503 tests each, zero failures/errors/skips. Focused connectedDebugAndroidTest: 14 tests, zero failures/errors/skips. Covers month selection versus stats, in-place episode expansion, exact episode opening, genre selection, list bookmark versus open, provider generation isolation, shelf deduplication, original detail/Auto-add contracts and pure date/provider boundaries.

Rendered month selector, episode diary, Discover featured/genre/shelf hierarchy and compact list bookmark fixtures inspected. Artwork in screenshots is synthetic and local; production continues to use existing cached movie artwork. Official APK package/signature/cloud verification and downloadable checksum are recorded in the GitHub release notes after packaging. No database/schema or settings migration. Synthetic fixtures do not prove owner-phone rendering or live service availability. No live TorBox mutations or watched-state writes are used as tests. Contributor CI builds do not contain owner signing material or cloud configuration.
