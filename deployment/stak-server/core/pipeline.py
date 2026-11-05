"""Episode pipeline: the 9-step add flow with honest statuses and backoff.

Steps (brief order):
 1. released + rule-watched?
 2. known TorBox/Jellyfin/ledger state?
 3. search + rank
 4. FRESH cache re-confirmation immediately before add
 5. server-side enforced cached-only TorBox add
 6. STAK-filter approval for the exact torrent identity
 7. filter/Zurg publication wait
 8. optional Jellyfin scan request
 9. available_jellyfin only after verified indexing

Statuses: waiting_release, waiting_release_match, waiting_torbox_cache,
added_torbox, waiting_library, available_jellyfin, paused, connection_unavailable
"""

from __future__ import annotations

import datetime as dt
import time

from . import matching, providers
from .dedup import AddLedger, EpisodeState, TorrentRecord
from .episode import EpisodeContext, episode_is_released, episode_wanted, parse_air_date

STATUS_WAITING_RELEASE = "waiting_release"
STATUS_WAITING_MATCH = "waiting_release_match"
STATUS_WAITING_CACHE = "waiting_torbox_cache"
STATUS_ADDED = "added_torbox"
STATUS_WAITING_LIBRARY = "waiting_library"
STATUS_AVAILABLE = "available_jellyfin"
STATUS_PAUSED = "paused"
STATUS_CONNECTION = "connection_unavailable"


class NetworkDown(Exception):
    """A provider/service was unreachable: backoff before the next attempt."""


BASE_BACKOFF_SECONDS = 60
MAX_BACKOFF_SECONDS = 3600


class WatcherDeps:
    """Injected services for the pipeline.

    `client` is the fetch provider passed into `providers.*` and every HTTP
    wrapper. A failing fetch must raise; the pipeline maps that to backoff.
    """

    def __init__(self, *, store, client, torbox, filter_client, jellyfin,
                 addons: list[tuple[str, str]], torznab: list[dict],
                 jellyfin_scan_enabled: bool = False, filter_scope: str = "",
                 negative_ttl_seconds: float = 12 * 3600):
        self.store = store
        self.client = client
        self.torbox = torbox
        self.filter = filter_client
        self.jellyfin = jellyfin
        self.addons = addons
        self.torznab = torznab
        self.jellyfin_scan_enabled = jellyfin_scan_enabled
        self.filter_scope = filter_scope
        self.negative_ttl_seconds = negative_ttl_seconds
        self.rule_is_current = lambda rule: True
        self.verify_filter = False
        self.reserve_if_current = None
        self.reuse_if_current = None


def _now() -> float:
    return time.time()


def _retry_in(row: dict, seconds: float) -> float:
    """Exponential network-error backoff, always >= the requested interval."""
    attempts = row.get("attempts") or 0
    backoff = min(BASE_BACKOFF_SECONDS * (2 ** min(attempts, 6)), MAX_BACKOFF_SECONDS)
    return _now() + max(seconds, backoff)


def _rule_started_date(rule) -> dt.date:
    return parse_air_date(getattr(rule, "created_on", None)) or dt.date(1970, 1, 1)


def _context(rule, row: dict) -> EpisodeContext:
    return EpisodeContext(
        rule_id=row["rule_id"], season=row["season"], episode=row["episode_number"],
        air_date=parse_air_date(row.get("air_date")), rule_started_on=_rule_started_date(rule),
        enabled=True)


def _ledger(deps, row: dict) -> AddLedger:
    torrents = {}
    for info_hash in deps.store.torrents_owned()+deps.store.torrents_unconfirmed():
        record = deps.store.torrent(info_hash)
        if record:
            torrents[info_hash] = TorrentRecord(record["info_hash"], record["source_identity"],
                                                record["episode_key"], record["added_at"],
                                                record["state"])
    episodes = {row["episode_key"]: EpisodeState(row["episode_key"], row.get("torrent_hash"),
                                                 row.get("torrent_identity"))}
    return AddLedger(torrents=torrents, episodes=episodes)


# --- rule <-> episode row sync ------------------------------------------

def sync_rule_episodes(deps: WatcherDeps, payload: dict, metadata_loader) -> int:
    """Create/refresh episode rows for a rule from TMDb/Cinemeta episode data.

    `metadata_loader(series)` yields {season, episode, air_date}. Episodes
    outside watched seasons are never created; specials only when allowed."""
    from .rules import rule_from_dict
    rule = rule_from_dict(payload)
    return deps.store.apply_episode_refs(rule,list(metadata_loader(rule.series)))


