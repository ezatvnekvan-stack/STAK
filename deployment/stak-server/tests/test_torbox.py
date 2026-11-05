"""TorBox client tests — fixtures only; the real account is never touched."""

import json

import pytest

from core.torbox import TorBox, TorBoxError, UnconfirmedTorrentMutation


API = "https://api.torbox.app/v1/api"


def route_mylist(client, pages):
    """Queue paginated mylist responses (page size 200)."""
    for index, page in enumerate(pages):
        client.route("torrents/mylist", {"success": True, "data": page},
                     status=200 if index == len(pages) - 1 else 200)


def test_checkcached_list_format(fake_client):
    fake_client.route("torrents/checkcached", {
        "success": True, "data": [
            {"hash": "a" * 40, "cached": True},
            {"hash": "b" * 40, "cached": False},
        ]})
    box = TorBox(fake_client, "k")
    out = box.checkcached(["a" * 40, "c" * 40])  # c not in response at all
    assert set(out) == {"a" * 40}
    assert out["a" * 40]["cached"] is True


def test_checkcached_never_reports_uncached_as_cached(fake_client):
    """cached=false entries are excluded in every response shape."""
    fake_client.route("torrents/checkcached", {
        "success": True, "data": {("d" * 40): {"cached": False, "files": []}}})
    box = TorBox(fake_client, "k")
    out = box.checkcached(["d" * 40])
    assert out == {}


def test_checkcached_dict_with_files(fake_client):
    fake_client.route("torrents/checkcached", {
        "success": True, "data": {("e" * 40): {
            "cached": True, "total_size": 6_000_000_000,
            "files": [{"name": "show s01e01.mkv", "size": 6_000_000_000}]}}})
    box = TorBox(fake_client, "k")
    result = box.checkcached(["e" * 40], list_files=True)
    assert result["e" * 40]["size"] == 6_000_000_000
    assert result["e" * 40]["files"][0]["name"] == "show s01e01.mkv"


def test_checkcached_http_error(fake_client):
    fake_client.route("torrents/checkcached", {"success": False}, status=503)
    box = TorBox(fake_client, "k")
    with pytest.raises(TorBoxError):
        box.checkcached(["f" * 40])


def test_authorization_header(fake_client):
    fake_client.route("torrents/checkcached", {"success": True, "data": []})
    TorBox(fake_client, "secretkey").checkcached(["0" * 40])
    assert fake_client.requests[0]["headers"].get("Authorization") == "Bearer secretkey"


def test_mylist_pagination(fake_client):
    page_one = [{"hash": "1" * 40}] * 200
    page_two = [{"hash": "2" * 40}]
    fake_client.routes = [("offset=0", "gen", 200, {"success": True, "data": page_one}, "GET"),
                          ("offset=200", "gen", 200, {"success": True, "data": page_two}, "GET")]
    box = TorBox(fake_client, "k")
    rows = box.mylist()
    assert len(rows) == 201
    # pagination walked the offset parameter
    urls = [r["url"] for r in fake_client.requests]
    assert any("offset=200" in u for u in urls)


def test_mylist_failure(fake_client):
    fake_client.route("torrents/mylist", {"success": True, "data": []}, status=500)
    with pytest.raises(TorBoxError):
        TorBox(fake_client, "k").mylist()


def test_add_cached_magnet_success_and_body(fake_client):
    fake_client.route("torrents/createtorrent",
                      {"success": True, "data": {"id": 55}})
    box = TorBox(fake_client, "k")
    out = box.add_cached_magnet("magnet:?xt=urn:btih:" + "9" * 40)
    assert out == {"added": True, "id": "55", "detail": "added (cached only)"}
    body = fake_client.bodies[0].decode("utf-8")
    # server-enforced cached-only: the flag MUST travel with every add
    assert "add_only_if_cached=true" in body
    assert "magnet=" in body
    assert fake_client.requests[0]["method"] == "POST"


def test_add_cached_magnet_not_cached(fake_client):
    """Refusal because nothing is on the cache: a false, not an exception."""
    fake_client.route("torrents/createtorrent",
                      {"success": False, "error": "no cached torrent found on "
                       "the server."})
    out = TorBox(fake_client, "k").add_cached_magnet("magnet:?xt=urn:btih:" + "8" * 40)
    assert out["added"] is False


def test_add_cached_magnet_unconfirmed(fake_client):
    """Unexpected API failure >= ambiguous: never a silent false."""
    fake_client.route("torrents/createtorrent",
                      {"success": False, "error": "IP banned until restart."})
    with pytest.raises(UnconfirmedTorrentMutation):
        TorBox(fake_client, "k").add_cached_magnet("magnet:?xt=urn:btih:" + "7" * 40)


def test_add_cached_magnet_http_error_is_unconfirmed(fake_client):
    fake_client.route("torrents/createtorrent", {}, status=500)
    with pytest.raises(UnconfirmedTorrentMutation):
        TorBox(fake_client, "k").add_cached_magnet("magnet:?xt=urn:btih:" + "6" * 40)


def test_add_cached_magnet_string_id(fake_client):
    fake_client.route("torrents/createtorrent", {"success": True, "data": "abc-1"})
    out = TorBox(fake_client, "k").add_cached_magnet("magnet:?xt=urn:btih:" + "5" * 40)
    assert out["id"] == "abc-1"


@pytest.mark.parametrize("data", [{"torrent_id":55},[{"id":55}],{"torrent_id":55,"id":55}])
def test_documented_create_torrent_identity(fake_client,data):
    fake_client.route("torrents/createtorrent",{"success":True,"data":data})
    result=TorBox(fake_client,"fixture").add_cached_magnet("magnet:?xt=urn:btih:"+"a"*40)
    assert result['id']=='55'

@pytest.mark.parametrize("data", [{"torrent_id":55,"id":56},[{"id":55},{"id":56}],{"queued_id":55}])
def test_ambiguous_or_queued_create_is_not_acknowledged(fake_client,data):
    fake_client.route("torrents/createtorrent",{"success":True,"data":data})
    with pytest.raises(UnconfirmedTorrentMutation):
        TorBox(fake_client,"fixture").add_cached_magnet("magnet:?xt=urn:btih:"+"a"*40)
