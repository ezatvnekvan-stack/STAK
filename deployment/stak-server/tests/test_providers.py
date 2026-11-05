"""Provider parsing/search tests — fixtures only, never network."""

import pytest

from core import providers


def info_hash(name: str) -> str:
    "Unique-looking 40-hex chars from a short name."
    return (name * 20)[:40].encode("ascii").hex()[:40]


def stream_entry(release_name: str, size=8_000_000_000) -> dict:
    """A Stremio-style stream entry the way Torrentio-like addons return them."""
    return {
        "name": "X",
        "title": release_name,
        "infoHash": info_hash(release_name),
        "behaviorHints": {"filename": f"{release_name}.mkv", "videoSize": size},
        "description": release_name,
    }


# --- parse_stream_entry -------------------------------------------------------

def test_stream_entry_requires_identity():
    # no hash, no magnet: a playback URL is never torrent metadata
    entry = {"title": "Show.S01E01", "url": "https://cdn.example/video.m3u8"}
    assert providers.parse_stream_entry(entry, "myaddon") is None


def test_stream_entry_with_info_hash():
    candidate = providers.parse_stream_entry(
        stream_entry("Show.S01E01.2160p.WEB-DL.DV.DTS-HD.Hun.x265"), "myaddon")
    assert candidate is not None
    assert candidate.info_hash == info_hash(
        "Show.S01E01.2160p.WEB-DL.DV.DTS-HD.Hun.x265")
    assert candidate.size_bytes == 8_000_000_000
    assert candidate.provider == "addon:myaddon"
    assert "S01E01" in candidate.release_text


def test_stream_entry_magnet_fallback():
    entry = {"title": "Show.S02E03.1080p.WEBRip",
             "url": "magnet:?xt=urn:btih:" + "b" * 40 + "&dn=x"}
    candidate = providers.parse_stream_entry(entry, "addon2")
    assert candidate is not None
    assert candidate.info_hash == "b" * 40


# --- search_stremio ------------------------------------------------------------

def test_search_stremio_url_and_results(fake_client):
    fake_client.route("/stream/series/tt111:1:1.json", {"streams": [
        stream_entry("Show.S01E01.2160p.WEB-DL")]})
    found, warnings, failures = providers.search_stremio(
        fake_client, [("https://addon.example/manifest.json", "myaddon")],
        "tt111", 1, 1)
    assert failures == []
    assert len(found) == 1
    assert fake_client.requests[0]["url"] == \
        "https://addon.example/stream/series/tt111:1:1.json"


def test_search_stremio_isolates_broken_addon(fake_client):
    fake_client.route("good.example", {"streams": [stream_entry("Show.S02E04.1080p")]})
    # no route for bad.example -> raises -> recorded as failure only
    found, warnings, failures = providers.search_stremio(
        fake_client,
        [("https://bad.example/manifest.json", "bad"),
         ("https://good.example/manifest.json", "good")],
        "tt222", 2, 4)
    assert len(found) == 1
    assert any("bad" in f for f in failures)


def test_search_stremio_all_failed_raises(fake_client):
    fake_client.fail_unmatched = True
    with pytest.raises(providers.ProviderError):
        providers.search_stremio(
            fake_client, [("https://down.example/manifest.json", "down")],
            "tt333", 1, 1)


# --- torznab --------------------------------------------------------------------

NAB_XML = """<?xml version="1.0" encoding="utf-8"?>
<rss><channel>
<item><title>Show.S01E01.2160p.WEB-DL.DV.H.265.HUN</title>
 <guid>https://idx.example/details/101</guid>
 <torznab:attr name="size" value="8000000000"/>
 <torznab:attr name="seeders" value="7"/>
 <torznab:attr name="imdb" value="tt0044"/>
 <torznab:attr name="magneturl" value="magnet:?xt=urn:btih:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"/>
</item>
<item><title>Other.Show.S01E01.1080p.WEB-DL</title>
 <guid>https://idx.example/details/102</guid>
 <torznab:attr name="size" value="4000000000"/>
 <torznab:attr name="imdb" value="tt999"/>
 <link>magnet:?xt=urn:btih:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb</link>
</item>
</channel></rss>"""


