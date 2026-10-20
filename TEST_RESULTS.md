# STAK v2.4.1 English-only metadata validation

New regression coverage checks normalization of legacy Hungarian/other metadata preferences without losing source/rating options; English enforcement at the TMDb request boundary even when a caller supplies another language; English then neutral logo preference with no foreign-language fallback; and removal of the Metadata sources language chooser. Production Chromium checks assert that every observed TMDb metadata request uses English, alongside existing persistence, UI and integration-action coverage.

Local checks passed: 97/97 dependency-free adapter regressions, 233/233 source/import checks, browser script syntax, Android configuration/XML/native-plugin checks and `git diff --check`. Complete dependency installation, ESLint, React tests, strict production build, Chromium smoke tests, Capacitor synchronization and Gradle APK compilation run in the matching GitHub Actions workflow. `CI_TEST_RESULTS.md` in the generated source archive records actual outcomes. Release publication is gated on successful validation. Final delivery links the confirmed run; do not infer a pass from this description alone.

The CI export compares the new debug APK's public signing certificate with v2.4.0 and records the result in `validation/signing-compatibility.json`. A changed runner debug signer may prevent in-place installation. No original private signing key is present; preserve installed data/backups and do not uninstall to bypass a mismatch.

No live-provider, device/emulator, native secure-storage or real Android WebView success is claimed. Intercepted fixtures are test-only. English translations/artwork still depend on what the selected provider actually supplies.

--- Previous validation record ---

# STAK v2.4 metadata validation

Local checks performed after excluding TVDB:
- Adapter regressions cover canonical identity resolution, selected-source search and dedupe, movie/series source selection, fallback, scoped credentials/preferences, OMDb quota handling, TTL expiry, four-request concurrency and migration of withdrawn provider preferences.
- Source structure/local-import and Android configuration checks are run locally; actual counts/outcomes are in the delivery report and CI logs.

Full lint, React tests, production web/browser validation, Capacitor synchronization and Gradle APK compilation run in GitHub Actions. `CI_TEST_RESULTS.md` in the generated archive records actual outcomes. The release job refuses publication unless validation succeeds.

Fixtures are test-only API responses; no provider credentials are included. Live TMDb/OMDb/Cinemeta compatibility, local Jellyfin connectivity, native Keystore round trips and physical Android/WebView behavior remain unverified until tested with real accounts/devices. Browser tests exercise actual IndexedDB persistence across reloads with intercepted provider requests. There is no TVDB implementation or related live test claim in this version.

See METADATA_SOURCES.md for source/cache behavior and build instructions. See validation/signing-compatibility.json in CI exports for measured APK update-signing compatibility.

--- Previous validation record ---

# STAK v2.3 verification

Local: 83 adapter regressions, 215 source/import checks and Android configuration checks passed. Full React/lint/browser/Capacitor/Gradle outcomes are recorded for the exact delivered commit in the exported CI_TEST_RESULTS.md and linked Actions run. Signing compatibility is checked separately against v2.2.0; it must not be inferred from package identity. No physical-device or live-provider testing is claimed.

# STAK 2.2 verification

First full run 38802096349 passed ESLint, all 31 React suites / 112 tests, 82 adapter tests, 215 source checks and strict production compilation. Expanded browser checks reached the sharing page after the primary UI/Discover/episode/Settings/TorBox checks, then blocked publication because the assertion expected a hosted-share input that is intentionally absent in standalone mode. It now verifies the real local-list heading/empty state and absence of the hosted input. No application capability or verification gate was removed. A later run also passed all 112 React tests but caught Escape being sent during the rapid drawer-to-magnet-dialog transition. The browser sequence now waits for drawer detachment and focuses the magnet field before asserting Escape dismissal; a shared-dialog focused-input regression is added. The subsequent matching run remains authoritative.

Local: 82 adapter regressions, 215 source/import checks, Android configuration/XML, browser syntax and diff checks pass. Existing regression suites remain; new React/browser checks cover the unified UI, Discover/provider/filter/search contracts, navigation continuity, compact episode actions and watched/undo progress updates. Actual npm/lint/React/production/browser/Capacitor/Gradle/APK/ZIP outcomes come from the matching workflow and exported CI_TEST_RESULTS.md. No live-provider, device, emulator or measured-FPS success is claimed. The matching workflow also compares the verified APK public signing certificates against v2.1.0 and exports the actual compatibility result. Full scope/limits: STAK_UI_SYSTEM.md. Earlier sections are historical.

# STAK 2.1 verification

First v2.1 run 38788283485 passed lint, all 28 React suites / 104 tests, adapters, source checks and strict production compilation. Its new browser artwork assertion correctly blocked publication because the existing fixture contained neither poster nor backdrop, so no ambient artwork could render. The fixture now includes real-schema test-only artwork URLs, leaving the full-viewport/scroll assertions intact. The subsequent matching run remains authoritative.

Local: 79 adapter regressions, 206 source/import checks, Android configuration/XML, browser script syntax and diff checks pass. New React/browser regressions cover screenshot defects while retaining the existing tests. The matching Actions run, exported CI_TEST_RESULTS.md and final Draft PR record actual npm/React/browser/Capacitor/Gradle/APK/ZIP outcomes. No physical-device or live-provider success is claimed. See STAK_V2_1.md; earlier sections are historical.

# STAK 2.0 verification

