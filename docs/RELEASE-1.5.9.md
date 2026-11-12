# Android 1.5.9 / code85 — Daily Home and episode cards

Three owner-approved Home changes. Existing installations update in place with the original package and signer. STAK Server remains 1.2.3; no Linux update is required.

## Home hero

The hero reads `/trending/all/day`, retains TMDb's mixed movie/TV order, removes people and duplicate typed identities, and takes the first six titles. It no longer forces three weekly movies and three weekly series. Other Home shelves and Discover retain their existing sources. Missing TMDb configuration or a failed request does not fabricate a daily list.

TRENDING TODAY appears over a restrained top shade with no visible numeric counter or new header buttons. The half-screen image uses a longer alpha fade into the actual Current/Glyph background and a 6dp gap before the next shelf. Page indicators, original-resolution textless hero artwork, accessibility position announcements, title navigation and reduced motion remain. An open Home refreshes when its observed local date changes.

## Continue Watching

Series cards use their Jellyfin episode still, with the explicit series backdrop only as an image-error fallback. No parent metadata request replaces an episode picture. Own Jellyfin image resize parameters are removed while image tags are retained. Movies retain neutral original-resolution backdrop enrichment.

Large upper status chips and the numeric percentage are removed. Compact bottom information reads NEXT UP · S01 E01, or IN PROGRESS with episode/time when known. Short red segmented progress, original card dimensions, next-card peek and long-press behavior remain.

TMDb logo records do not reliably identify embedded HBO/Netflix/studio branding. Continue Watching therefore uses a small clean typographic title, rather than claiming unverified image logos are studio-free. Hero/Details logos are unchanged.

## Coming Up

A wide outlined card combines a left-hand day/date block, artwork fading behind it, a small title/logo and separate episode/name information. Today alone gets the restrained accent dot. Existing metadata supplies dates only; no clock time is invented.

One upcoming title fills the rail. Multiple titles use 86% width with a glimpse of the next, and horizontal scrolling. Larger font settings increase card height and the date column. The older sampled glass countdown chip is removed. Coming Up still uses explicit Auto-add rules and does not equate an air date with Jellyfin/TorBox availability.

## Verification

Managed `gradle_run.py` workflow `e4dd38dd800ffe8c0d58b9a029f4b0ea`, documented private `gradlew-local.cmd` entry point. Existing private cloud inputs were supplied without emitting their values.

"Do daily trending, episode artwork and compact Home information pass the JVM regression suite?" — PASS. Final debug JVM suite: 526 tests, zero failures/errors/skips.

"Do the three corrected semantics fixtures, final preview JVM suite and optimized signed APK pass?" — PASS. Final preview JVM suite: 526 tests, zero failures/errors/skips. Seventeen distinct emulator cases were verified: 14 passed in the full selected run; three needed updated text/unmerged-semantics fixture queries and then passed in the focused rerun. Production behavior did not change for those fixture corrections. Cases cover the daily header, spacing, compact statuses, small clean titles, single/multiple Coming Up widths, large fonts, navigation, hero wrapping, source controls and preserved library/watchlist contracts.

Rendered local-fixture captures were inspected, including hero/Continue composition and the single-item Coming Up card. Retained copies are in the local `outputs/home159-verification/screens` folder, including `coming-up-large-font.png`, `coming-up-date-logo.png`, `continue-restored-logo.png`, and `continue-large-text.png`. Connected output is replaced by targeted reruns. These are synthetic fixtures, not live owner-service or physical-phone performance evidence.

Owner's final percentage-only correction was verified in workflow `4c0581c465ee3066eaafa19d7b41b53c`: "Does Continue Watching omit percentages while retaining short progress, compact geometry and the signed release build?" — PASS. Three affected normal/large-font UI cases passed, the optimized signed APK was rebuilt, and the final rendered card has no percentage. The earlier 526/526 JVM results are reused for unchanged data behavior. Both workflows are finished and their wrapper-owned logs removed.

The optimized APK passed original-signer verification, retains the exact existing cloud configuration (verified inside the signed DEX without printing values), installed with `adb install -r`, and cold-launched successfully. Wrapper finish removed only wrapper-owned temporary logs after the build daemon released its Windows file handle. Existing Android Gradle plugin/compileSdk warning remains; no dependency update is included.

Artifact: package `app.stak.nativeapp.parity`, versionName `1.5.9`, versionCode `85`.

APK SHA-256: `39e4e992e507ac7370c7fa1ddff49f9a8e5dfc94fedbea3b0104d95642901a2a`.

Original certificate SHA-256: `bd9c33ebf4a4a62fc034cbb363635ed8d4d55f081ce43fcaff59706d2230c430`.
