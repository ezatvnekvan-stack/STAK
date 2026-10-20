# Tracking, calendar, Details and sources refinement

2026-10-20. Work continues on `stak-discover-202`, from validated `b775086`, descended from `v2.0.2-polish`. No 3.x/4.x source, package identity, signing configuration or release changes.

## Changes and acceptance

- Watched actions on movie/episode Details now open a date picker. A separate **Watched on release day** action uses a full known release date. Season marking uses the date of each selected episode and leaves already watched episodes unchanged. Unknown/future dates cannot invent a completed action. Watched dates remain separate from edit timestamps and survive v1 backups. Jellyfin receives its supported `DatePlayed` parameter; local state is retained if sync fails.
- Shared Jellyfin sync runs on connection/open, on foreground/online and at a bounded 60-second interval while the app is active. It coalesces requests and backs off failures to five minutes. All shared tracking, title-availability, Home Jellyfin and library queries are invalidated after success. It does not claim server-push or closed-app background monitoring.
- History defaults/migrates to **All time**, with **All / Movies / Series**, compact rows and one show row instead of separate watched episodes. Known series totals support a progress bar; missing totals show watched-episode counts without a fabricated percentage. History merges local and Jellyfin identities and retains local history on failure. Existing remote history loads up to the latest 300 Jellyfin watched items; All time removes the date filter but does not claim an unlimited viewing-event archive.
- Calendar is Following-only and Month-only, including migration of old Trending/Week/Agenda preferences. Today, month arrows and a single Filters action remain visible. Release type, optional artwork and week-start preferences are in a sheet. The default date grid is quiet; selected-day release cards remain actionable. The watchlist still supplies Following. Errors/retries and partial coverage are retained; no fabricated release times.
- Details uses a content-width Find sources button, larger 4:3 episode frames with three cards across phones, unboxed monochrome hero ratings and compact cast cards.
- Source filtering replaces the text field with visible HDR / Atmos / WEB-DL / REMUX badges, and only 4K / 1080p resolution buttons. Tapping a selected resolution clears it. Dolby Vision, HUN preference and size controls remain accessible in the options sheet. Quality properties also appear on release rows. Add uses an icon-only 44px plus/check/spinner button with real action/state/accessibility labels.
- Pack size uses a Torznab torrent total, TorBox cache total, or existing library torrent total. A selected episode size is never relabelled as the pack total or multiplied into a guess; unknown totals say **Pack size unavailable**.
- Home **New releases** retains the followed upcoming window and now includes movies with movie links and episodes with episode links.

## Verification

Regression coverage includes chosen-date persistence/Jellyfin submission/backup restore, invalid-date no-write, per-episode release dates, history deduplication/fallback/grouping, active-app refresh/pause, removed-calendar preference migration and filters, pack-total honesty, source badges and movie/episode navigation. Existing browser journeys are updated for the date dialog and intentionally removed controls. Local adapter/source/native configuration checks run before publication; React/browser/Gradle results must be read from the exact subsequent CI commit, not assumed from this document.

Physical Android installation, live private-account sync timing and native date-picker behavior require device testing. The previous development APK had a different signer from historical releases; no matching private signing key is present, so update compatibility must be checked again for any generated artifact. Do not uninstall or clear data as a workaround.
