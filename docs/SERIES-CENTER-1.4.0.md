# Series Center — STAK 1.4.0 / code70

Library > Series replaces the Auto-add navigation entry. Includes Jellyfin series and saved Auto-add rules (not every watchlisted title). Search and All / Library / Auto-add / Attention filters. Library titles do not automatically create rules. Details has a Series center action; direct navigation preserves Back to the title.

Opening a series reads metadata and the paginated Jellyfin episode list independently, then combines episodes with the existing watcher rows. Select a season; expand an episode for availability, actual known quality, available versions and episode-specific source search. Future episodes have no source-search action. Unresolved metadata identities offer the title page instead of an invalid search. Auto-add management uses the existing rule editor and management dialog, including consent, pause/resume, settings and named stop confirmation. Turning it off retains media/history.

Availability is separate from watched status. No watched writes, playback mutation, background acquisition or automatic rule creation. A failed/partial Jellyfin read cannot mark episodes missing; unknown release dates remain unknown. Watcher-only availability is explicitly reported, not claimed as a current library verification. Existing library episodes take precedence over stale watcher states. Read requests retain account guards and cancellation; source additions retain original cache-only account-bound guards. Metadata/provider IDs drive grouping; equal display names do not merge series.

Presentation uses existing Doto/Hanken/Space Mono typography, understated outlines and NothingExpand with reduced-motion support. Lazy lists and stable keys; episode work only starts for the opened series. App-owned wiring is separated from the pure SeriesCenterList and SeriesCenterDetail composables for isolated UI fixtures.

Backend remains STAK Server 1.2.3. Existing URLs/tokens are retained; no Linux update is required. Subtitles are not part of this release.

## Verification

Managed gradle-run question: "Do Series Center identity, availability and UI navigation checks pass?" Final answer: PASS. Tasks :app:testDebugUnitTest and :app:connectedDebugAndroidTest (SeriesCenterUiTest and TitleHeaderUiTest). The initial UI fixture assertions targeted mixed-case text instead of the existing uppercase control labels; corrected to test the real labels.

Managed gradle-run question: "Does the final Series Center pass regression, version-selection and existing Auto-add UI tests and produce the signed-update variant?" Answer: PASS. Tasks :app:testDebugUnitTest :app:testPreviewUnitTest :app:connectedDebugAndroidTest (SeriesCenterUiTest, TitleHeaderUiTest, AutoAddOverviewUiTest) :app:assemblePreview --offline.

Counts [tests, failures, errors]: {"debug": [489, 0, 0], "preview": [489, 0, 0], "device": [11, 0, 0]}

Rendered list, episode availability, larger-text unverified state and version-picker fixtures were inspected. Official package and certificate were verified by build-update.ps1 -ExportOnly. Optimized APK updated the emulator in place and cold-launched without AndroidRuntime errors. Owner-phone rendering and real Jellyfin/watcher/source requests are not proved by these isolated fixtures. No live acquisition, stop/delete or watched-state mutation was performed.

APK SHA-256: d6f9ecd096a662f882fccd10ca42977c2c7d71983fdd4ba0300a0e58f1a46524
Certificate SHA-256: bd9c33ebf4a4a62fc034cbb363635ed8d4d55f081ce43fcaff59706d2230c430
Next Android code: 71+.

