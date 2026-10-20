# Priority evolution pass — 2026-10-14

The user approved the evolution roadmap, then narrowed this pass to the most important fixes within approximately 30 minutes. This is a source repair candidate, not a completed implementation of every roadmap phase.

## Implemented in this pass

- Explicit bounded HTTP redirect handling, including Cinemeta HTTP 307 catalog responses. Public GET redirects can move to validated HTTPS origins; authenticated/query-key requests and mutation bodies cannot cross origins. HTTPS downgrade, loops, excessive redirects and unsafe mutation redirects are blocked.
- Concurrent public metadata deduplication and longer bounded metadata caching.
- Compatible Jellyfin MediaBrowser/token headers; verify the exact token/user pair before replacing a saved connection. Authentication and permission failures have specific recovery instructions. The screenshot alone cannot identify whether the session was expired, revoked, misconfigured or proxy-filtered.
- Reconnect without first disconnecting; preserve local lists/history on authentication failures. Library errors no longer also claim "No libraries found". Admin-only scan control.
- One encrypted device-level Jellyfin connection independent of TorBox key changes, with non-destructive migration of selected legacy connections. Multiple saved Jellyfin profiles and migration of TorBox-scoped watchlists are deferred.
- Authenticated artwork uses the same headers; redirects are guarded; connection changes invalidate private artwork.
- TMDb IDs resolve to IMDb IDs before Torrentio/indexer search. Missing mappings are errors, not successful empty lists. Original Torznab magnets/trackers are retained. Local custom indexers can be searched without TorBox credentials.
- Source errors are displayed individually. Clear distinction between failed sources, filtered results and a genuine provider response with no releases. No torrent availability is invented for unreleased/new titles.
- Restored episode selection, normalized episode numbers, episode deep links, Next Up, episode watched controls, and explicit episode/season-pack search modes.
- Accessible Radix source dialog with focus/escape handling and Android bottom-sheet styling.
- Corrected Progress sync undefined dependency; watchlist refresh updates persisted list data; guest cards do not show endless cache spinners.
- Corrected search type control, genre controls and filter preservation while typing.
- Corrected default/legacy style light-mode surfaces and light-mode focus contrast.
- All enabled Home sections now follow the saved order; no unconditional recently-added request when those rows are disabled. Home rows use account-scoped queries without remounting the entire dashboard on refresh.
- Reduced unnecessary context array changes; native foreground refresh no longer performs duplicate event-driven refresh; native back chooses the topmost dialog.
- Corrected active-transfer polling dependencies/visibility and partial bulk-operation feedback; retained failed selections for retry.
- Late bulk file links cannot cross torrent selections; poster fallback cannot loop on a broken fallback image.
- Credential memory changes only after native persistence succeeds; already-saved account keys are not written twice.
- GitHub Actions workflow added to attempt full lint/Jest/build/Capacitor/Android validation and export one source ZIP, containing an APK only if compilation succeeds.

## Deferred to future releases

Stable profiles for all local data; named/smart lists; virtualized collections; full shared persistent artwork/metadata database; complete customization system; full TMDb Discover pagination/collection screens; large-library Jellyfin identity indexing; synchronization outbox and complete unwatched reconciliation; audio/subtitle selection; native Media3 playback/media session/PiP; managed downloads; native notifications/background tasks/widgets/shortcuts; full TalkBack/device/performance certification; local bundled fonts.

## Verification limits

No live Jellyfin, TorBox, indexer, YouTube, emulator or physical-device tests were performed. The existing Android SDK/dependencies are unavailable locally and outbound shell connections fail with EPERM at the session proxy. Source structure and dependency-free adapter tests are available. GitHub Actions results must be checked separately and must not be inferred from workflow creation.

## Delivery

The final priority source archive was generated and fully extracted/byte-compared. GitHub publication did not complete during this pass; the remote branch and CI run were not confirmed updated. The included workflow can be run after these changes are pushed. No APK was compiled.
