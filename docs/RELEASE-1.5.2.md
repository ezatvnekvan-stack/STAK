# STAK 1.5.2 — Restore original 1.4 Tracking and Discover

Android versionCode 77; next distributable code78+. This is an in-place update, not an APK downgrade.

At the owner's request, the following production files are restored exactly from the published 1.4.0 commit b86505dfb4e611afda11c446cd99b230e01016ff:

- ui/screens/TrackingScreen.kt
- ui/screens/CalendarScreen.kt
- ui/screens/WatchingComponents.kt
- ui/screens/DiscoverScreen.kt
- ui/screens/DiscoverDeckViewModel.kt
- ui/screens/DiscoveryControls.kt
- ui/screens/ListsScreen.kt
- data/StreamingPicks.kt

The original Discover fixture file is also restored. The 1.5.1 monthly diary files and their specific tests are removed. Tracking returns to Watching/Calendar with original progress, recent history, Month in Review, habits and detailed statistics. Discover returns to the original featured/mood/trending/collections layout, controls, list cards and request behavior.

Following, Find Sources format badges/actions and Auto-add-only Coming Up from 1.5.0 are retained. No server, Room schema, credentials, cloud account, settings migration, acquisition guards or watched-state write behavior changes. Existing history is not deleted. Install with the original signer and increasing code77; do not uninstall.

## Verification
Exact-source comparison against the 1.4.0 commit has passed for all eight production files and the original Discover fixture. Managed test/build results and package checks are recorded in the release notes after verification. No live owner TorBox/Jellyfin mutations are used as tests; isolated emulator tests do not prove owner-phone integration.

Managed workflow: 8ef8269e9e3de4e0a651243dbe178421. Question: "Do the exact 1.4 Tracking and Discover pages pass read-only history/calendar, discovery, Following and source regression checks?" PASS: 498 debug + 498 preview JVM tests and 17 device checks, zero failures/errors/skips. The retired newer calendar API fixture was removed after its compile error; original calendar/read-only tracking fixtures cover the restored behavior. Original calendar/history and Discover fixtures rendered for review. Official package checks and build result are recorded in GitHub release notes.
