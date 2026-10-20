# STAK 2.1.0 — screenshot corrections

Android versionCode 23, package `app.dmmstudio.cinema`. Built on the complete v2 source. No credential, local data, backup format, provider configuration or playback-engine changes. Main and earlier releases remain untouched.

| Request | Change / affected source |
|---|---|
| Home scan styling | `JellyfinScan.jsx` reuses real server scan/progress/results with the existing triangular Jellyfin mark, restrained refresh animation and shared Nothing pill styling. No popup or fake progress. Administrator access still required. |
| Floating Home logo and TorBox status | `AppShell.jsx` renders branding/status only on Home. Transparent borderless header replaces the solid bar. Other destinations, including Details, have no app logo/header; their own headings/back actions remain. |
| Seamless Details / side color cutoff | `TitleAmbient.jsx` portals the single fixed artwork layer directly into the app shell, outside animated main and title stacking contexts. It covers the viewport during scrolling rather than the padded content rectangle. `index.css` masks both ends of hero artwork, removes its solid background/end cap and softens section surfaces. Synopsis uses spacing instead of an outlined card. |
| Remove bottom additional details | The bottom technical-facts grid is removed from `TitlePage.jsx`; gallery, cast, crew, synopsis, episode popup, ratings, tracked progress and all source actions remain. |
| Consistent trailer and Discovery buttons | `TrailerButton.jsx`, `TitleProgress.jsx`, `DiscoverPage.jsx` and shared `nothing-action` CSS use the same rounded secondary action geometry, type, neutral border and 44px minimum target. Active selections remain visibly filled; focus, loading, disabled and error states remain. Trailer functionality and provider fallback are unchanged. |
| Episode shapes and alignment | `EpisodePicker.jsx` has explicit thumbnail/title regions. Cards start their flex content at the top; thumbnails have a fixed 16:9 area and matching 12px rounding; long titles are limited to two equal-height lines. Real full titles/synopses remain available in the episode popup. Watched indicators, selection and episode source coordinates remain intact. |
| Compact service artwork | `DiscoverPage.jsx` uses one compact row of up to six square service tiles. Artwork fills each tile with `object-fit:cover`, matching the real square TMDb provider logos and avoiding rectangular letterboxing. Selected borders and accessible provider names remain; missing Paramount artwork stays hidden. |
| GitHub delivery | Dedicated `stak-v2.1` branch / Draft PR. Workflow publishes v2.1.0 APK, complete source ZIP and SHA256 checksums only after all verification gates pass, and refuses to overwrite a different existing tag/release. |

## Verification and practical limits

Local checks: 79 adapter regressions, 206 source/import checks, Android configuration/XML, browser-script syntax and diff checks. The matching Actions run performs dependency installation, ESLint, React regressions, strict production compilation, four production Chromium width/theme configurations, Capacitor sync, Gradle debug assembly, APK contents/signature and source ZIP extraction/byte comparisons. Read its exported `CI_TEST_RESULTS.md` and Draft PR for actual outcomes. No tests are silently skipped to publish an APK.

New browser assertions check full-viewport ambient geometry after scrolling with motion enabled, no global header outside Home, transparent borderless Home branding, no bottom facts grid, consistent episode thumbnail offsets/heights/radii across different title lengths, square full-tile service artwork, and shared secondary controls. All existing v2 search, trailer, source-selection, navigation and layout assertions remain. A React regression verifies the artwork portal's location and cleanup; Home scan coverage verifies the Jellyfin mark while retaining its real progress/action assertions.

No physical Android device, emulator, real credentialed providers or measured FPS validation has run. Browser providers are isolated test fixtures. Image metadata can be incomplete; embedded YouTube remains subject to provider/WebView restrictions. TorBox additions still require an external server import workflow to reach Jellyfin. Original v2 limitations are documented in `STAK_V2.md`.

The downloadable APK is a debug build. An in-place update requires the same signing certificate as the installed app; CI does not have the original signing key. Preserve backups and rebuild with that key for a safe update. Do not clear app data or uninstall to bypass signature errors.
