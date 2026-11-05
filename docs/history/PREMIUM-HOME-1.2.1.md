# Android1.2.1 / code61 — Premium Home, 2026-11-04

## Delivered behavior

- Compact Home header: expandable search, settings/connection indicator, no decorative Trakt/Jellyfin/TorBox labels. Search behavior and metadata setup remain available.
- Larger cinematic hero with fixed-canvas image dissolve, transition-only scale/parallax, independently animated title/logo and GPU/Canvas page indicators. Duplicate edge pages preserve the transition at last→first. Manual gestures and dot controls remain available. Autoplay is gated by lifecycle, visibility, vertical scrolling, dialogs and Reduced motion.
- Hero artwork taps are dispatched by the common canvas to the current title; transparent neighboring pager layers cannot capture them. The View details action also opens the current target; outgoing text actions are disabled during their exit.
- A singleton Continue Watching title fills the row; multiple titles have larger cards with a visible next-card edge. Context menus and passive progress remain intact.
- Two-up Coming Up is retained. Leading Trending movie has a wide #1 spotlight; the remaining titles keep their real ranks. Adjacent enabled anticipated sections share Movies/Series tabs. Non-adjacent/custom order and disabled sections are preserved without rewriting preferences.
- Find Sources is a prominent light primary button near the top of movie/series Details, before secondary actions. One-time saveable identity sweep, press feedback and reduced-motion cancellation. The bottom dock is removed. Existing episode source buttons and all cached-only acquisition/consent guards remain unchanged.

## Verification

- 470 debug + 470 preview JVM tests passed (four new section-presentation checks).
- Nine focused API35 emulator tests passed: hero dissolve/manual paging/cyclic wrap, correct title opened by both artwork tap and button, reduced-motion suppression, compact search, full-width continue geometry, paired anticipated type switch, top Details button geometry/source-sheet opening, read-only season retry/cancellation, public list save/open and collage, original shared artwork navigation.
- Captures inspected for Home top, Continue Watching, Trending spotlight, anticipated tabs, mid-transition/settled hero and top Details action. Captures use local synthetic artwork, not owner accounts. PixelCopy/root captures were used to synchronize renderer state with test assertions.
- A dedicated artwork-tap test caught the translated pager layer hit-region issue; dispatch moved to the common hero surface and the full focused suite passed afterward.
- build-update.ps1 produced the optimized, original-signer APK with the existing hosted cloud deployment configured. No Room schema, private key, account settings, watched mutation, source/cache guard or server change.

APK: distribution/STAK-current.apk
Version: 1.2.1 / 61
Application ID: app.stak.nativeapp.parity
SHA-256: 28a43996e7ca76b4c23ab59085887de9de98617f19062d205169e29e7d23d35f
Original certificate SHA-256: bd9c33ebf4a4a62fc034cbb363635ed8d4d55f081ce43fcaff59706d2230c430

The signed optimized APK also installed with adb -r on API35 and cold-launched successfully; the process remained alive with no AndroidRuntime crash. This is an emulator startup check, not an owner-phone upgrade or FPS measurement.

## Limits

No physical-device FPS, owner-phone upgrade, live TorBox action or live account restore claimed. Existing settings/history are preserved by the unchanged identity, signing and storage design; install as an update without uninstalling. STAK Server1.2.3's separately delivered episode fix remains unchanged and is not installed on the owner's Linux host by this Android build.