def pause_rule_episodes(deps: WatcherDeps, rule_id: str) -> None:
    for row in deps.store.episodes_for_rule(rule_id):
        if row["status"] != STATUS_AVAILABLE:
            deps.store.set_episode_status(row["episode_key"], STATUS_PAUSED, "rule paused")


def resume_rule_episodes(deps: WatcherDeps, rule_id: str, check_minutes: int) -> None:
    for row in deps.store.episodes_for_rule(rule_id):
        if row["status"] == STATUS_PAUSED:
            deps.store.set_episode_status(row["episode_key"], STATUS_WAITING_RELEASE
                                          if episode_unstarted(row) else STATUS_WAITING_MATCH,
                                          "rule resumed", next_check=_now() + 15)


def episode_unstarted(row: dict) -> bool:
    air = row.get("air_date")
    return not air or air >= dt.date.today().isoformat()


# --- provider-state reconciliation --------------------------------------

def reconcile_with_account(deps):
    """Only this ledger's attempted hashes may be attached to an episode.
    Missing/ambiguous lists never erase ownership or approve foreign torrents.
    Unknown outcomes require a quarantine plus repeated fresh absence proof."""
    store=deps.store
    try:items=deps.torbox.mylist()
    except Exception as error:
        store.event('reconcile_failed',type(error).__name__)
        return {'reconciled':0,'error':'TorBox account unavailable'}
    if not isinstance(items,list):return {'reconciled':0,'error':'Invalid account response'}
    account={str(i.get('hash','')).lower():i for i in items if isinstance(i,dict) and len(str(i.get('hash','')))==40}
    count=0
    for h in store.torrents_unconfirmed():
        rec=store.torrent(h);item=account.get(h)
        if item is not None:
            ident=item.get('id')
            if ident is None:continue
            store.put_torrent(h,rec['episode_key'],rec['source_identity'],'added',str(ident),rec['added_at'])
            if rec['episode_key']:
                store.set_episode_torrent(rec['episode_key'],h,rec['source_identity'],str(ident),rec['added_at'])
                store.set_episode_status(rec['episode_key'],STATUS_ADDED,'TorBox identity confirmed; continuing filter approval',next_check=_now())
            count+=1
        else:
            key='absent:'+h;first=store.get_meta(key)
            if first is None:store.set_meta(key,_now())
            elif _now()-float(first)>=60 and _now()-rec['added_at']>=900:
                store.put_torrent(h,rec['episode_key'],rec['source_identity'],'rolled_back')
                if rec['episode_key']:store.set_episode_status(rec['episode_key'],STATUS_WAITING_MATCH,'Account confirms the attempt did not land; retry allowed',next_check=_now()+60)
                count+=1
    return {'reconciled':count,'orphans':sum(1 for h in account if not store.torrent(h))}


# --- one pipeline attempt ------------------------------------------------

