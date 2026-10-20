# Current completion pass — 2026-10-15

See EXISTING_FEATURES_COMPLETION.md for the current 24-group inventory, source-to-repair mapping, all four connected dormant components and outstanding live/device checks. TEST_RESULTS.md records actual CI results. The original checklist below is retained as historical evidence; its older “not run” statements do not override newer verified results.

---

# Feature inventory and verification checklist

Legend: **Fixture-tested** = relevant actual module code passed the isolated tests; **Source/config checked** = implementation inspected/structural configuration verified; **Pending live/UI** = no installed React app/device/provider verification. All features require successful production and native builds before deployment. A row marked implemented is not a claim of live success.

| Feature | Implemented behavior / repair | Evidence and remaining checks |
|---|---|---|
| Startup / recovery | Native standalone selection, safe credentials bootstrap, root and screen boundaries, Settings recovery, stale SW removal | Source checked; actual startup/device tests pending |
| Appearance crash | Home genre objects converted to names; discovery filter names normalized | Structure/adapter checks; React crash regressions added, not run |
| Theme / accent / style | Dark/light/system, color, all existing styles and persistence connected | Fixture-tested setters; visual/system-theme tests pending |
| Animations / layout / AMOLED | Stored flags and effective CSS attributes | Fixture-tested state; touch/visual behavior pending |
| Rating visibility | Per-source controls and badges/blocks honor settings | Fixture-tested preference merge; UI rendering pending |
| TorBox login | Validate real user response, wait for credential save, errors do not overwrite the previous account | Malformed/error fixture tests; real key pending |
| TorBox persistence recovery | Invalid JSON/lists safe, namespace isolation, account query retirement | Fixture-tested adapter/state logic; React/account device tests pending |
| TorBox list/status/progress/files | Successful-envelope/list normalization and periodic library refresh | Fixture-tested normalization; native/live refresh/file IDs pending |
| Magnet addition | Validated v1/base32 hashes, multipart create request, failed input retained | Fixture-tested request; live addition pending |
| Torrent-file addition | 5 MiB picker, fixed HTTPS native multipart upload, no credential logging/redirect | Native plugin/source checked; compile/live upload pending |
| Cache checks | Deduplicate/normalize and chunk hashes; supported list envelopes | Fixture-tested; current provider variants pending |
| Control / deletion / reannounce | Supported control API, individual failures and confirmation UI | Source checked; live destructive operation tests not performed |
| Downloads / ZIP / streaming links | Real HTTPS requestdl URL; link state reset per torrent; browser/system opening | Source checked; live links/expiry/player behavior pending |
| TorBox watchlist-related functionality | Device-local account watchlists, not an invented TorBox watchlist endpoint | Fixture-tested persistence; React UI pending |
| Jellyfin LAN HTTP / HTTPS | Android XML + LAN-only application validator, system trust, no mixed content | Config/URL tests passed; actual 192.168.0.5 connection not tested |
| Jellyfin username/password | Official AuthenticateByName and client authorization header; password discarded | Fixture-tested route/contract; server auth pending |
| Jellyfin API key | Public info/users test, user selection and secret storage | Source checked; server permissions pending |
| Server switching/disconnect | Credentials scoped, fields invalidated on URL changes, explicit disconnect | Fixture-tested disconnect/privacy; device/profile tests pending |
| Libraries / movies / series | Views, filtered Movie/Series pages, load more, cards navigate to detail | Pagination fixture-tested; UI/large library pending |
| Seasons / episodes / metadata | Series episodes, season/unwatched filters, detail text and provider IDs | Source checked; up to 1000 episodes, live completeness pending |
| Posters / backdrops / logos | Token header + native binary artwork component, type fallbacks; TMDb logos | Source checked; authenticated WebView imagery and logo availability pending |
| Image caching / memory | Lazy observer, max 4 artwork requests, dedupe, max 75 cached images | Source checked; image cache/account/device memory tests pending |
| Continue Watching / Next Up | Direct resume and NextUp APIs, linked queue | Source checked; actual progress/permissions pending |
| Recently Added / available / surprise | Direct Jellyfin catalog rows and title navigation | Source checked; real library/UI pending |
| Watch history | Episode → series ID resolution; latest Jellyfin sample or local manual history | Fixture-tested local history; live mapping pending; not a full history import |
| Watched/unwatched | Local-first marking, real PlayedItems POST/DELETE, partial sync failures | Fixture-tested local preservation; live bidirectional changes pending |
| Tracking sync | Stores remote played/resume records and last-sync stats | Source checked; externally un-watched removals/full pagination still incomplete |
| Resume / remote playback | Uses saved PlaybackPositionTicks; Unpause/Pause/Stop/NextTrack/PreviousTrack | Fixture-tested resume/command; client compatibility pending |
| TMDb Discover | Trending/day/week, popular/top/new/hidden, genre/year/rating/language/provider filters | ID/date/sort fixtures; live keys/provider filters/UI pending |
| Movie/TV search | Cinemeta search and IMDb lookup, metadata fallback, local page sorting/filtering | Source checked; catalog/provider availability pending |
| Movie/TV details | IMDb and tmdb-prefixed routes, numeric provider ID preserved | Resolve/normalization fixtures; real titles/UI pending |
| Cast/crew / actor pages | Credits/filmography and restored person route | Source checked; live metadata pending |
| Recommendations / similar | Genuine TMDb result lists | Source checked; live results pending |
| Collections | Genuine belongs_to_collection objects, collection parts | Source checked; limited popular movie-page discovery, no invented series collections |
| Seasons / metadata logos | TMDb season poster lookup and corrected logo field | Source checked; real images pending |
| IMDb / RT / Metacritic | Legitimate OMDb and Cinemeta values; TMDb labeled separately | Attribution fixtures; real OMDb limits/scores pending |
| Episode ratings | Shows only supplied scores and explains absent ratings | Source checked; no external episode-rating enrichment promised |
| Trailers | Valid YouTube keys, embed, load/error/timeout, retry/close/back, external fallback | Source checked; provider blocking/fullscreen/referrer/device pending |
| Torrentio | Direct native Android HTTP; valid parsed torrent hashes | Source/fixtures; live addon availability pending |
| Stremio indexers | Manifest configuration/test and torrent stream results | Failure-isolation fixture; arbitrary URL-only video addons not supported |
| Torznab | Capabilities test, movie/tv/season/episode requests, XML metadata | Parser fixtures; real Prowlarr/Jackett/private trackers pending |
| Hash / magnet parsing / dedupe | Hex/base32 normalization, XML entities, seeders/peers and magnet attributes | Fixture-tested; v2-only/url-only torrents require more support/manual upload |
| Search filtering/sorting/cache sending | Quality/text/size/cache filters, merged sources, real TorBox create operation | Source checked and adapter fixtures; live search/UI pending |
| Home dashboard | Search, queues, preferences, local genre personalization, visible retry errors | Source checked; full visual interaction pending |
| Watchlist / guest use | Local lists, compatible dates, sorting/filtering and no mandatory TorBox login | Fixture-tested persistence/date handling; UI pending |
| History / Tracking / Progress | Manual local history; direct Jellyfin sample; existing progress views | Local-history fixture; React views/full remote sync pending |
| Upcoming/calendar | Followed/trending metadata aggregation, four-worker concurrency, partial failures | Calendar fixture; schedules/time zones/provider accuracy pending |
| Media Toolkit | Real provider stats and remote controls; one failing request doesn't imply success | Source checked; real provider/device diagnostics pending |
| Duplicate management | Detect identical hashes, review deletion, keep a preferred copy | Source checked; no real account deletion performed |
| Sharing | Native magnet-text export, no fake public local links | Source checked; cross-device/native share sheet pending |
| Backup/export/import | Whitelisted JSON, credentials/URLs excluded, merge missing records | Fixture-tested; native sharing/large file/device import pending |
| Navigation | Restored missing routes, fallback route, dialog/keyboard/fullscreen/root back behavior | Structure checked; actual back/gesture/device UX pending |
| Lifecycle / API efficiency | Foreground invalidation, explicit timeouts/retries, 100-entry metadata cache | Transport fixtures; network/device profiling pending |
| Security/privacy | Keystore native vault, migration, no logs/secrets in package, CSP, disabled backup | Config/source reviewed; crypto/migration device tests and npm audit pending |
| Production build / APK | Android project and complete build instructions included | Build attempted but blocked; no compiled APK |
| Notifications/webhooks/cloud | Browser-while-open calendar alerts, local alternatives | Android background notifications/hosted webhook/share/cloud infrastructure unavailable |

The checklist is intentionally explicit about incomplete/native/unverified areas. Refer to TEST_RESULTS.md for exact limitations and BUILD_ANDROID.md for the release gate.
