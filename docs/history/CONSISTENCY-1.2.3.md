# Android 1.2.3 / code63 — consistency and usability

Owner-approved implementation of the full UI audit, plus narrower Continue Watching cards.

## Presentation and navigation

- Continue Watching cards use76% of the available width, capped at296dp, including one-item rails;84×64dp thumbnail, readable episode/time-left and read-only progress. Long-press menus remain available.
- Global Search uses its own model and consistent three-column posters, with a back action and44dp clear target. It no longer overwrites Discover's query/filters. Retapping Discover does not reload/reset it.
- Library/Jellyfin/TorBox/Auto-add are sibling destinations rather than accumulating a history stack.
- Lists reset scroll only on explicit category/list selection. Returning from Details restores the same visible poster. Descriptions expand; missing artwork has a neutral fallback.
- Title models are keyed by account/type/title, load once on reentry, and preserve season/episode state. Synopsis and selected season are saveable. Top source/trailer/watchlist/Auto-add actions are visually grouped.
- Underlined tabs identify pages; outlined segmented controls identify content filters. Selected semantics, larger management metadata and44dp controls improve readability/accessibility.
- Discover category choices are in a compact outlined dropdown, beside sorting. Streaming shortcuts remain available.
- Person/episode panels use the shared glass style. Filmography incomplete rows keep three equal columns. Shared alert dialogs scroll their content while retaining the footer actions.

## Tracking, server and accounts

- Watching starts with In progress, then viewing history, then summary/habits. No watched mutations were added.
- Charts retain Nothing dot styling, add date ticks and44dp previous/next day/month controls. The All-time totals remain all-time; their chart is explicitly labelled Last12months.
- Monthly calendar uses larger day labels, stronger today/selected treatment and up to three event dots, with an explanation.
- Settings → STAK Server includes Auto-add connection and library organization. Unified pairing and legacy credentials/protocols are retained. Background automation and visible-page status refresh are clearly distinguished.
- Source settings explain that Find Sources uses app sources while Auto-add uses server sources.
- Cloud account exposes Retry backup as well as optional reauthentication. Backup encryption, revision conflicts and restore rules are unchanged.

## Verification

470debug +470preview unit tests passed. Thirteen focused API35 emulator tests passed, including narrow-card geometry/next-card peek, large chart controls, fixed dialog footer/close, title reentry without duplicate metadata load, list return scroll, Home motion/reduced motion and list/source interactions. Rendered fixture captures reviewed. Additional management/passive-tracking checks are recorded below.

No physical-phone performance claim or live owner-service mutation testing. Server code/package, database schema and account/service credentials are unchanged. Install the signed APK as an update without uninstalling.

## Delivery verification

- 26 distinct API35 UI checks passed across focused runs:13 consistency/Home/lists checks plus13 Library/Auto-add/passive-tracking checks. One older ACTIVITY heading assertion was updated to the intentional DAILY ACTIVITY label and rerun successfully. No watched/acquisition protections were relaxed.
- Both full JVM suites:470 tests each,0 failures/errors.
- Optimized original-signer preview built successfully with build-update.ps1, including lintVital/R8/resource shrinking and existing public cloud deployment injection.
- Signed package installed with adb -r on the isolated API35 emulator and cold-launched successfully. Original application ID retained. No owner-phone or live-service verification is claimed.

Application ID: app.stak.nativeapp.parity

Version: 1.2.3 / 63

APK SHA-256: 867ed1a3049ad53bff622ac4508ec49c09f578318b8bbd9047df422ea179913f

Certificate SHA-256: bd9c33ebf4a4a62fc034cbb363635ed8d4d55f081ce43fcaff59706d2230c430
