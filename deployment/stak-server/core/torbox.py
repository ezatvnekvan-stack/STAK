"""TorBox client for the watcher — cached-only add enforced server-side.

Mirrors the app rules:
- `checkcached` is used for fresh confirmation right before adding
- `createtorrent` always carries `add_only_if_cached=true` and `cachedOnly=true`
- one-shot bodies, no automatic mutation retries
- ambiguous answers surface as `UnconfirmedTorrentMutation`
"""

from __future__ import annotations

import json
import time
import re
import urllib.parse

API_ORIGIN = "https://api.torbox.app/v1/api"
MAX_TORRENT_BYTES = 5 * 1024 * 1024


class TorBoxError(Exception):
    pass


class UnconfirmedTorrentMutation(Exception):
    """The add did not literally succeed; the ledger stays 'unconfirmed'."""
    pass


def parse_magnet_identity(raw: str) -> str | None:
    """40-hex v1 hash from a magnet or bare hash; v2 torchers not supported."""
    if not raw or len(raw) > 32 * 1024:
        return None
    raw = raw.strip()
    if re.fullmatch(r"[a-fA-F0-9]{40}", raw):
        return raw.lower()
    if raw.startswith("magnet:?"):
        match = re.match(r"magnet:\?xt=urn:btih:([a-fA-F0-9]{40})", raw)
        return match.group(1).lower() if match else None
    return None