def process_episode(deps: WatcherDeps, row: dict, rule, series_name: str, imdb: str | None,
                    now: float | None = None) -> str:
    now = _now() if now is None else now
    store = deps.store
    episode_key = row["episode_key"]
    context = _context(rule, row)

    if not deps.rule_is_current(rule):
        return _obsolete_rule(deps,rule,episode_key)
    reservation=store.episode_reservation(episode_key)
    if reservation and reservation['state']=='unconfirmed':
        store.set_episode_status(episode_key,STATUS_ADDED,'Add outcome is being reconciled; no duplicate request will be sent',next_check=now+60,last_check=True)
        return STATUS_ADDED
    if reservation and reservation['state']=='added' and reservation.get('torbox_id') and not row.get('torbox_id'):
        store.set_episode_torrent(episode_key,reservation['info_hash'],reservation['source_identity'],reservation['torbox_id'],reservation['added_at'])
        row=store.episode(episode_key)

    # Step 2: already verified / already added states short-circuit.
    if row.get("jellyfin_verified"):
        store.set_episode_status(episode_key, STATUS_AVAILABLE, "library already verifiers", next_check=None)
        return STATUS_AVAILABLE
    if row.get("torbox_id") and row.get("torrent_hash"):
        return _publication_stage(deps, rule, row, now)

    # Step 1: releases + rule scope.
    if not episode_wanted(rule, context):
        store.set_episode_status(episode_key,STATUS_PAUSED,'Outside selected episode scope',next_check=None,last_check=True)
        return STATUS_PAUSED
    if not episode_is_released(context, dt.datetime.fromtimestamp(now,dt.timezone.utc).date()):
        air_text = row.get("air_date") or "episode air date unknown"
        store.set_episode_status(episode_key, STATUS_WAITING_RELEASE, air_text,
                                 next_check=_retry_in(row, 6 * 3600), last_check=True)
        return STATUS_WAITING_RELEASE

    # A verified existing Jellyfin episode needs no acquisition at all.
    if deps.jellyfin is not None:
        try:
            sid=deps.jellyfin.find_series_base(rule.series.imdb or rule.series.jellyfin)
            if sid and deps.jellyfin.episode_present(sid,row['season'],row['episode_number']):
                store.set_series_jellyfin(episode_key,sid);store.set_jellyfin_verified(episode_key,True)
                return STATUS_AVAILABLE
        except Exception:
            store.set_episode_status(episode_key,STATUS_CONNECTION,'Jellyfin preflight unavailable; acquisition postponed',next_check=_retry_in(row,60),last_check=True)
            return STATUS_CONNECTION
    if deps.torbox is None:
        store.set_episode_status(episode_key,STATUS_CONNECTION,'TorBox is not configured',next_check=_retry_in(row,60),last_check=True)
        return STATUS_CONNECTION

    # Step 3: search (per-provider negative cache; stale negatives revisited).
    candidates, search_error = _search(deps, rule, context, series_name, imdb, row)
    if search_error is not None:
        store.set_episode_status(episode_key, STATUS_CONNECTION,
                                 f"search failed: {search_error}",
                                 next_check=_retry_in(row, BASE_BACKOFF_SECONDS),
                                 attempts=(row.get("attempts") or 0) + 1, last_check=True)
        store.event("search_failed", str(search_error), episode_key)
        return STATUS_CONNECTION

    if not candidates:
        # Nothing usable anywhere: negative caches give a slower next check;
        # a stale negative entry is never a permanent block.
        store.set_episode_status(episode_key, STATUS_WAITING_MATCH,
                                 "searched: nothing usable returned by configured providers",
                                 next_check=_retry_in(row, rule.check_minutes * 60), last_check=True)
        return STATUS_WAITING_MATCH

    fresh_cache = _checked_cache(deps, candidates, row)
    if not fresh_cache:
        store.set_episode_status(episode_key, STATUS_WAITING_CACHE,
                                 "no release in TorBox cache; rechecking later",
                                 next_check=_retry_in(row, rule.check_minutes * 60), last_check=True)
        return STATUS_WAITING_CACHE
    store.put_search_cache("-positive-", episode_key, False, sorted(fresh_cache))

    accepted, rejected = matching.match_release_list(candidates, rule, context, fresh_cache, now)
    if not accepted:
        store.set_episode_status(episode_key, STATUS_WAITING_MATCH, detail_rejections(rejected),
                                 next_check=_retry_in(row, rule.check_minutes * 60), last_check=True)
        return STATUS_WAITING_MATCH

    candidate = accepted[0]

    # Step 5 gating: provider state first (is the torrent "mine" already?).
    provider_state = "missing"
    account_matches=[]
    try:
        for item in deps.torbox.mylist():
            if str(item.get("hash", "")).lower() == candidate.info_hash:
                provider_state = "mine"
                account_matches.append(item)
    except Exception as error:  # noqa: BLE001
        store.set_episode_status(episode_key, STATUS_CONNECTION,
                                 f"TorBox account check failed: {error}",
                                 next_check=_retry_in(row, BASE_BACKOFF_SECONDS), last_check=True)
        return STATUS_CONNECTION

    ledger = _ledger(deps, row)
    action, detail = ledger.decide_add(episode_key, candidate.info_hash,
                                       candidate.source_identity, provider_state)
    if action == "reuse":
        # Only a previously watcher-owned exact hash can be reused. Unknown
        # account torrents still take reconcile_first, never automatic approval.
        if not deps.rule_is_current(rule):return _obsolete_rule(deps,rule,episode_key)
        record=store.torrent(candidate.info_hash)
        ident=account_matches[0].get('id') if len(account_matches)==1 else None
        # Torrent names can cover several episodes. Prove this episode from
        # actual video basenames, not its parent folder or release label.
        from .episode import parse_episode_marker
        import re
        def exact_video(value):
            if not isinstance(value,dict):return False
            name=value.get('name') or value.get('short_name') or ''
            if not isinstance(name,str):return False
            name=name.replace('\\','/').rsplit('/',1)[-1]
            if not name.lower().endswith(('.mkv','.mp4','.m4v','.avi','.webm')):return False
            if re.search(r'(?i)(?:^|[. _-])sample(?:[. _-]|$)',name):return False
            if re.search(r'(?i)(?:E\d{1,3}(?:[-+&]E?\d{1,3}|E\d{1,3})|\d+x\d+[-+]\d+)',name):return False
            return parse_episode_marker(name)==(row['season'],row['episode_number'])
        files=account_matches[0].get('files',[]) if len(account_matches)==1 else []
        episode_verified=isinstance(files,list) and sum(exact_video(f) for f in files)==1
        attach=deps.reuse_if_current or (lambda r,h,k,i:store.attach_owned(h,k,i))
        if record and record['state']=='added' and ident is not None and episode_verified and attach(rule,candidate.info_hash,episode_key,str(ident)):
            store.event('reused_owned','Existing watcher-owned torrent; no TorBox add sent',episode_key)
            return _publication_stage(deps,rule,store.episode(episode_key),now)
        store.set_episode_status(episode_key,STATUS_WAITING_MATCH,'Known torrent requires verified account identity and an exact episode video file before reuse',next_check=now+60,last_check=True)
        return STATUS_WAITING_MATCH
    if action != "add":
        store.set_episode_status(episode_key, STATUS_WAITING_MATCH, f"deferred: {action} — {detail}",
                                 next_check=_retry_in(row, rule.check_minutes * 60), last_check=True)
        return STATUS_WAITING_MATCH

    # Final dependency and consent guards, after all expensive reads.
    if deps.verify_filter:
        try:
            status=deps.filter.status() if deps.filter is not None else {}
            if status.get('scope')!=deps.filter_scope or status.get('source_available') is not True:
                raise NetworkDown('Filter account or source is not ready')
        except Exception:
            store.set_episode_status(episode_key,STATUS_CONNECTION,'Filter account/source is not ready; nothing was added',next_check=_retry_in(row,60),last_check=True)
            return STATUS_CONNECTION
    payload=getattr(candidate,'torrent_payload',None)
    if getattr(candidate,'torrent_url',None) and payload is None:
        from .torrent_metadata import fetch_torrent
        payload,actual=fetch_torrent(deps.client,candidate.torrent_url)
        if actual!=candidate.info_hash:raise NetworkDown('Torrent metadata identity mismatch')
    if payload is not None:
        from .torrent_metadata import validate_episode,inspect_torrent
        if inspect_torrent(payload)[0]!=candidate.info_hash:raise NetworkDown('Torrent metadata identity mismatch')
        validate_episode(payload,row['season'],row['episode_number'])
    if candidate.info_hash not in deps.torbox.cached_hashes([candidate.info_hash]):
        store.set_episode_status(episode_key,STATUS_WAITING_CACHE,'Cache changed before add; nothing was sent',next_check=now+rule.check_minutes*60,last_check=True)
        return STATUS_WAITING_CACHE
    if not deps.rule_is_current(rule):
        return _obsolete_rule(deps,rule,episode_key)
    reserve=deps.reserve_if_current or (lambda r,h,k,i:store.reserve_add(h,k,i))
    if not reserve(rule,candidate.info_hash,episode_key,candidate.source_identity):
        if not deps.rule_is_current(rule):return _obsolete_rule(deps,rule,episode_key)
        store.set_episode_status(episode_key,STATUS_ADDED,'Existing durable reservation; reconciliation required',next_check=now+60,last_check=True)
        return STATUS_ADDED
    # Mutation is reserved on disk BEFORE it is sent. Never blind-retry it.
    try:
        result=(deps.torbox.add_cached_torrent_file('release.torrent',payload) if payload is not None else
                deps.torbox.add_cached_magnet(getattr(candidate,'magnet',None) or identity_less_magnet(candidate)))
    except Exception as error:
        store.set_episode_status(episode_key,STATUS_ADDED,'Add response unclear; reconciling provider state before retry',next_check=_now()+60,last_check=True)
        store.event('add_unconfirmed',type(error).__name__,episode_key)
        return STATUS_ADDED

    if not result.get("added"):
        store.put_torrent(candidate.info_hash,episode_key,candidate.source_identity,'rolled_back')
        store.set_episode_status(episode_key, STATUS_WAITING_CACHE,
                                 "TorBox refused non-cached add (cached-only enforced server-side)",
                                 next_check=_retry_in(row, rule.check_minutes * 60), last_check=True)
        return STATUS_WAITING_CACHE

    torbox_id = result.get("id")
    if torbox_id is None:
        store.set_episode_status(episode_key,STATUS_ADDED,'Add has no confirmed id; reconciliation required',next_check=now+60,last_check=True)
        return STATUS_ADDED
    ledger.mark_added(episode_key, candidate.info_hash, candidate.source_identity, now)
    store.put_torrent(candidate.info_hash, episode_key, candidate.source_identity,
                      "added", torbox_id, added_at=now)
    store.set_episode_torrent(episode_key, candidate.info_hash, candidate.source_identity,
                              torbox_id, now)
    store.event("added", f"{candidate.source_identity} -> {torbox_id}", episode_key)

    try:
        _post_approval(deps, rule, row, candidate, torbox_id)
    except Exception as error:  # noqa: BLE001
        store.set_episode_status(episode_key, STATUS_ADDED,
                                 f"approval send failed; will retry: {error}",
                                 next_check=_retry_in(row, 30), last_check=True)
        return STATUS_ADDED

    return _publication_stage(deps, rule, store.episode(episode_key), now)


