"""Integration clients: STAK library filter (approvals) + Jellyfin check.

Both are read/bounded clients modeled on the app:
- filter: Authorization: Bearer <STAK_ACCESS_TOKEN>, no mutation retries,
  protocol/version check via /v1/status
- Jellyfin: read-only episode existence verification. Never writes playback
  state or triggers destructive scans (a scan is only REQUESTED if enabled).
"""

from __future__ import annotations

import json
import re
import urllib.parse


class FilterError(Exception):
    pass


class JellyfinApiError(Exception):
    pass


class LibraryFilter:
    """Client for the existing stak-library-filter service protocol
    (stak-library-filter/1, /v1/status + GET/POST /v1/approvals)."""

    PROTOCOL = "stak-library-filter/1"

    def __init__(self, client, base_url: str, token: str):
        self._client = client
        self._base = base_url.rstrip("/") + "/"
        self._token = token

    def _headers(self) -> dict:
        return {"Authorization": f"Bearer {self._token}", "Accept": "application/json",
                "Connection": "close"}

    def status(self) -> dict:
        response = self._client.fetch(self._base + "v1/status", headers=self._headers(), timeout=25)
        if response.status != 200:
            raise FilterError(f"filter status HTTP {response.status}")
        document = json.loads(response.read().decode("utf-8", "replace"))
        if document.get("protocol") != self.PROTOCOL:
            raise FilterError("unexpected filter protocol")
        return document

    def post_approval(self, torbox_id: str, scope: str, info_hash: str | None,
                      name: str, approved: bool = True) -> dict:
        status=self.status()
        if status.get('scope')!=scope:raise FilterError('Filter account scope mismatch')
        body = {"id": torbox_id, "scope": scope, "name": name, "type": "series",
                "approved": approved}
        if info_hash:
            body["hash"] = info_hash
        payload = json.dumps(body).encode("utf-8")
        response = self._client.fetch(
            self._base + "v1/approvals", headers={**self._headers(),
                                                  "Content-Type": "application/json"},
            data=payload, timeout=25, method="POST")
        if response.status not in (200, 201):
            raise FilterError(f"filter approval HTTP {response.status}")
        return json.loads(response.read().decode("utf-8", "replace"))

    def approvals(self) -> dict:
        response = self._client.fetch(self._base + "v1/approvals", headers=self._headers(), timeout=25)
        if response.status != 200:
            raise FilterError(f"filter approvals HTTP {response.status}")
        return json.loads(response.read().decode("utf-8", "replace"))

    def approval_by_id(self, torbox_id: str) -> dict | None:
        document = self.approvals()
        for row in document.get("items", []):
            if str(row.get("id")) == str(torbox_id):
                return row
        return None


class Jellyfin:
    """Read-only Jellyfin client: verify an episode is actually indexed."""

    def __init__(self, client, base_url: str, user_id: str, token: str):
        self._client = client
        self._base = base_url.rstrip("/") + "/"
        self._user = user_id
        self._token = token

    def _headers(self) -> dict:
        return {"Authorization": f'MediaBrowser Token="{self._token}"',
                "Accept": "application/json"}

    def _get(self, path: str) -> dict | list:
        response = self._client.fetch(self._base + path, headers=self._headers(), timeout=25)
        if response.status != 200:
            raise JellyfinApiError(f"Jellyfin HTTP {response.status}")
        return json.loads(response.read().decode("utf-8", "replace"))

    def find_series_base(self,provider_id,name=''):
        # No title fallback: one similarly named show must never prove identity.
        if not provider_id:return None
        if re.fullmatch(r'[a-fA-F0-9]{32}',provider_id):
            doc=self._get(f'Users/{self._user}/Items/{provider_id}')
            return doc.get('Id') if isinstance(doc,dict) and doc.get('Type')=='Series' else None
        if not re.fullmatch(r'tt[0-9]{3,12}',provider_id):return None
        query=urllib.parse.urlencode({'IncludeItemTypes':'Series','Recursive':'true','Limit':100,
            'AnyProviderIdEquals':'imdb.'+provider_id,'Fields':'ProviderIds'})
        doc=self._get(f'Users/{self._user}/Items?{query}')
        items=doc.get('Items',[]) if isinstance(doc,dict) else []
        matches=[i for i in items if isinstance(i,dict) and str((i.get('ProviderIds') or {}).get('Imdb','')).lower()==provider_id.lower()]
        if len(matches)>1:raise JellyfinApiError('Ambiguous series identity in Jellyfin')
        return matches[0].get('Id') if matches else None

    def episode_present(self, series_id: str, season: int, episode: int,
                        name_hint: str | None = None) -> bool:
        """TmKm — /Shows/{series}/Episodes lookup by season+episode number,
        optionally cross-checked by title to protect against numbering drift."""
        query = urllib.parse.urlencode({"Fields": "ParentIndexNumber,IndexNumber", "Season": season, "UserId": self._user})
        items = self._get(f"Shows/{series_id}/Episodes?{query}")
        episodes = items.get("Items") if isinstance(items, dict) else items
        for item in episodes or []:
            if item.get("ParentIndexNumber") == season and item.get("IndexNumber") == episode:
                if name_hint and "Name" in item:
                    if item["Name"] != name_hint:
                        return False  # numbering drift: never claim a wrong episode
                return True
        return False

    def request_scan(self, series_id: str | None = None) -> None:
        """Optional: request a library scan. Disabled by default config-wise;
        Jellyfin may ignore it. Only called when the user allows it."""
        try:
            body = json.dumps({
                "Id": series_id, "Recursive": True, "Subtitles": False,
                "MetadataRefreshMode": "Default", "ImageRefreshMode": "Default",
            }).encode()
            response=self._client.fetch(
                self._base + f"Items/{series_id}/Refresh" if series_id else
                self._base + "Library/Refresh",
                headers={**self._headers(), "Content-Type": "application/json"},
                data=body, timeout=25, method="POST")
            if not 200<=response.status<300:raise JellyfinApiError("Jellyfin scan was not accepted")
        except Exception as error:  # noqa: BLE001 — scan requests are advisory only
            raise JellyfinApiError(f"scan request failed: {error}")


def filter_scope_hash(torbox_key: str) -> str:
    """sha256("torbox:"+key) — identical to the app + filter scope contract."""
    import hashlib
    return hashlib.sha256(("torbox:" + torbox_key).encode()).hexdigest()