The first complete v2 workflow (run 38776535788) passed dependency installation, ESLint, strict production compilation, all 79 adapter tests, 204 source checks and all four Chromium layouts. Publication correctly stopped at the React test gate: three assertions needed updating for the merged Activity route, optional request configuration and a source fixture name that collided with the new cached-release heading. These tests retain their original retry, input and cache-refresh behavior checks; no test gate was disabled. The matching subsequent workflow and exported CI_TEST_RESULTS.md determine final build results.

Local: 79/79 adapter tests, 204/204 source/import checks, Android configuration, browser-script syntax and diff checks pass. Full React/browser/Gradle outcomes require the matching v2 Actions run; its exported CI_TEST_RESULTS.md and final PR metadata are authoritative. Tests and browser assertions were updated to the explicitly requested removed/replaced UI contracts while retaining workflow coverage. No live-provider or physical-device success is claimed. See STAK_V2.md. Earlier sections are historical.

# STAK v1 verification

The first full v1 run correctly blocked publication: browser/layout, production compilation, lint and all 74 adapter tests passed, while two React checks exposed an undefined test environment flag enabling a query and a missing wait for React Query observer notification in the new Undo assertion. The catalog enable flag is now explicitly boolean and UI state assertions await actual observer updates; no lint/test gate was disabled. The later matching run remains authoritative.

Local: 75/75 dependency-free adapter tests, 199/199 source/import structure checks, Android configuration and `git diff --check` pass. New React UI regressions are added; full dependency install, ESLint, Jest, production build/browser, Capacitor and Gradle results must be read from the matching workflow and exported CI_TEST_RESULTS.md. No physical-device, emulator, live-provider or measured FPS success is claimed. Detailed coverage and acceptance targets: [STAK_V1.md](STAK_V1.md). Historical results below apply to their named revisions.

# STAK 14.6 verification

Request coverage, data preservation and test scope: STAK_UI_UPDATE.md. Existing tests remain and new gesture/inline scan/episode regressions are added. The matching GitHub Actions run and CI_TEST_RESULTS.md are authoritative for actual results; no live-provider/device test is claimed. Historical results follow below.

# Android 14.5 verification

New coverage and runtime limits are documented in DISCOVER_LIBRARY_SCAN.md. Local adapter/source/Android checks run before publication; the matching Actions run and generated CI_TEST_RESULTS.md are authoritative for actual React/Gradle/browser checks. Prior results below are historical; no live-provider/device success is claimed.

The first full 14.4 CI run confirmed all 21 Jest suites / 80 tests and 63 adapter tests, then correctly rejected three ESLint warnings during the strict production build (operator grouping and an unused import). Those source warnings were corrected without weakening lint. The final matching run remains required for APK delivery.

# Android 14.4 validation

Current implementation and verification scope: SMOOTH_LIBRARY_ACTIVITY.md. Local source/import, adapter and Android checks are run before publication. Dependency install is blocked locally (proxy connect EPERM; offline ENOTCACHED), so no local React/Gradle success is claimed. The matching GitHub Actions run and generated CI_TEST_RESULTS.md are authoritative for actual build/test outcomes; final PR metadata links the confirmed run/artifact. No real-device, live-provider, instant-loading or measured 90/120-FPS claim. Historical results below belong to their indicated revisions.

# 14.3 validation scope

Local: 62 adapter regressions pass. Full Actions outcomes are generated into CI_TEST_RESULTS.md in the exported source/APK artifact. The first full run compiled the APK but its test gate caught trailer messages being ignored because the iframe had not yet mounted through the dialog portal. The player listener now attaches to the actual mounted frame, preserving both origin and source checks. Test gates run before Android packaging to surface regressions earlier. The current PR will record actual lint/Jest/production/browser/Capacitor/Gradle/signature/ZIP results after completion. See NOTHING_MENUS_TRAILERS.md for this update and its test coverage. No device/emulator/live-provider success is claimed; embedded playback is tested with isolated fixtures and still depends on YouTube/WebView permission.

Historical validation below refers to earlier source revisions.

# Nothing redesign validation

Local verification: 58/58 dependency-free adapter regressions passed. 177/177 JavaScript/JSX structural/import checks and Android configuration checks passed before publication. The restricted local shell cannot install dependencies or run Gradle; GitHub Actions performs the full build. CI has already confirmed 18 Jest suites / 73 React tests, 58 adapter tests, ESLint and production compilation on revision 66f59a5; the current-head run additionally verifies accessible Discovery controls and all four production-browser layouts before final delivery. Its generated `CI_TEST_RESULTS.md` records installation, ESLint, Jest, adapters, production React, Chromium, sync and APK outcomes for the exported artifact. Final delivery reports only confirmed results from the matching commit. A later run successfully compiled/verified the APK and passed all four Chromium layouts, but its final gate caught an intermittent rapid-preference observer test. The regression now explicitly waits for and asserts the newest optimistic UI while the older network save is pending, and again after both writes settle; no assertion or product functionality was removed. The final current-head run must pass before delivery.

New tests cover cross-movie/TV exact and year relevance; parent-series identity including specials; neutral/negative Jellyfin availability; selected-episode playback; true user library counts; legacy Jellyfin route redirect, unmatched manual search and retry; preserved source pack/episode contracts; provider catalog and trending filter semantics; safe Nothing style migration. Existing regressions remain. Chromium production-browser tests use isolated intercepted fixtures and do not authenticate to real services.

Live provider, physical Android, emulator, visual quality and measured performance tests have not run. See [NOTHING_REDESIGN.md](NOTHING_REDESIGN.md) for the complete request checklist and limitations.
