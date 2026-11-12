# Current v1.5.10 replacement / code87

Owner follow-up: remove the Trending Today label from the Home hero. Its 55% screen height, daily feed, artwork/fade and all Continue Watching/Coming Up changes remain. The existing v1.5.10 GitHub release is replaced with a code87 APK for in-place updates. The code86 notes below document the original release and its earlier validation.

Replacement verification: managed gradle_run.py workflow `4f4e464345f83232595ce2613d365c88`, designated `gradlew-local.cmd`, question "Eltűnt a Trending Today felirat, és elkészült az aláírt APK?" — PASS. Tasks `:app:connectedDebugAndroidTest` (one focused hero fixture) and `:app:assemblePreview` passed. The replacement screenshot `outputs/home1510-verification/replacement87/hero-and-continue.png` was inspected. Original signer/package and exact existing cloud inputs were verified inside the signed APK. Unchanged data/card behavior reuses the previous results. Workflow finished; only wrapper-owned temporary logs removed.

Current APK: versionName `1.5.10`, versionCode `87`, SHA-256 `7674300439d80e6cfd7bbeb6f36c7964fcc5a58195a46e510b5cea12a6b6948b`.

# Android 1.5.10 / code86 — Home artwork and timeline refinement

Three owner-approved refinements to Home. STAK Server stays 1.2.3; no server update is needed. Original package and signing identity preserve in-place updates.

## Hero

Home retains the mixed daily TMDb feed and original-resolution textless artwork. The hero grows from 50% to 55% of screen height, with existing bounded font-scale and landscape allowances. TRENDING TODAY keeps its 11sp size, changes to Doto and aligns with the 16dp shelf gutter. A 22dp inset below the status-bar area and the existing soft top shade keep it readable, without a dot, chip, counter or separate header. The long lower fade and 6dp shelf gap remain.

## Continue Watching

Small real title logos replace typographic titles, left-aligned within a 164dp by 24dp maximum area. Missing or failed logos fall back to readable text. English logos are preferred, then neutral variants; within each group horizontal variants are preferred over stacked lockups, then source resolution. Dimensions cannot reliably identify all embedded HBO/Netflix/studio labels, so branding-free artwork is not guaranteed.

Series retain the actual episode still. When public series identity is missing, only its explicit Jellyfin parent can supply verified show metadata for logo selection, through the existing account-scoped read. Neither the parent backdrop nor its playback row replaces the episode. Visible-card enrichment remains lazy and bounded to three requests. Bottom NEXT UP/IN PROGRESS, episode/time, long-press and short segmented progress remain; there is no numeric percentage.

## Coming Up

Every item uses a compact width matching the Continue Watching rail sizing, including a singleton. A small timeline marker and day/date sit above the artwork; the next card peeks when present. The 16dp timeline inset and 140dp compact artwork leave room for a small logo, episode number and episode title directly over a continuous soft shade. There is no separate lower band or notification bell. Large-font height allowance remains bounded. Only explicit Auto-add rules supply events; dates do not imply an invented airtime or cached availability.

## Verification

Managed `gradle_run.py` workflow `df54647c475d4b3da1ee0cc9594329cd`, using the designated private `gradlew-local.cmd` entrypoint and existing private cloud inputs without printing their values.

"Do the Home artwork identity and geometry regressions pass, and does the signed 1.5.10 APK build?" — PASS. `:app:testDebugUnitTest`, `:app:testPreviewUnitTest`, `:app:assemblePreview`: 528 debug and 528 preview JVM tests, zero failures/errors/skips.

"Do the final hero alignment, small logos, compact timeline and large-font UI fixtures pass with the optimized signed APK?" — PASS after correcting the fixture's missing image import and its obsolete text-title expectation. `:app:connectedDebugAndroidTest` covered ten distinct cases: nine passed in the selected run; the updated small-logo/progress/navigation case then passed alone. `:app:assemblePreview` rebuilt the final optimized signed APK. The width-only final logo adjustment does not change the already-tested data/geometry behavior.

Rendered local fixtures were inspected and retained under the local `outputs/home1510-verification/screens/`: `hero-and-continue.png`, `continue-small-logo.png`, `continue-restored-logo.png`, `coming-up-single.png`, `coming-up-next-peek.png`, `coming-up-date-logo.png`, `coming-up-large-font.png`, `coming-up-logo-fallback.png`, `continue-large-text.png`, `continue-landscapes.png`. Assertions cover actual logo pixels, gutter alignment, date-above-art geometry, singleton compact width, next-card peek, scrolling, navigation, fallback text and absence of percentages. These are controlled emulator fixtures, not physical-phone or live provider evidence.

Original package/certificate and exact existing cloud configuration inside signed DEX were verified. `adb install -r` succeeded, cold launch returned `Status: ok`, the process remained alive and the crash buffer was empty. No live acquisition, deletion or watched mutation was performed. Source-tree and whitespace checks passed. Existing Android Gradle plugin/compileSdk warning remains; dependencies were not changed. The workflow was finished and only its wrapper-owned temporary logs were removed.

Artifact: package `app.stak.nativeapp.parity`, versionName `1.5.10`, versionCode `86`.

APK SHA-256: `72a6b3eccf7a04c169795232fa3000893c3156f988c38939b009e448055fe526`.

Original certificate SHA-256: `bd9c33ebf4a4a62fc034cbb363635ed8d4d55f081ce43fcaff59706d2230c430`.