class TorBox:
    def __init__(self, client, api_key: str):
        self._client = client
        self._key = api_key

    # --- read -------------------------------------------------------------
    def checkcached(self, hashes: list[str], list_files: bool = False) -> dict:
        """Returns {hash: {"cached":bool, "size":long|None, "files":[{name,size}]}}.

        Defensive port of the app's confirmed* implementations: entries that
        are cached!=true are never reported as cached."""
        plain = [h.lower() for h in hashes if re.fullmatch(r"[a-fA-F0-9]{40}", h or "")]
        if not plain:
            return {}
        query = urllib.parse.urlencode({
            "hash": ",".join(sorted(set(plain))),
            "format": "object" if list_files else "list",
            "list_files": "true" if list_files else "false",
        })
        response = self._client.fetch(f"{API_ORIGIN}/torrents/checkcached?{query}",
                                      headers=self._headers(), timeout=30)
        if response.status != 200:
            raise TorBoxError(f"TorBox cache check failed: HTTP {response.status}")
        body = response.read().decode("utf-8", "replace")
        try:
            document = json.loads(body)
        except ValueError as error:
            raise TorBoxError(f"TorBox returned invalid JSON: {error}")
        if document.get("success") is not True:
            raise TorBoxError("TorBox cache verification failed.")
        data = document.get("data") or {}
        result: dict = {}
        plain = set(plain)
        if list_files:
            if isinstance(data, dict):
                for item_hash, value in data.items():
                    if item_hash.lower() in plain:
                        normalised = self._normalised_cached(value, True,item_hash.lower())
                        if normalised.get("cached"):
                            result[item_hash.lower()] = normalised
        else:
            if isinstance(data, list):
                for value in data:
                    if isinstance(value, dict):
                        item_hash = value.get("hash")
                        if isinstance(item_hash, str) and item_hash.lower() in plain:
                            normalised=self._normalised_cached(value,False,item_hash.lower())
                            if normalised['cached']:result[item_hash.lower()]=normalised
                    elif isinstance(value, str) and value.lower() in plain:
                        result[value.lower()] = {"cached": True, "size": None, "files": []}
            elif isinstance(data, dict):
                for item_hash, value in data.items():
                    if item_hash.lower() in plain:
                        normalised = self._normalised_cached(value, False,item_hash.lower())
                        if normalised.get("cached"):
                            result[item_hash.lower()] = normalised
        return result

    @staticmethod
    def _normalised_cached(value, list_files: bool, expected_hash=None) -> dict:
        files: list[dict] = []
        size = None
        if isinstance(value, dict):
            actual=value.get('hash')
            if expected_hash is not None and actual is not None and (not isinstance(actual,str) or actual.lower()!=expected_hash):
                return {"cached": False, "size": None, "files": []}
            if 'cached' in value:
                cached=value['cached'] is True
            else:
                # /checkcached returns only cached members. The documented
                # list/object entries have hash/name/size, with NO cached flag.
                # Require the requested identity and valid metadata shape;
                # never interpret an arbitrary nonempty object as proof.
                cached=(expected_hash is not None and isinstance(actual,str) and actual.lower()==expected_hash
                        and isinstance(value.get('name'),str) and bool(value['name'].strip())
                        and isinstance(value.get('size'),(int,float)) and not isinstance(value.get('size'),bool)
                        and 0<=value['size']<float('inf'))
            if not cached:return {"cached": False, "size": None, "files": []}
            if isinstance(value.get("size"), (int, float)) and value["size"] > 0:
                size = int(value["size"])
            if isinstance(value.get("total_size"), (int, float)) and value["total_size"] > 0 and size is None:
                size = int(value["total_size"])
            array = value.get("files")
            if isinstance(array, list):
                for f in array:
                    if isinstance(f, dict) and isinstance(f.get("name"), str) \
                            and isinstance(f.get("size"), (int, float)) and f["size"] >= 0:
                        files.append({"name": f["name"], "size": int(f["size"])})
        elif isinstance(value, str) and value == "true":
            cached = True
        else:
            cached = value is True
        return {"cached": cached is True, "size": size, "files": files}

    def cached_hashes(self, hashes: list[str]) -> set[str]:
        out=set();unique=list(dict.fromkeys(hashes))
        for start in range(0,len(unique),80):
            out.update(h for h,v in self.checkcached(unique[start:start+80]).items() if v.get('cached') is True)
        return out

    def torrent_sizes(self, hashes: list[str]) -> dict[str, int]:
        out = self.checkcached(hashes, list_files=True)
        sizes: dict[str, int] = {}
        for item_hash, value in out.items():
            if not value.get("cached"):
                continue
            if value.get("size"):
                sizes[item_hash] = int(value["size"])
            elif value["files"]:
                total = sum(int(f["size"]) for f in value["files"])
                if total > 0:
                    sizes[item_hash] = total
        return sizes

    def mylist(self, bypass_cache: bool = True) -> list[dict]:
        """Account reconciliation source (pagination 200 like the filter)."""
        out: list[dict] = []
        offset = 0
        pages=set();deadline=time.monotonic()+60
        while offset<20000:
            if time.monotonic()>deadline:raise TorBoxError("Account listing deadline exceeded")
            query = urllib.parse.urlencode({
                "bypass_cache": "true" if bypass_cache else "false",
                "offset": offset, "limit": 200,
            })
            response = self._client.fetch(f"{API_ORIGIN}/torrents/mylist?{query}",
                                          headers=self._headers(), timeout=30)
            if response.status != 200:
                raise TorBoxError(f"TorBox mylist failed: HTTP {response.status}")
            document = json.loads(response.read().decode("utf-8", "replace"))
            if document.get("success") is not True:
                raise TorBoxError("TorBox mylist failed.")
            batch = document.get('data')
            if not isinstance(batch,list) or any(not isinstance(i,dict) for i in batch):raise TorBoxError('Incomplete TorBox account response')
            import hashlib
            fingerprint=hashlib.sha256(json.dumps(batch,sort_keys=True).encode()).hexdigest()
            if fingerprint in pages:raise TorBoxError('Repeated TorBox account page')
            pages.add(fingerprint)
            out.extend(batch)
            if len(batch) < 200:
                return out
            offset += 200
        raise TorBoxError('TorBox account pagination exceeded its safety limit')

    # --- mutations --------------------------------------------------------
    def add_cached_magnet(self, magnet_raw: str) -> dict:
        """POST /torrents/createtorrent with add_only_if_cached=true.

        Returns {added: bool, id: str|None}: added only when TorBox confirms
        the torrent was already cached and got linked.
        """
        identity = parse_magnet_identity(magnet_raw)
        if not identity:
            raise TorBoxError("invalid magnet")
        body = (
            'add_only_if_cached=true&as_queued=false&not_seed=false&'
            f'magnet={urllib.parse.quote(magnet_raw)}'
        )
        response = self._client.fetch(
            f"{API_ORIGIN}/torrents/createtorrent",
            headers={**self._headers(), "Content-Type": "application/x-www-form-urlencoded"},
            data=body.encode("utf-8"), timeout=30, method="POST")
        return self._interpret_add(response)

    def add_cached_torrent_file(self, name: str, payload: bytes) -> dict:
        from .torrent_metadata import inspect_torrent
        inspect_torrent(payload)
        if any(c in name for c in '\r\n"'):raise TorBoxError('Invalid torrent filename')
        if len(payload) > MAX_TORRENT_BYTES:
            raise TorBoxError("torrent file too large")
        boundary = "STAKwatcherFormBoundary"
        parts: list[bytes] = []
        parts.append(f"--{boundary}\r\nContent-Disposition: form-data; name=\"file\"; "
                     f"filename=\"{name}\"\r\nContent-Type: application/x-bittorrent\r\n\r\n".encode())
        parts.append(payload)
        parts.append(b"\r\n")
        parts.append(f"--{boundary}\r\nContent-Disposition: form-data;"
                     " name=\"add_only_if_cached\"\r\n\r\ntrue\r\n".encode())
        parts.append(f"--{boundary}--\r\n".encode())
        body = b"".join(parts)
        response = self._client.fetch(
            f"{API_ORIGIN}/torrents/createtorrent",
            headers={**self._headers(),
                     "Content-Type": f"multipart/form-data; boundary={boundary}"},
            data=body, timeout=30, method="POST")
        return self._interpret_add(response)

    @staticmethod
    def _interpret_add(response) -> dict:
        if response.status != 200:
            raise UnconfirmedTorrentMutation(f"createtorrent HTTP {response.status}")
        try:
            document = json.loads(response.read().decode("utf-8", "replace"))
        except ValueError as error:
            raise UnconfirmedTorrentMutation(f"TorBox returned invalid JSON: {error}")
        if document.get("success") is not True:
            error_text = str(document.get("error") or document.get("detail") or "")
            if "cached" not in error_text and "no cached" not in error_text:
                raise UnconfirmedTorrentMutation(f"TorBox add failed: {error_text}")
            # not cached on TorBox — server-side enforcement rejected the add
            return {"added": False, "id": None, "detail": "not cached: refused add_only_if_cached"}
        added_id = None
        data = document.get("data")
        if isinstance(data,list):
            if len(data)!=1 or not isinstance(data[0],dict):
                raise UnconfirmedTorrentMutation('TorBox returned ambiguous torrent identities')
            data=data[0]
        if isinstance(data,dict):
            values=[str(data[k]) for k in ('torrent_id','id') if data.get(k) is not None]
            if len(set(values))>1:raise UnconfirmedTorrentMutation('TorBox returned conflicting torrent identities')
            if values:added_id=values[0]
        elif isinstance(data, str):
            added_id = data
        if not added_id:raise UnconfirmedTorrentMutation('TorBox did not confirm the torrent id')
        return {"added": True, "id": added_id, "detail": "added (cached only)"}

    def _headers(self) -> dict:
        return {"Authorization": f"Bearer {self._key}", "Accept": "application/json"}
