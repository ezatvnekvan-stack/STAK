# Android1.2.2 / code62 — compact Home/Details, 2026-11-04

The owner's follow-up replaces the large white action and oversized Home cards:

- Find Sources remains near the top of Details, with a dark translucent fill, fine outline, muted glyphs, 176–216dp width, 48dp minimum touch height and a very subtle one-time sweep. No fixed bottom dock.
- Home hero height is now screenWidth*.64 clamped to232–278dp, down from344–420dp. Logo area56dp, smaller title and spacing. View details button removed; the complete hero remains tappable. Dissolve/parallax, correct current-title taps, cyclic wrap and reduced-motion handling are retained.
- Continue Watching is an approximately88–100dp horizontal card with a100x68dp thumbnail, title, episode/time-left and read-only progress. Singleton fills the row without a large cover; multiple cards show a next-card edge. Long-press menu remains available.
- No metadata, source-acquisition/cache consent, Auto-add, account/history/storage, server or signing changes.

Verified: 470 debug + 470 preview unit tests; nine focused API35 emulator UI checks. Existing geometry checks now enforce the smaller hero, compact action and low Continue card. Source-sheet opening, selected-title taps, hero wrap, reduced motion, lists and season retry/cancellation remain covered. Updated Home/Details root captures inspected with local fixture artwork. Physical-device rendering/FPS is not claimed.

Original-signer optimized preview APK built with build-update.ps1; existing hosted cloud deployment configured.
Application ID: app.stak.nativeapp.parity
Version: 1.2.2 / 62
SHA-256: 939ce9bd7b319c2530915f1ea771c8a8e77b8faab18e5888d6b3279e15533186
Certificate SHA-256: bd9c33ebf4a4a62fc034cbb363635ed8d4d55f081ce43fcaff59706d2230c430

Install distribution/STAK-current.apk as an update. Do not uninstall or clear data. STAK Server1.2.3 is unchanged and independent of this Android presentation update.

Signed optimized APK installed with adb -r and cold-launched on API35; process remained alive and no AndroidRuntime crash appeared. No owner-phone or physical FPS measurement claimed.
