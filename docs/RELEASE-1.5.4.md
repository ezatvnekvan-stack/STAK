# STAK 1.5.4 — Four approved cinematic refinements

Android versionCode 79; next distributable code80+. This is an in-place update with the original package/signing identity. No uninstall or Linux update is required.

## 1. Shared outline media-type controls

MediaTypeSelector is used by Home anticipated content and Tonight, Discover (including its shared header), Watchlist, Jellyfin Library, Watching and history. Equal-sized transparent targets have a fine off-white active outline, 12dp corners, shared Space Mono labels, 46dp minimum height and short 160ms ink/outline transitions. Inactive borders remain transparent without shifting layout. Page-navigation tabs such as Jellyfin/TorBox/Following and Watching/Calendar retain underlines because they navigate between pages. Existing dropdown inputs remain inputs, not a new navigation system.

## 2. Home cinematic hero

The hero reaches both screen edges and draws under the status bar. The header floats over a darkened upper portion of the image; search still opens the existing search interface with safe insets. Artwork dissolves toward the background. Title/logo, compact facts, certification and up to two synopsis lines are centered. Height targets 44% of a phone screen with bounded large-text/landscape/tablet behavior. It has no Details button; the hero opens the currently visible title. Touch follows the pager; pictures dissolve with small depth movement and text enters separately. Duplicate end pages preserve circular navigation. Autoplay remains paused when hidden, scrolling, modal, backgrounded or Reduced motion. Shared transitions stay attached only to artwork. Shelf gutters and section customization remain intact.

## 3. Details

Movies and series share a larger image stage (roughly 48% screen height with bounds), centered logo/title and centered metadata below. IMDb keeps its compact off-white mark/score with no /10. Certification is a shared stamp. Find Sources is a modest outlined full-width primary action; Trailer and Watchlist/Saved are separate equal-width outline actions below, stacking at very large text or small widths. Existing callbacks, explicit acquisition flow and trailer presentation are retained. Auto-add stays series-only, Series Center shortcut remains absent, library/media-quality states remain truthful. Synopsis, cast, credits, galleries, related titles, season selection and episode details remain accessible. Parallax is bounded and read only in the artwork layer; one-shot title/controls/season-entry motion respects Reduced motion. Loading/error retries and selected-season source searches retain existing behavior.

## 4. Classification stamps

CertificationBadge is a single native text-rendered off-white stamp with dark bold lettering and small squared corners, rather than an outline input chip. Home, Details and certification filter values/menus share the same component; PG-13, R, TV-MA and other labels preserve the provider value. Missing ratings stay absent. No external image or logo download is added. Filter forms retain their editable controls, with the same stamp inside. Selected age filters have an accessible clear action.

## Preserved scope

Current remains the default background; opt-in Glyph and its backup-compatible setting are retained. Tracking/Discover keep their restored 1.4 content and data flows, with only the approved media-type selector and classification-stamp call sites changed. No new diary/heatmap layout, Terminal UI, BetterPosters, backend integration, account recreation, Room migration or watched-state write. Existing cached-only checks, explicit consent, account scopes, mutation reservations and unknown-outcome handling remain intact.

## Verification

Managed workflow b030541b5ef25e04c77799d32de59894. Questions: "Does the approved cinematic layout compile with the existing Compose stack?" and "Do the four approved UI changes preserve navigation, media filtering, passive tracking and acquisition guards?" Final compile and validation passed after correcting the FlowRow alignment/imports and updating stale geometry/button-label fixture expectations.

- 504 debug + 504 preview JVM tests passed, with no failures/errors/skips; final signed-build regression is recorded in the release notes.
- 38 broad focused emulator tests passed; the final classification follow-up added a successful new select/clear case and three Discovery filter regression cases. Across these runs, 42 distinct device cases passed. The two older Discover tests were corrected to use the actual "Discovery filters" accessible name; the season fixture now scrolls through its LazyColumn rather than looking up an uncomposed offscreen row.
- Rendered Home, movie/series Details, season 2, large-text outline selection, metadata/actions and classification stamps were reviewed. Fixture images are local synthetic artwork; real public artwork is checked separately in the optimized-app smoke run where available.
- Source/private-path checks passed. Production data repositories, Room and server files are unchanged. Current/Glyph behavior and passive tracking/acquisition fixtures pass.

The managed workflow is finished after the official export and deletes only its own temporary logs. Official package identity, signer, configured-cloud input retention and release-asset checksum are recorded in the GitHub release notes. Tests use isolated local artwork/catalogs; they do not prove owner-phone performance or live TorBox/Jellyfin connectivity. No live owner-service mutations are used for validation.