def _publication_stage(deps: WatcherDeps, rule, row, now: float) -> str:
    """Steps 7-9: filter publication, optional scan request, Jellyfin proof."""
    store = deps.store
    episode_key = row["episode_key"]
    try:
        approval = deps.filter.approval_by_id(row["torbox_id"])
        if approval is None:
            record=store.torrent(row['torrent_hash'])
            if not record or record['state']!='added':raise NetworkDown('Approval requires recorded ownership')
            _approve_series(deps,rule,row['torbox_id'],row['torrent_hash'],rule.series.name)
            approval=deps.filter.approval_by_id(row['torbox_id'])
        state = (approval or {}).get("state") or "unknown"
    except Exception as error:  # noqa: BLE001
        store.set_episode_status(episode_key, STATUS_ADDED,
                                 f"filter unreachable: {error}",
                                 next_check=_retry_in(row, 30), last_check=True)
        store.event("filter_unavailable", str(error), episode_key)
        return STATUS_ADDED
    store.set_filter_state(episode_key, state)
    if state != "published":
        store.set_episode_status(episode_key, STATUS_ADDED
                                 if state != "revoked" else STATUS_WAITING_RELEASE,
                                 f"filter state: {state}",
                                 next_check=(now + 30) if state not in ("needs_type","revoked") else None,
                                 last_check=True)
        return STATUS_ADDED if state != "revoked" else STATUS_WAITING_RELEASE

    if deps.jellyfin is None:
        store.set_episode_status(episode_key, STATUS_WAITING_LIBRARY, "published; Jellyfin not configured",
                                 next_check=None, last_check=True)
        return STATUS_WAITING_LIBRARY

    series_id = row.get("series_jellyfin")
    if not series_id:
        # Look up the Jellyfin series once by stable provider identity; the
        # result persists so later runs never re-guess.
        try:
            series_id = deps.jellyfin.find_series_base(
                rule.series.imdb or rule.series.jellyfin)
        except Exception as error:  # noqa: BLE001 — lookup is retryable
            store.event("series_lookup_failed", str(error), episode_key)
            series_id = None
        if series_id:
            store.set_series_jellyfin(episode_key, series_id)
            row = {**row, "series_jellyfin": series_id}

    try:
        series_id = series_id or row.get("series_jellyfin") or ""
        present = deps.jellyfin.episode_present(series_id, row["season"], row["episode_number"]) \
            if series_id else False
    except Exception as error:  # noqa: BLE001
        store.set_series_jellyfin(episode_key,None)
        store.set_episode_status(episode_key, STATUS_WAITING_LIBRARY, f"Jellyfin check failed: {error}",
                                 next_check=_retry_in(row, 60), last_check=True)
        return STATUS_WAITING_LIBRARY
    if present:
        store.set_jellyfin_verified(episode_key, True)
        store.event("available_jellyfin", "verified", episode_key)
        return STATUS_AVAILABLE

    # A reorganized library may give the series a new Jellyfin id.
    store.set_series_jellyfin(episode_key,None)
    if deps.jellyfin_scan_enabled and now-float(store.get_meta('last_library_scan','0'))>=300:
        try:
            deps.jellyfin.request_scan(row.get('series_jellyfin'))
            store.set_meta('last_library_scan',now)
        except Exception as error:
            store.event('scan_failed',type(error).__name__,episode_key)
    store.set_episode_status(episode_key, STATUS_WAITING_LIBRARY, "published; waiting for indexing",
                             next_check=now+30, last_check=True)
    return STATUS_WAITING_LIBRARY


