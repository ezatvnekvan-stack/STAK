# 1.5.9 (code85) — Daily Home and episode cards

- Daily mixed TMDb trending hero, TRENDING TODAY heading, softer artwork fade and tighter shelf spacing.
- Continue Watching uses episode stills, compact clean wordmarks and bottom inline status/episode/time, retaining short progress without a percentage label.
- Coming Up becomes a wide date-led card: full width for one item, a next-card peek for several; existing rule-only data and date accuracy retained.
- Original signer/cloud settings, passive playback tracking and cached-only acquisition preserved. No server update.

# 1.5.8 replacement (code84) — Original Continue Watching restored

- Original 1.4 Continue Watching layout, with a short progress bar.
- Highest-resolution neutral movie/show backdrop with a separate logo; parent series artwork replaces episode stills.
- Replaces the existing v1.5.8 release APK. Home and other screens unchanged.

# 1.5.8 (code83) — Continue Watching Nothing panel

- A shared rounded outline joins 16:9 artwork and a matte information footer with a small play mark.
- Larger episode/time text, no repeated title or blur; short red segmented progress remains on the image.
- Existing English show artwork, half-next-card rail, long-press, reduced motion and data/signing/cloud guards retained. Home hero unchanged.

# 1.5.7 (code82) — Compact Home and Continue Watching

- Home hero uses approximately half the screen; retains seamless fade, textless high-resolution art, logo and facts.
- Continue Watching uses the show backdrop for episodes, with original-resolution English branded art preferred. Missing public identity resolves from the explicit Jellyfin parent with account guards.
- Episode/time information moves below the artwork without a repeated title. Lower blur removed; short progress stays on the image.
- Half-next-card rail, long-press, data/signing/cloud compatibility and the six 1.5.6 refinements remain. No server update.

# 1.5.6 (code81) — Six approved refinements

- Taller portrait Home hero without app header controls or synopsis.
- Continue Watching: original English branded artwork, half-next-card rail, local blurred information band and short progress.
- Taller Details image extending beneath centered metadata; unchanged action sizes.
- Larger single-frame source filters; recommended edge/marker without a text row, upper-right size and acknowledged plus-to-check action.
- Jellyfin Sort by replaces the main library chooser; libraries/collections remain inside filters.
- Compact single-line Watchlist totals. Original signing/cloud/data, Current/Glyph and passive/cached-only guards retained.

# 1.5.5 (code80) — Cinematic refinement

- Larger seamless Home hero; top-aligned high-resolution artwork and bounded transition depth.
- Independent textless artwork preference, original TMDb resolution, separate logos and image-failure fallback for Home/Details.
- Compact outline classification badges globally; centered genres and compact facts/IMDb on Details.
- Sources shares the other action outlines; quick filters are adjacent and use consistent marks. Cards omit duplicate quality headings.
- Movie library status drops redundant quality text, retaining Media Quality and passive playback state.
- Grid posters, restored Tracking/Discover, Current/Glyph, accounts and cached-only acquisition protections remain. No server update.

# 1.5.4 (code79) — Cinematic Home and Details

- Shared transparent outline Movies/Series/All controls across browsing surfaces, including Home anticipated shelves and Tracking.
- Edge-to-edge Home hero with overlay header, centered title branding and facts, compact synopsis and touch-driven cinematic dissolves.
- Larger Details artwork, centered metadata/IMDb, separate primary and secondary action rows, bounded parallax and season-entry motion.
- Shared compact off-white film-classification stamps replace outline age chips, preserving exact provider values.
- Current/Glyph backgrounds, original Tracking/Discover functionality, original signing/cloud settings and all acquisition/passive-tracking guards retained. No poster-provider or server changes.

# 1.5.3 (code78) — Optional Glyph background

- Settings → Appearance adds Current / Glyph. Existing installations keep Current.
- Glyph uses a matte black canvas, quiet broken graphite arcs/lines and short signals on navigation or confirmed source additions. No continuous loop; respects reduced motion, scrolling, modal surfaces and lifecycle.
- Details removes the Series Center shortcut while retaining Auto-add. Compact IMDb marks/scores are whiter.
- Dolby Vision and Atmos badges share the same typography, contrast and surfaces as other format badges, with a smaller Dolby mark.
- Original 1.4 Tracking and Discover pages remain restored. No server, acquisition, watched-state or database behavior changes.

# 1.5.2 (code77)

- Restore exact original 1.4.0 Tracking and Discover pages, including calendar, list cards and discovery behavior.
- Retain Following, source badges and Auto-add Coming Up.
- Increasing versionCode and original signer allow updating existing installations without uninstalling.

# 1.5.1 (code76)

- Month-selectable poster viewing diary replaces the Tracking hub heatmap and duplicate Continue Watching section.
- Same-day series episode groups expand in place; monthly statistics follow the selected month.
- Cinematic Discover genres, shorter featured cards, compact list bookmarks and deduplicated shelves.
- Service selection scopes curated requests, cache and streaming verification.

# 1.5.0 (code75)

- Simplified rule-only Following in Library.
- Compact Find Sources, Nothing format marks and explicit Add feedback.
- Coming Up from Auto-add series only.
- Twelve-week daily activity grid and cleaner monthly release calendar.
- Existing original signing, account isolation, passive tracking and cached-only protections retained.

# 1.4.0 (Android code70)

- Library Series tab replaces the Auto-add entry point with a unified series list: library titles and existing Auto-add rules, search, library/automation/attention filters.
- Season/episode availability view joins public metadata, read-only Jellyfin episodes and watcher states. Failed verification stays unknown; provider/series IDs drive merging, never title guesses.
- Episode quality/version selection and episode-specific Find Sources retain the existing cache-only confirmation flow.
- Existing Auto-add management, consent, pause/resume and named stop confirmation remain available; Details links to Series Center.
- No backend migration, manual watched writes, subtitle feature or unrelated visual redesign.

