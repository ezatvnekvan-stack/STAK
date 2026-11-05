# Details header 1.3.2 / code67

Narrow update to the restored 1.3.1 interface. Movies and series share a metadata block: year/runtime/certification and genres on the left, IMDb score on the right when space allows. IMDb uses a monochrome mark, a static 3dp red dot and no /10. Narrow screens and larger text stack the metadata safely.

Trailer and Watchlist have a visible 12dp gap. Trailer moves below the other actions when width or font size requires it. Actions and callbacks remain unchanged. No other page redesign, backend update, tracking or acquisition changes.

## Verification

Managed gradle-run question: "Do the approved header, IMDb badge and non-touching Trailer/Watchlist controls pass regression and UI checks?"
Answer: PASS. Tasks: :app:testDebugUnitTest :app:testPreviewUnitTest :app:connectedDebugAndroidTest (TitleHeaderUiTest, RemakeUiTest, RefinementUiTest).

Managed gradle-run question: "Does the approved header update produce the optimized 1.3.2 preview APK?"
Answer: PASS. Task: :app:assemblePreview --offline. Exported using build-update.ps1 -ExportOnly, which verifies package and original signing identity.

Counts [tests, failures, errors]: {"debug": [478, 0, 0], "preview": [478, 0, 0], "device": [19, 0, 0]}

Screenshots inspected for movie metadata/actions, narrow layout at 1.4 font scale, and full movie Details. Emulator update install succeeded without uninstalling; optimized APK cold launch succeeded with no AndroidRuntime error. Real phone layout and owner data have not been inspected. Production backend unchanged at 1.2.3.

APK: distribution/STAK-current.apk
SHA-256: ff537dab19216942c081044dc86eb70cd57bddadbe4ab9756a98a3b50d25e72b
Signer: bd9c33ebf4a4a62fc034cbb363635ed8d4d55f081ce43fcaff59706d2230c430
Next Android versionCode: 68 or greater.
