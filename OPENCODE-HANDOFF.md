# OpenCode handoff

Continue this repository: **STAK Android 1.5.8 / code84**, **STAK Server 1.2.3**. The next distributable Android update must use code85 or greater.

Start with [README](README.md), [contributor setup](CONTRIBUTING.md), [agent rules](AGENTS.md), [architecture](docs/ARCHITECTURE.md) and [Series Center notes](docs/RELEASE-1.5.5.md).

All current application source, tests, resources, unified server/cloud deployment source and project-local skills are included. No SDK, cache, private signing key, completed service configuration or owner database is included.

For ordinary development, configure JDK17 and Android SDK35 and run the Gradle wrapper. Cloud configuration is optional; copy the example locally only if needed. For official updates, recover the original private signing key and existing cloud configuration from a trusted owner backup; do not generate replacements and claim update compatibility. Never uninstall the owner's application as a shortcut around a signing mismatch.

Following is Library's rule-only Auto-add entry point; library browsing remains on Jellyfin. Existing explicit-consent cached-only automation remains intact. Jellyfin tracking remains read-only. Subtitle acquisition is not implemented in this release. No Linux server update is required specifically for the Android Series Center feature.

Historical rollout/verification notes are in `docs/history/`. Treat older phase ledgers as history, not current incomplete task instructions.

Current changes: docs/RELEASE-1.5.8.md. Original 1.4.0 Tracking/Discover remain restored. Optional Current/Glyph background, compact white IMDb and uniform Dolby badges; Details keeps Auto-add without a Series Center shortcut. No Terminal mode.

1.5.4 implements the four owner-approved visual refinements. Shared MediaTypeSelector and CertificationBadge are the single sources for these visual rules. Home/Details alone draw under the status bar and reserve their own insets. Do not reintroduce the rejected Tracking diary/Discover redesign or add a poster provider.

1.5.5 supersedes the filled classification stamps, hero geometry/cutoff and three-line Details facts of 1.5.4. See the eight approved steps in the current release note. No new image provider is required; CinematicArtwork chooses existing TMDb alternatives while preserving grid art.

1.5.6 implements the six approved Home/Continue Watching/Details/Sources/Library/Watchlist changes in docs/RELEASE-1.5.6.md. The English backdrop preference belongs only to Continue Watching. Its artwork enrichment never changes the Jellyfin playback row.

1.5.7 supersedes the 1.5.6 tall Home hero and blurred Continue Watching information band. Home is about half-screen; Continue info is below the image, without a separate title. Parent-series artwork/identity replaces episode-still fallback. See current release notes.
