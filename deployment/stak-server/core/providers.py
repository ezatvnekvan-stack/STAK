"""Metadata providers for the watcher: Stremio addons + Torznab indexers.

Network access is injected via a `fetch` callable implementing:

    fetch(url, *, headers, data, timeout, method) -> HTTPResponse

with attributes `status:int`, `headers:Mapping[str,str]`, `read()->bytes`.
This keeps each module testable against fixtures with real parsing logic.
"""

from __future__ import annotations

import json
import hashlib
from concurrent.futures import ThreadPoolExecutor
from .security import Redactor
import re
import threading
import time
import urllib.parse

from .matching import Candidate
from .traits import parse_traits

_NAME_SEMAPHORE = threading.Semaphore(3)

_addon_cache: dict[str, dict] = {}
_addon_cache_times: dict[str, float] = {}
_cache_lock=threading.RLock()


class ProviderError(Exception):
    """Raised when a provider cannot be queried (network, limits, parsing)."""


def _addon_stream(client, base: str, imdb: str, season: int, episode: int) -> dict:
    """Fetch (and cache per URL) the stream document of a manifest-bearing base.

    A manifest URL like `https://x.strem.fun/manifest.json` serves streams at
    the same root without the manifest segment.
    """
    root = base.rstrip("/")
    if root.endswith("/manifest.json"):
        root = root[: -len("/manifest.json")]
    url = f"{root}/stream/series/{imdb}:{season}:{episode}.json"
    now = time.time()
    with _cache_lock:
        cached = _addon_cache.get(url)
        if cached and now - _addon_cache_times.get(url, 0) < 300:
            return cached
    response = client.fetch(url, headers={"Accept": "application/json"}, timeout=30)
    if response.status != 200:
        raise ProviderError(f"addon returned HTTP {response.status}")
    payload = response.read()
    if len(payload) > 8 * 1024 * 1024:
        raise ProviderError("addon manifest response too large")
    document = json.loads(payload.decode("utf-8", "replace"))
    # Bounded document cache; no unbounded per-episode memory growth.
    if len(payload)<=1024*1024:
        with _cache_lock:
            if len(_addon_cache)>=8:
                oldest=min(_addon_cache_times,key=_addon_cache_times.get)
                _addon_cache.pop(oldest,None);_addon_cache_times.pop(oldest,None)
            _addon_cache[url] = document
            _addon_cache_times[url] = now
    return document


def parse_stream_entry(entry: dict, addon_name: str) -> Candidate | None:
    """Port of the app's parseSourceRelease adapted for automation honesty.

    Returns None for entries that cannot be reliably identified (no hash, no
    magnet): the watcher never treats a playback URL as torrent metadata.
    """
    info_hash = None
    raw_hash = entry.get("infoHash")
    if isinstance(raw_hash, str) and re.fullmatch(r"[a-fA-F0-9]{40}", raw_hash):
        info_hash = raw_hash.lower()
    url = entry.get("url")
    magnet = None
    if isinstance(url, str) and url.startswith("magnet:?"):
        match = re.match(r"magnet:\?xt=urn:btih:([a-fA-F0-9]{40})", url)
        if match:
            info_hash = info_hash or match.group(1).lower()
            magnet = url
    if info_hash is None and magnet is None:
        return None

    hints = entry.get("behaviorHints") or {}
    filename = hints.get("filename") or ""
    description = entry.get("description") or entry.get("title") or filename or "Release"
    name = (entry.get("title") or description).splitlines()[0]
    combined = " ".join([description, entry.get("title") or "", filename])
    size = hints.get("videoSize")
    size_bytes = int(size) if isinstance(size, (int, float)) and size > 0 else None
    seeders = 0
    match = re.search(r"(?:👤|👥|[Ss]eeders?:?)\s*(\d+)", combined)
    if match:
        seeders = int(match.group(1))

    traits = parse_traits(combined, size_bytes)
    return Candidate(
        name=name,
        release_text=combined,
        size_bytes=size_bytes,
        info_hash=info_hash,
        source_identity=f"{addon_name}#{entry.get('name', '')}#{filename or info_hash}",
        provider=f"addon:{addon_name}",
        traits=traits,magnet=magnet,
    )


