# Auto-add UX update — 1.1.0 / code 57

Implemented in the active Desktop/STAK-OpenCode project on 2026-11-03.

## Navigation and presentation

- Library now has Jellyfin, TorBox and Auto-add tabs. The separate Series bottom
  navigation entry is retired; existing navigation preferences map it to Library.
- Auto-add has search, All / In progress / Needs attention filters and compact
  series rows. Details show episode states, last/next checks and conservative
  pipeline markers. Raw server messages and credential-bearing URLs are not shown.
- Existing local library/watchlist artwork is reused by stable identifiers. New
  rules retain Details artwork. Missing artwork has a local placeholder.
- Details exposes Auto-add / Active / Paused / Pending sync / Stop pending on the
  entry button. State signals, existing tab transitions, staged reveals and
  expandable episode/quality panels reuse the reduced-motion-aware Nothing UI.
- Pairing stays in Settings → Auto-add. Rule editing stays on series Details.
- Quality defaults can be saved explicitly for future series; they never enable
  automation or change existing rules. HDR, DV and Atmos are editable and visible
  under advanced options. Empty season selection means all seasons, including
  future seasons; specials remain an independent opt-in.

## Correctness changes

- Rule operations accept either a stable series key or the actual rule id.
- A failed stop keeps a visible pending-stop rule. A refresh is read-only and can
  acknowledge an already-applied stop, but never replays mutations on restoration.
- Offline edits lose their previous server confirmation. A GET cannot confirm a
  different quality payload merely because the rule id matches.
- Pause cannot accidentally acknowledge an unsent quality edit.
- Pairing tests call authenticated /v1/status after public /health. Saving through
  Settings verifies the connection and token first.
- Watcher changes invalidate prior acknowledgements and cached state. Connection
  changes and rule operations are serialized; no blind mutation retries were added.
- Response bodies are bounded while reading and are never echoed into errors.

## Verification

- testDebugUnitTest: 436 tests, zero failures/errors.
- testPreviewUnitTest: 436 tests, zero failures/errors.
- 13 new JVM regressions cover acknowledgements, offline stop, identifiers,
  protected pairing, defaults, endpoint changes, status interpretation and nav migration.
- AutoAddOverviewUiTest: 3 emulator tests passed (API 35). Isolated preferences and
  fixture episode data; no live watcher or acquisition calls. Screenshots inspected
  after animations settled: overview, episode detail, Details badge, quality options.
- build-update.ps1 -Offline exported the optimized preview APK with original
  application id/signing identity and configured cloud settings.
- APK: distribution/STAK-current.apk, version 1.1.0, code 57.
- SHA-256: 4845752bb1f61625fb43a9fb200c1a056b1034f6db1ebb80d93b5b5f193409d0
- Certificate: bd9c33ebf4a4a62fc034cbb363635ed8d4d55f081ce43fcaff59706d2230c430

No watcher deployment changes are required for this UI/client update. The Linux
watcher/filter and the owner's phone were not accessed. Physical-device rendering,
actual home-network conditions and organic TorBox acquisition were not verified.
Install over the existing app without uninstalling or clearing data.

Historical ledger notes claiming that waiting_release rows prove source searches
should not be used as evidence of acquisition: those rows alone only establish
that episode metadata was created. Public health alone does not verify downstream
services or a bearer token.
