# Tracking artwork 1.3.4 / code69
- Failed backdrop/episode-still requests now fall back to the poster on recent-history and month-review surfaces; blank URLs skipped, attempts bounded. Softer scrims keep images visible.
- Saved Jellyfin history borrows fresh artwork from the same-account matching current entry; viewing timestamps, identifiers and watched state are preserved. Missing artwork or other accounts cannot overwrite retained artwork.
- Episode posters use their known parent series even if SeriesPrimaryImageTag is absent.

Verification question: "Do tracking history artwork refresh tests pass and does the update build?"
PASS via managed gradle-run: :app:testDebugUnitTest --tests app.stak.nativeapp.AutomaticTrackingTest and :app:assemblePreview --offline. Signed export verified; update installed on emulator and cold launch succeeded. Live owner Jellyfin artwork has not been tested. Missing or deleted images on both endpoints still use a neutral placeholder. No backend change required.

APK SHA-256: dec619754bc89a6b5dfff145c9735df54f6e1217e7e3123271c834030c7bf2b2
Next versionCode: 70+.