def search_stremio(client, addons: list[tuple[str, str]], imdb: str, season: int,
                   episode: int) -> tuple[list[Candidate], list[str], list[str]]:
    """Search every addon concurrently. Returns (candidates, warnings, failures).

    `failures` carries per-addon exception messages; when every addon failed the
    pipeline treats it as a network/provider outage instead of 'nothing found'.
    """
    results: list[Candidate] = []
    warnings: list[str] = []
    failures: list[str] = []
    lock = threading.Lock()

    def run(base: str, name: str) -> None:
        try:
            document = _addon_stream(client, base, imdb, season, episode)
            found: list[Candidate] = []
            for entry in document.get("streams", [])[:1500]:
                if not isinstance(entry, dict):
                    continue
                candidate = parse_stream_entry(entry, name)
                if candidate is not None:
                    found.append(candidate)
            with lock:
                results.extend(found)
        except Exception as error:  # noqa: BLE001 — isolate addon failures
            with lock:
                failures.append(f"{name}: {type(error).__name__}")

    with ThreadPoolExecutor(max_workers=3) as executor:
        list(executor.map(lambda pair:run(*pair),addons[:16]))
    if len(failures)>=len(addons[:16]) and addons:
        raise ProviderError("All addon sources are unavailable")
    return results, warnings, failures


# --- Torznab ----------------------------------------------------------------

_NAB_TYPE_TV = "tvsearch"


def nab_query_url(indexer_url: str, api_key: str, imdb: str | None, title: str,
                  season: int, episode: int) -> str:
    """Build one tvsearch request. Prefers `imdbid` (ids, never title-only);
    without an IMDb id falls back to `title SxxEyy` — the pipeline then still
    verifies the actual episode marker before accepting anything."""
    u=urllib.parse.urlsplit(indexer_url)
    path=u.path.rstrip('/')
    if not path.endswith('/api'):path+='/api'
    params=[(k,v) for k,v in urllib.parse.parse_qsl(u.query) if k not in ('t','apikey','imdbid','q','season','ep')]
    params += [('t',_NAB_TYPE_TV),('season',str(season)),('ep',str(episode))]
    if imdb:params.append(('imdbid',imdb.lower().removeprefix('tt')))
    else:params.append(('q',f'{title} S{season:02d}E{episode:02d}'))
    params.append(('apikey',api_key))
    return urllib.parse.urlunsplit((u.scheme,u.netloc,path,urllib.parse.urlencode(params),''))



def parse_nab_response(data):
    import xml.etree.ElementTree as ET
    if len(data)>8*1024*1024 or '<!DOCTYPE' in data.upper() or '<!ENTITY' in data.upper():raise ProviderError('Unsafe or oversized XML response')
    # Some compatible Torznab feeds omit the namespace declaration.
    if 'torznab:' in data and 'xmlns:torznab' not in data:
        data=data.replace('<rss','<rss xmlns:torznab="http://torznab.com/schemas/2015/feed"',1)
    root=ET.fromstring(data);items=[]
    if root.tag.rsplit('}',1)[-1]=='error':raise ProviderError('Torznab reported an API error')
    for item in root.iter('item'):
        if len(items)>=1500:break
        attrs={a.get('name'):a.get('value') for a in item if a.tag.rsplit('}',1)[-1]=='attr'}
        title=item.findtext('title');link=item.findtext('link') or ''
        enclosure=item.find('enclosure');download=enclosure.get('url') if enclosure is not None else None
        # Torznab enclosures may contain magnets, not just HTTP torrent files.
        # Keep the two channels separate; never feed a magnet to HTTP fetch.
        magnet=None
        for value in (attrs.get('magneturl'),link,download):
            if isinstance(value,str) and value.strip().startswith('magnet:?'):
                magnet=value.strip();break
        def http_metadata(value):
            if not isinstance(value,str):return None
            value=value.strip()
            try:
                u=urllib.parse.urlsplit(value)
                if u.scheme in ('http','https') and u.hostname and not u.username and not u.password and len(value)<=32768:
                    _=u.port
                    return value
            except ValueError:pass
            return None
        download=http_metadata(download)
        raw=attrs.get('infohash') or attrs.get('infoHash') or ''
        if not re.fullmatch(r'[a-fA-F0-9]{40}',raw):
            match=re.search(r'(?i)(?:[?&])xt=urn:btih:([a-f0-9]{40})',magnet or '')
            raw=match.group(1) if match else ''
        if not download:download=http_metadata(link)
        def number(raw):
            try:return max(0,int(raw or 0))
            except (TypeError,ValueError):return 0
        guid=item.findtext('guid') or link or raw
        if not title or not guid:continue
        items.append({'title':title,'guid':guid,'link':link,'hash':raw.lower() or None,
            'magnet':magnet,'torrent_url':download,'size':number(attrs.get('size') or item.findtext('size')),
            'seeders':number(attrs.get('seeders')),'imdb':attrs.get('imdb') or attrs.get('imdbid')})
    return items