# 1.3.4 (Android code69)

- Tracking cards fall back from failed backdrop/still images to posters, with lighter artwork scrims.
- History uses fresh same-account artwork while preserving original viewing records.
- Episode poster selection uses the known parent series even without a SeriesPrimaryImageTag.

# 1.3.3 (Android code68)

- Smaller, muted IMDb rating matching the surrounding metadata; removed the vertical divider. Button spacing unchanged.

# 1.3.2 (Android code67) — 2026-11-04

- Movie/series metadata alignment and monochrome IMDb badge with a small static red dot, without /10.
- Separate Trailer and Watchlist buttons with 12dp spacing and narrow-screen wrapping.
- Regression, UI checks and signed optimized build verified; no backend changes.

# 1.3.1 (Android code65) — 2026-11-04

- IMDb-only ratings with exact Cinemeta fallback; labelled Trailer action.
- Compact landscape Continue Watching cards with aligned title, progress and episode metadata.
- Verified regional streaming For tonight picks sorted by popularity, with title logos; first Home Trending Movies logo enrichment.
- Progressive provider/metadata/cache checks and scoped short-lived search reuse. Fresh pre-add checks and original private torrents preserved.
- Fixed stale remaining-source count after verification failure. Backend1.2.3 unchanged.

# 1.3.0 (Android code64) — 2026-11-04

- Discover rebuilt as a compact editorial explore page with a top-rated selection, trending, genre/mood entry points, community collections and supported hidden-gem results. Search/filter actions switch to a dedicated three-column grid.
- No top-level Titles/Lists tabs. Public lists are reachable through Browse all lists; existing saved lists/ranking/scroll remain available.
- Movie/series Details rebuilt with a cinematic header, one compact source/trailer/watchlist row, neutral library status, read-only quality/progress and a separate Auto-add status row.
- Three-line expandable synopsis, real season dropdown and virtualized vertical episode rows, with read-only watched, next-up and resume presentation.
- Compact creator/director line, separate full credits, larger gallery carousel and related-title rows.
- Continue Watching width follows measured title/metadata text, with two-line title and viewport caps; narrow singletons retained.
- Continue episode selection is preserved during season restoration. Zero/invalid TMDb scores are omitted.
- Original signer, account/history/cloud settings, server compatibility and acquisition/read-only tracking protections preserved.

# 1.2.3 (Android code63) — 2026-11-04

- Narrow Continue Watching cards, including a singleton; next-card peek and compact thumbnail layout.
- Separate global-search state from Discover; consistent three-column results; stable tabs and return positions.
- Title-specific, account-scoped models preserve selected seasons; saveable synopsis and season state.
- Consistent filter pills, accessible selections/touch targets, clearer Discover category dropdown and metadata.
- Shared glass dialogs with a fixed action footer; unified episode/person panels and correct incomplete filmography rows.
- Watching content first, clearer chart dates and 44dp day/month navigation, accurate twelve-month chart label and stronger calendar cues.
- Central STAK Server settings with library management, explicit source-scope wording and clearer background Auto-add explanation.
- Cloud-backup retry without forcing reauthentication; retained conflict/isolation protections.
- Original update identity, signer, encrypted storage, cloud configuration, cached-only acquisition and passive tracking retained.

# 1.2.2 (Android code62) — 2026-11-04

- Smaller dark outlined Find Sources action at the top of Details.
- Smaller Home hero, no View details button; image tap and cinematic paging preserved.
- Compact thumbnail Continue Watching cards with episode/progress and context menu.
- Original update identity, signer, account/history, cloud deployment and source guards preserved.

# 1.2.1 (Android code61) — 2026-11-04

- Premium Home with compact expandable search, large cinematic hero, full-width singleton Continue Watching and a wide leading Trending movie.
- Hero dissolve/parallax with separate title/logo transitions and continuous last-to-first paging; lifecycle/visibility/dialog/reduced-motion gating retained.
- Adjacent enabled anticipated rows share Movies/Series tabs; custom section order and hidden rows remain intact.
- Decorative Trakt/Jellyfin/TorBox labels removed from Home; connection remains available through the settings indicator.
- Find Sources moved from the bottom footer into a highlighted top primary action with a one-time sweep and touch feedback.
- Existing source/cache guards, optional Auto-add, private accounts/history/cloud configuration and original signing identity preserved. STAK Server1.2.3 is unchanged.

# 1.2.0 (Android code60)

- Discover public Trakt Lists: popular, trending, search, local favorites, ranked movie/show grids, progressive artwork and pagination.
- Core Details opens independently of seasons and ratings; selected seasons load and retry on demand. Full tracking metadata remains separate.
- Progressive Home/Trakt rows, public metadata caching, bounded artwork caches and smaller gallery/cast/episode thumbnails.
- Faster shared touch/navigation motion, matching search results retained during typing, compact floating Find Sources action.
- Original application identity, signer, cloud configuration and read-only Jellyfin tracking preserved. No server update required.

# Current release — 1.0.2

- Username/password STAK accounts with encrypted automatic cloud backup and restore.
- Nothing motion system: artwork shared transitions, staged Details, animated metrics/dot graphs, calendar movement, navigation reactions and red bookmark confirmation.
- Reduced-motion support and lifecycle-aware loading motion.

Android versionCode49, package app.stak.nativeapp.parity. Current APK only is in distribution/; earlier releases and Git history are omitted from this handoff.
