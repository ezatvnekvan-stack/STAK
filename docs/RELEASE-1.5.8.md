# Android 1.5.8 / code83 — Continue Watching Nothing panel

Only Continue Watching presentation changes. Original-resolution English branded movie/show artwork, explicit parent-series identity, account-scoped lazy loading, original playback rows and half-next-card rail remain unchanged.

A single 16dp rounded outline encloses the 16:9 artwork and a solid matte information footer. The footer has a decorative white Lucide play mark, 11sp Space Mono episode/time information and no repeated title. Its 52dp minimum grows for large text. The short red segmented progress bar remains on the artwork. No blur, status badge, percentage or overflow button is introduced. The existing card action opens Details, with long-press actions preserved; the decorative play mark does not introduce playback control.

The panel uses the app's existing StakSurface2/StakText colors, restrained outline, shared progress animation and reduced-motion-aware press/image transitions. Home hero, Details, other shelves, data/acquisition behavior, schema4, signing and cloud configuration are unchanged. No backend update is needed.

## Verification

Managed `gradle_run.py` workflow `28bc7ffad7ac23d86644cb6d881b80e5`, with designated `gradlew-local.cmd` and `--no-daemon`.

"Megfelelő a Nothing panel és elkészül az APK?" — PASS: `:app:connectedDebugAndroidTest` (Cinematic156UiTest and PremiumHomeUiTest), 11 cases with no failures/errors/skips, plus `:app:assemblePreview`. Regular/large-text rendered panels were inspected. Existing artwork selection and data code are unchanged; no broad JVM repeat was needed for this presentation-only change.

Original signing certificate and package were verified by `build-update.ps1 -ExportOnly`; original cloud build inputs were verified present without displaying them. In-place emulator installation and cold launch passed. Workflow finish succeeded and removed only wrapper-owned temporary logs.

Fixtures use synthetic local content; they do not establish live artwork availability or owner-phone performance.


APK SHA-256: `3e7bf46a536eb465a38b4c4305f086a53e9edb1b94b47fecdd022641288128a0`. Package `app.stak.nativeapp.parity`, versionCode `83`, original certificate `bd9c33ebf4a4a62fc034cbb363635ed8d4d55f081ce43fcaff59706d2230c430`.
