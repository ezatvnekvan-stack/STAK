First **fully native Android alpha** of STAK, built with Kotlin and Jetpack Compose. No Capacitor, WebView UI, JavaScript bundle, or companion web server.

This is the start of the native migration, not a feature-complete replacement for STAK 1.0. The existing app and stable release are preserved.

### Motion

- Connected poster-to-details transitions with reverse navigation.
- Retargetable navigation/segment indicator springs and subtle press compression.
- Draggable bottom sheets; static equivalents when reduced motion is enabled.
- Watched check drawing, real segmented progress, and request-driven refresh dots.
- Optional Android touch/success haptics.
- App/system reduced-motion support, lifecycle-aware status animation, and Navigation Compose predictive-Back integration.

### Available in this alpha

- Home, Cinemeta movie/series browsing and search, pagination, title details, seasons and episodes.
- Room-persisted Watchlist, watched journal, local episode marks, and a calendar following watchlisted series.
- Jellyfin library/resume cards and watched-state actions; playback opens Jellyfin externally.
- TorBox task progress, file lists, and external file playback/download links.
- Keystore-encrypted connection credentials; credential-free backup export and merge import of existing STAK `dmm-backup` v1 JSON lists/episode marks.
- Resizable Android Watchlist widget.

### Install

Download **STAK-Native-0.1.0-alpha.apk**. Requires Android 8.0 or newer. Package: `app.stak.nativeapp`; label: **STAK Native**.

Installs alongside the original app. Export a plain JSON backup from your existing STAK, then import it in Native → Settings → Backups. Re-enter provider credentials. Native exports have their own format; keep the original backup too.

This evaluation APK is **debug signed**, not a Play Store production build. The original app's signing key is not available here. Do not uninstall the original app or clear its data to resolve signing issues. A future stable release needs a persistent owner-controlled signing key.

### Remaining migration work

Advanced TMDb/provider filters, cast/person pages, source/indexer aggregation, TorBox creation/deletion, Jellyfin remote controls, account switching/isolation, full playback tracking, automatic background sync, accent/AMOLED/compact preferences, and Continue/Tonight widgets remain to be ported. Jellyfin library loading is currently limited to 300 titles. Episode marks are local; marking a series watched does not mark every episode.

### Validation

See **VALIDATION.md** and the linked GitHub Actions run for the actual build, lint, unit-test and emulator results. Screenshots labeled fixture use an isolated test title and do not establish live private-provider success. No physical-device, measured 90/120 FPS, or live credentialed Jellyfin/TorBox result is claimed.

STAK is independent of Nothing and is not an official Nothing product.