# --- approvals ------------------------------------------------------------

def _approve_series(deps,rule,torbox_id,info_hash,name):
    method=getattr(deps.filter,'post_series_approval',None)
    if callable(method):return method(torbox_id,deps.filter_scope,info_hash,name,rule.series)
    return deps.filter.post_approval(torbox_id,deps.filter_scope,info_hash,name)


def _post_approval(deps, rule, row, candidate, torbox_id) -> None:
    """Publish the add to the STAK moderation filter (type=series approval).

    The scope is the account-bound hash computed at startup; the approval is
    only registered, media data is never sent. Unrelated torrents are never
    approved: the add itself was already account-guarded by the pipeline.
    """
    if deps.filter is None:
        deps.store.event("filter_unconfigured", "no filter client configured",
                         row["episode_key"])
        return
    _approve_series(deps,rule,torbox_id,candidate.info_hash,candidate.release_text or candidate.name)


# --- search plumbing -----------------------------------------------------

_PROVIDERS: tuple[str, ...] = ("addons", "torznab")


def _search(deps,rule,context,series_name,imdb,row):
    out=[];failures=[];configured=0;successful=0
    groups=[('addons',bool(deps.addons),lambda:providers.search_stremio(deps.client,deps.addons,imdb,context.season,context.episode)),
            ('torznab',any(t.get('enabled',True) for t in deps.torznab),lambda:providers.search_torznab(deps.client,[t for t in deps.torznab if t.get('enabled',True)],imdb,series_name,context.season,context.episode))]
    for name,enabled,search in groups:
        if not enabled:continue
        configured+=1
        cache=deps.store.search_cache(name,row['episode_key'])
        if _negative_usable(cache,min(deps.negative_ttl_seconds,rule.check_minutes*60)):
            successful+=1;continue
        try:
            found,warnings,errors=search();successful+=1;out.extend(found)
            if not errors:deps.store.put_search_cache(name,row['episode_key'],not found,sorted({c.info_hash for c in found if c.info_hash}))
        except Exception as error:failures.append(type(error).__name__)
    from .episode import parse_episode_marker,is_season_pack
    unique={c.info_hash:c for c in out if c.info_hash and parse_episode_marker(c.release_text)==(context.season,context.episode) and not is_season_pack(c.release_text,context.season)}
    error='No enabled source configured' if not configured else 'All configured sources are unavailable' if not successful else None
    return list(unique.values()),error


