# STAK 1.0.0

Android media browsing, tracking and library management with a Nothing-inspired interface, built with React 18 and Capacitor 7.

This baseline is the complete application at source commit `b433c7ca3c69c37208be3dd8e508e8d408ddee8f`, immediately before Cinema Explorer, Trailer Channel and Series Hub were introduced. No later industrial redesign or 3.x/4.x source is included.

## Included

- Home, Discover/Search, movie and series Details, title trailers, seasons, episodes, cast and ratings.
- Watchlist, Jellyfin library and watched-state synchronization, TorBox task/file management and source selection.
- Watched-date selection, release-day tracking, compact grouped history and a Following-only month calendar.
- Theme, accent, AMOLED, compact and reduced-motion preferences, account isolation and backups.

## Download

[STAK 1.0.0 release](https://github.com/ezatvnekvan-stack/STAK/releases/tag/v1.0.0) includes the original verified APK, complete source, checksums and public signing certificate. The APK is reused byte-for-byte from the successful pre-Cinema build, not rebuilt or relabelled internally.

- VersionName: **1.0.0**; versionCode: **38**.
- Application ID: **app.dmmstudio.cinema**; minimum Android SDK 24.
- Existing debug-signed release configuration retained. Updating an installed APK requires a matching signing certificate. Do not uninstall or clear user data to bypass a mismatch.

## Development

Application root: `frontend/`. See [BUILD_ANDROID.md](BUILD_ANDROID.md), [OPEN_SOURCE_UI.md](OPEN_SOURCE_UI.md) and [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). The existing GitHub Actions validation workflow is retained, with its manual default ref set to `main`.

The original build passed automated React, adapter, browser and APK validation. No new build, physical-device installation or live private-provider success is claimed for this repository migration. Historical delivery documents in the source describe their original dates and repositories; this README identifies the new baseline.