def test_parse_nab_response():
    items = providers.parse_nab_response(NAB_XML)
    assert len(items) == 2
    first, second = items
    assert first["hash"] == "a" * 40
    assert first["imdb"] == "tt0044"
    assert first["size"] == 8_000_000_000
    assert second["hash"] == "b" * 40  # magnet in the <link> element


def test_nab_query_url_prefers_imdbid():
    url = providers.nab_query_url("https://idx.example/api", "KEY",
                                  "tt0417299", "Show", 1, 2)
    assert "imdbid=0417299" in url           # no tt prefix, ids not titles
    assert "apikey=KEY" in url
    assert "t=tvsearch" in url
    plain = providers.nab_query_url("https://idx.example/api", "KEY", None, "Show", 1, 2)
    assert "q=Show+S01E02" in plain
    assert "imdbid=" not in plain


def test_search_torznab_filters_wrong_series(fake_client):
    fake_client.route("/api?", NAB_XML)
    found, warnings, failures = providers.search_torznab(
        fake_client, [{"id": "idx", "url": "https://idx.example/api",
                       "apiKey": "KEY", "enabled": True}],
        "tt0044", "Show", 1, 1)
    # the tt999 item is a different series: never matched by title similarity
    assert [c.info_hash for c in found] == ["a" * 40]
    assert failures == []


def test_search_torznab_all_failed_raises(fake_client):
    fake_client.fail_unmatched = True
    with pytest.raises(providers.ProviderError):
        providers.search_torznab(
            fake_client, [{"id": "idx", "url": "https://idx.example/api",
                           "apiKey": "KEY"}], "tt0044", "Show", 1, 1)


def test_search_torznab_isolates_broken_indexer(fake_client):
    fake_client.route("idx.example", NAB_XML)
    found, warnings, failures = providers.search_torznab(
        fake_client,
        [{"id": "idx", "url": "https://idx.example/api", "apiKey": "KEY"},
         {"id": "down", "url": "https://down.example/api", "apiKey": "K2"}],
        "tt0044", "Show", 1, 1)
    assert [c.info_hash for c in found] == ["a" * 40]
    assert [c.provider for c in found] == ["torznab:idx"]
    assert any("down" in f for f in failures)


@pytest.mark.parametrize("with_hash", [True, False])
def test_torznab_magnet_enclosure_is_not_http_metadata(fake_client, with_hash):
    magnet="magnet:?xt=urn:btih:"+"a"*40
    attr='<torznab:attr name="infohash" value="'+"a"*40+'"/>' if with_hash else ''
    xml='<rss><channel><item><title>Show.S01E01.2160p.WEB-DL.HDR</title><guid>fixture</guid><enclosure url="'+magnet+'"/>'+attr+'</item></channel></rss>'
    item=providers.parse_nab_response(xml)[0]
    assert item['hash']=="a"*40
    assert item['magnet']==magnet
    assert item['torrent_url'] is None
    fake_client.route('idx.example', xml)
    found,warnings,failures=providers.search_torznab(fake_client,[{"id":"fixture","url":"https://idx.example/api","apiKey":"fixture"}],"tt0044","Show",1,1)
    assert not failures and len(found)==1
    assert found[0].magnet==magnet and found[0].torrent_url is None
    assert len(fake_client.requests)==1


@pytest.mark.parametrize("enclosure", ["ftp://example.invalid/file.torrent", "file:///tmp/file.torrent", "https://user:password@example.invalid/file.torrent"])
def test_torznab_invalid_enclosure_does_not_reach_http(enclosure):
    xml='<rss><channel><item><title>Show.S01E01</title><guid>fixture</guid><enclosure url="'+enclosure+'"/><link>magnet:?xt=urn:btih:'+"b"*40+'</link></item></channel></rss>'
    item=providers.parse_nab_response(xml)[0]
    assert item['torrent_url'] is None
    assert item['magnet'].startswith('magnet:?')


def test_torznab_private_http_enclosure_preserved_with_magnet():
    url='https://idx.example/private.torrent?apikey=fixture'
    xml='<rss><channel><item><title>Show.S01E01</title><guid>fixture</guid><enclosure url="'+url+'"/><link>magnet:?xt=urn:btih:'+"b"*40+'</link></item></channel></rss>'
    item=providers.parse_nab_response(xml)[0]
    assert item['torrent_url']==url
    assert item['magnet'].startswith('magnet:?')