def _negative_usable(cached: dict | None, ttl: float) -> bool:
    """A stale negative entry is revisited; fresh negative skips one round."""
    if not cached or not cached.get("negative"):
        return False
    return (time.time() - cached["checked_at"]) < ttl


def _checked_cache(deps, candidates, row) -> set[str]:
    """Fresh TorBox cache verification for the candidate set (80-hash batches
    handled by the torbox client)."""
    hashes = [c.info_hash for c in candidates if c.info_hash]
    try:
        return deps.torbox.cached_hashes(hashes)
    except Exception as error:  # noqa: BLE001
        raise NetworkDown(f"TorBox cache check failed: {error}")


def detail_rejections(rejected) -> str:
    if not rejected:
        return "no matching releases yet"
    totals: dict[str, int] = {}
    for _, reason in rejected:
        totals[reason] = totals.get(reason, 0) + 1
    top = sorted(totals.items(), key=lambda kv: -kv[1])[:3]
    return "rejects: " + "; ".join(f"{r} (x{n})" for r, n in top)


def identity_less_magnet(candidate) -> str:
    return f"magnet:?xt=urn:btih:{candidate.info_hash}"


def _obsolete_rule(deps,rule,key):
    current=deps.store.rule(rule.rule_id)
    if current and current.get('enabled') and not current.get('paused'):
        deps.store.set_episode_status(key,STATUS_WAITING_MATCH,'Rule updated; checking the new settings next',next_check=_now()+1)
        return STATUS_WAITING_MATCH
    deps.store.set_episode_status(key,STATUS_PAUSED,'Rule paused or removed; no new add sent',next_check=None)
    return STATUS_PAUSED
