# 1.2.3 Jellyfin episode filename compatibility

355 offline Linux tests passed in 24.12s (Python3.12 Alpine, network disabled). The 53 new regressions reproduce the owner-provided Leftovers S03 E01–E08 filenames and Jellyfin's false E05 ending from e5dbdc39. Official Jellyfin SxxEyy multi-episode regexes and ending-number guards are exercised with adapted named-group syntax. New bracketed, labeled version tags do not create a false range. True S03E01-E02 files remain combined.

Existing owned video/subtitle links migrate once, preserve all source bytes and stable folders, and do not change again on the second cycle. Collision refusal and interruption journal recovery are verified. Movie filenames are unchanged. All prior acquisition, approval/account isolation, read-only playback, migration and authentication tests pass.

The production Docker image stak-server-qa:1.2.3 was built and exercised without networking or owner credentials. A real fixture database containing two old e5-hash episode links upgraded to two correct bracketed names; original sources stayed intact and the second publish was unchanged. No new server API, schema, config variable or Android APK is required.

Initial QA invocations caught a missing project import path in the console pytest entrypoint and a staging version-file edit that omitted the protocol constant. The launcher was switched to python -m pytest and the original protocol constant retained before the full successful suite and production-image check.

Not verified: deployment on the owner's Linux laptop and its resulting Jellyfin metadata rescan. File-path changes may create new Jellyfin item ids, so take a current Jellyfin /config backup before updating. The STAK update script retains its online SQLite/WAL backups. Existing acquired media must not be removed/re-added and no watched-state writes are introduced.

Upstream references:
- https://github.com/jellyfin/jellyfin/blob/master/Emby.Naming/Common/NamingOptions.cs
- https://github.com/jellyfin/jellyfin/blob/master/Emby.Naming/TV/EpisodePathParser.cs
- https://jellyfin.org/docs/general/server/media/shows/

## Previous records

# 1.2.2 automatic folder organization

302 offline Linux tests passed in 19.51s. Tests include the owner-provided Common Side Effects, Yaga/UIndex, Nocturne, Carrie and IT release naming shapes; shared seasons across torrents; optional provider ids and release years; consistent year hints; unknown-category classification; underscore/spaced episode markers and explicit season parents; numeric movie titles; source preservation and existing acquisition/approval/account safety regressions.

The production Docker image was exercised with an isolated metadata catalog: two explicit approvals without media ids or category selection became two symlinks in one Yaga/Season 01 directory. No external network or owner credentials were available. Original files remained intact; graceful exit0.

A first test-launcher invocation omitted /app from its script import path. The launcher was corrected; the production entrypoint was unchanged and the real-image flow then passed.

Android 1.1.1/code59 is unchanged; this update is server-only. Owner 1.2.1 is already healthy with verified Jellyfin paths. Live owner 1.2.2 folder verification remains pending.

## Previous verification records

# 1.2.1 authentication compatibility patch

279 Linux tests passed in 19.65s, external network disabled. A local HTTP server rejects the deprecated token header with 401, accepts the supported MediaBrowser Authorization header, verifies both directory markers, and accepts an automatic scan only with supported authentication. Token/header injection cases are refused. TorBox Bearer authentication is unchanged.

Official compatibility note: https://jellyfin.org/posts/jellyfin-release-12.0/#http-api

Owner's 1.2.0 server is already migrated and reports six available Jellyfin episodes. Its library verification requests returned 401 using the deprecated header. No owner credentials were used in development tests; live 1.2.1 verification remains pending. Android APK is unchanged.

## Prior unified-server verification

# STAK Server 1.2.0 verification

The server bundles the tested watcher 1.1.4 core and legacy filter protocol, with in-process library approvals and a new organized publisher.
It does not require Sonarr/Radarr, an external database, a second STAK container, or another paid service.

Verified offline:

- Full Python watcher/filter/unified/migration suite in Linux Python 3.12 with external networking disabled.
- Films, common show/season folders, separate episode bundles, one-video multi-episode names, subtitles, sample exclusion, unicode filename bounds.
- Account isolation, original source integrity, revocation, no duplicate TorBox add, exact file mapping, collision refusal, interrupted layout recovery.
- Cached add -> in-process identity approval -> organized publication -> verified library status through injected services.
- Legacy database preparation while old services remain running; final snapshot after writers stop; preservation of post-cutover records on rollback.
- Complete private Jellyfin config backup, SQLite WAL backup, private output modes, no overwriting pre-existing settings.
- Production Docker image: public health, canonical-token library API, old-filter-token compatibility listener, successful graceful exit (0), restart.
- Android debug + preview unit suites: 453 tests each, zero failures. Signed Android 1.1.1/code59, original package/certificate, existing cloud config retained.

Final Python suite: **272 passed in 18.23s**. Android: **453 debug + 453 preview unit tests**, **5 relevant instrumentation tests passed** (the refined dialog layout also passed its targeted repeat). Artifact hashes are recorded in MANIFEST.json / the delivery status file.

The final production-image upgrade probe retained an existing TorBox id/hash, reset only its obsolete Jellyfin-derived id, became healthy and shut down with exit 0. Synthetic test containers/volumes were removed.

Emulator screenshots were visually inspected: Identify uses the existing Nothing styling, does not layer two dialogs, and action buttons wrap as whole buttons on narrow screens.

App APK: 1.1.1/code59, SHA256 fb395c072c3fd594d7e89b18f4895b2384b80f3c2fd352bf4de02b215c43342c.

Not verified: deployment on the owner's Linux laptop, live TorBox acquisition, real Jellyfin rescan/matching, original phone update installation,
or preservation of Jellyfin item ids after path migration. Emulator rendering is verified; the owner’s physical-device installation is not. No owner credentials were used in development tests.

The first Docker smoke test attempted shutdown before startup completed without Docker init; it was killed after its timeout.
The production configuration includes init, and the subsequent init-enabled health/compatibility/shutdown test exited cleanly with code 0.