def search_torznab(client, indexers: list[dict], imdb: str | None, title: str,
                   season: int, episode: int, max_items: int = 300
                   ) -> tuple[list[Candidate], list[str], list[str]]:
    """Search indexers concurrently; per-indexer error isolation. Returns
    (candidates, warnings, failures)."""
    results: list[Candidate] = []
    warnings: list[str] = []
    failures: list[str] = []
    lock = threading.Lock()

    def run(indexer: dict) -> None:
        try:
            url = nab_query_url(indexer["url"], indexer["apiKey"], imdb, title, season, episode)
            response = client.fetch(url, headers={"Accept": "application/xml"}, timeout=30)
            if response.status != 200:
                with lock:
                    failures.append(f"{indexer['id']}: HTTP {response.status}")
                return
            text = response.read().decode("utf-8", "replace")
            items = parse_nab_response(text)
            found = []
            plain_imdb = imdb.lower().removeprefix("tt") if imdb else None
            metadata_reads=0
            for item in items[:max_items]:
                from .episode import parse_episode_marker,is_season_pack
                if parse_episode_marker(item['title'])!=(season,episode) or is_season_pack(item['title'],season):continue
                payload=None
                if not item['hash'] and item.get('torrent_url'):
                    if metadata_reads>=4:continue
                    metadata_reads+=1
                    from .torrent_metadata import fetch_torrent
                    try:payload,item['hash']=fetch_torrent(client,item['torrent_url'])
                    except Exception:
                        warnings.append(str(indexer['id'])+': torrent metadata unavailable');continue
                item_imdb = str(item["imdb"]).lower().removeprefix("tt") if item["imdb"] else None
                if item_imdb and plain_imdb and item_imdb != plain_imdb:
                    continue  # wrong series: never match by title similarity alone
                candidate = Candidate(
                    name=item["title"],
                    release_text=item["title"],
                    size_bytes=item["size"] or None,
                    info_hash=item["hash"],
                    source_identity=f"torznab:{indexer['id']}:{hashlib.sha256(item['guid'].encode()).hexdigest()}",
                    provider=f"torznab:{indexer['id']}",
                    traits=parse_traits(item["title"], item["size"] or None),
                    magnet=item.get("magnet"),torrent_url=item.get("torrent_url"),torrent_payload=payload,
                )
                if candidate.info_hash and (candidate.magnet or candidate.torrent_url or candidate.torrent_payload):
                    found.append(candidate)
                else:
                    with lock:
                        warnings.append(str(indexer["id"])+": item without usable torrent metadata")
            with lock:
                results.extend(found)
        except Exception as error:  # noqa: BLE001
            with lock:
                failures.append(str(indexer["id"])+": "+type(error).__name__)

    with ThreadPoolExecutor(max_workers=3) as executor:
        list(executor.map(run,indexers[:16]))
    if len(failures)>=len(indexers[:16]) and indexers:
        raise ProviderError('All Torznab sources are unavailable')
    return results, warnings, failures
