# OpenCode handoff

Continue this repository: **STAK Android 1.5.2 / code77**, **STAK Server 1.2.3**. The next distributable Android update must use code78 or greater.

Start with [README](README.md), [contributor setup](CONTRIBUTING.md), [agent rules](AGENTS.md), [architecture](docs/ARCHITECTURE.md) and [Series Center notes](docs/RELEASE-1.5.2.md).

All current application source, tests, resources, unified server/cloud deployment source and project-local skills are included. No SDK, cache, private signing key, completed service configuration or owner database is included.

For ordinary development, configure JDK17 and Android SDK35 and run the Gradle wrapper. Cloud configuration is optional; copy the example locally only if needed. For official updates, recover the original private signing key and existing cloud configuration from a trusted owner backup; do not generate replacements and claim update compatibility. Never uninstall the owner's application as a shortcut around a signing mismatch.

Following is Library's rule-only Auto-add entry point; library browsing remains on Jellyfin. Existing explicit-consent cached-only automation remains intact. Jellyfin tracking remains read-only. Subtitle acquisition is not implemented in this release. No Linux server update is required specifically for the Android Series Center feature.

Historical rollout/verification notes are in `docs/history/`. Treat older phase ledgers as history, not current incomplete task instructions.

Current changes: docs/RELEASE-1.5.2.md. Original 1.4.0 source plus the four approved refinements; no Terminal mode.
