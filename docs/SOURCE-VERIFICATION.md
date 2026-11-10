# Public source verification — 2026-11-05

Prepared from the verified STAK Android 1.4.0 / code70 private development snapshot. All 342 Android source, resource and test files were checked byte-for-byte against that snapshot; application behavior was not edited during repository cleanup.

## Source-only build

Verification question: **"Does the clean public checkout pass JVM tests and build without private signing or cloud files?"**

Answer: **PASS** through the project-local managed `gradle-run` wrapper with `:app:testDebugUnitTest :app:testPreviewUnitTest :app:assemblePreview --offline`.

- Debug JVM: 489 tests, zero failures/errors.
- Preview JVM: 489 tests, zero failures/errors.
- Optimized contributor preview APK built successfully with no private signing directory or completed cloud configuration in the checkout.
- Existing compileSdk/AGP, deprecated icon and static-condition warnings remain; this cleanup did not alter application code to hide them.

## Unified server

**355 tests passed** in a Python 3.12 Alpine Linux container using a read-only source mount, offline provider fixtures and pytest 8.x. No real TorBox, Jellyfin or owner server was mutated. Native Windows execution is not a supported replacement for the Linux filesystem/permissions/local-HTTP fixture environment; the initial Windows probe failed those environment-sensitive cases, so the supported Linux run was used for verification. CI also runs this suite on Linux.

## Repository checks

- Signing files, owner cloud configuration, completed environment files, runtime databases/Torznab configuration, APKs, archives, caches and local assistant state are excluded from the source tree.
- Tracked-file checks reject generated/private paths, selected private-token/key patterns, NUL-filled source text and invalid UTF-8. Exact owner deployment values were separately checked for accidental inclusion without logging their contents.
- Active documentation links were checked. Historical release notes are separated under `docs/history`; current instructions are in README, CONTRIBUTING, AGENTS and the documentation index.
- Project-local skills and their upstream license/provenance records are retained.
- Public Windows build helpers tolerate absent private cloud/signing files for contributor builds. The previously corrupted Unix update script was replaced and syntax checked; official export still requires and validates the original key and package identity. The export scripts' full native Linux/macOS signing flow was not executed here.

The official release APK is the previously verified owner-signed 1.4.0 artifact, not the contributor build produced by this checkout. Real-device/live-service claims remain limited to the original release verification notes. GitHub Actions status is separate from these local results.

## 1.5.0 update
Based on b86505d (published 1.4.0); contains only the four approved UI/data-presentation refinements. Read RELEASE-1.5.0.md for validation and limitations. Private signing/configuration, generated APKs and local data remain excluded from the source tree.
