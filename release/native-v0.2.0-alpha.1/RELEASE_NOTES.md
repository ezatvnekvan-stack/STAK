A Kotlin / Jetpack Compose native preview of STAK with a Nothing-inspired interface. No Capacitor or WebView UI. This is an evaluation alpha; it does not yet cover every original STAK feature.

### UI and motion

- Original Doto/Hanken Grotesk/Space Mono fonts, dot texture, dark/light themes, accent controls and native Rive theme artwork.
- Connected poster-to-title transitions and reverse navigation; tactile posters, buttons and circular controls.
- Gliding navigation and segmented selections, coordinated label colours, animated switches and hero indicators, focus feedback and artwork fades.
- Dot-grip native sheets, real progress indicators and app/system reduced-motion support.
- Full-width selectable account email, compact account stats and a more spacious source setup panel.

### Functional additions since the first native alpha

- Inline Home search with IMDb identity, ranking, paging and clearing.
- Direct TMDb browsing/details, person pages, native calendar and appearance controls.
- Verified TorBox account details, an honest Home connection badge, masked/revealable key entry and sign-out. A rejected replacement key preserves the working account.
- Encrypted, account-scoped Torznab/Stremio indexers: add, test, enable and remove. Search combines enabled providers and retains successful results when another provider fails.
- TorBox add/control/file actions, saved magnet lists and Jellyfin remote controls.

### Install

Download **STAK-Native-0.2.0-alpha.1.apk**. Android 8.0+; package `app.stak.nativeapp`, versionCode 2.

This APK is debug signed for evaluation. Its signing certificate differs from the first Native alpha, so Android cannot update that alpha in place. Existing Native alpha users should export and retain their Native JSON backup before arranging a fresh Native installation. Keep the original STAK app and its data. The original React/Capacitor package (`app.dmmstudio.cinema`) installs separately and remains intact.

### Remaining work

This is not a complete replacement: advanced Home/taskbar personalization, every Discover/source filter and availability state, Jellyfin hierarchy/server tools/full synchronization, account isolation for all viewing data, encrypted backup coverage and device/accessibility validation remain unfinished. See the source’s `native-android/parity/README.md`. Local episode marks do not imply full Jellyfin episode synchronization. Do not assume feature equivalence from the screenshot set.

### Validation

See **VALIDATION.md** for the exact build and emulator checkpoint. Screenshot and motion evidence uses test-only provider fixtures and cannot establish live private-provider correctness, pixel equality or physical-device frame rates.

STAK is independent of Nothing and is not an official Nothing product.
