# STAK Native 0.4 — the private screening room

The native application uses a new cinematic design system throughout its destinations. The React/Capacitor app remains unchanged. This is a design refresh of the existing native implementation, retaining its provider integrations, account isolation, storage and navigation.

## Visual language

Graphite/ink dark surfaces and warm-ivory light surfaces have distinct background, panel and elevated-control levels. Existing user accent preferences remain available. Hanken Grotesk supplies editorial headings and readable body text; Space Mono is reserved for small metadata, and Doto remains the STAK wordmark. Rounded artwork, generous panels, restrained borders and accent-filled primary actions establish the hierarchy. No new remote fonts or runtime design dependencies are introduced.

The dock retains its destinations and saved order, with an animated accent selection capsule and tactile icons. Page entry uses a short fade/lift, poster navigation retains the shared bounds transition, and buttons, switches and tabs retain native press/focus/haptic feedback. Reduced motion snaps these effects. Sheets use opaque surfaces, a quiet grabber and a consistent silhouette. Modal posters remain outside the navigation shared-element layout, preserving the Find sources crash fix.

## Destination inventory

| Destination / state | Refresh |
| --- | --- |
| Home / inline search | Editorial introduction, enlarged spotlight hero, framed search, artwork rails and result cards |
| Discover / services / filters | New page hierarchy, elevated search/selectors, filter chips, retained drafts, premium sheet controls and empty/error states |
| Movie / series details / trailers / seasons / episodes | Cinematic masthead, refined poster/typography, rounded episode and cast surfaces, source action dock |
| Find sources / release details | Opaque sheet, readable release cards, tactile filters/actions; normal-motion modal regression retained |
| Watchlist / filters / list layout | Collection summary metrics, artwork cards, readable filter surfaces and empty states |
| Tracking / activity / journal / progress | Editorial headings, legible viewing totals, journal surfaces and the common control hierarchy |
| Calendar / day details / watched dialogs | Matching surfaces, typography, calendar selection and modal treatment |
| Jellyfin library / setup / server tools | Connection summary panel, collection cards, native form and action treatments |
| TorBox library / selection / filters | Cloud summary metrics, clearer state groups, file/task cards and elevated controls |
| Magnet entry / torrent files / details | Opaque sheet surfaces and a consistent action hierarchy |
| Share / saved hash lists / list detail | Curated-list summary, contextual help panel, collection cards and share/add controls |
| Jellyfin remote | Now-playing heading, playback panel and tactile native control set |
| Person / biography / credits | Creator-profile heading, portrait-led layout and artwork credits |
| Settings index | Full-width destinations with distinct icon wells, readable hints and native touch targets |
| Account / metadata / sources / Jellyfin settings | Dedicated captions and framed form panels with shared fields, switches and actions |
| Appearance / theme / accent / motion | Existing real Rive control and working preferences within the refreshed preview/panels |
| Home customization / navigation / genre shelves | Framed form hierarchy and the same native reorder/toggle controls |
| Backups / restore / data / about | Dedicated page hierarchy, framed form surfaces and password/confirmation flows |
| Loading / empty / failure / missing title | Rounded artwork placeholders, framed status cards and consistent retry actions |

## Validation

The existing production test gates remain enabled, including Find sources with normal motion and Jellyfin password/API-key request regressions. Added checks cover text contrast, opaque modal surfaces, large-font headings, 48dp touch targets and functioning controls in both themes. The existing matrix supplies 88 captures across 22 routes, two viewport sizes and both themes. Screenshot differences from the old Capacitor design are expected under this explicit redesign direction; dimensions and behavior checks remain enforced.

This does not certify complete functional parity, live-provider behavior, physical-device performance or every accessibility scenario. Review the actual PR workflow reports and retained captures for the validated checkpoint. Debug signing continuity still requires an owner-controlled key; no signing keys or credentials are included in source or artifacts.
