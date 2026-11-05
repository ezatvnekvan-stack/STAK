"""Documented TorBox /checkcached formats, not synthetic cached:true-only stubs.
Reference: https://api-docs.torbox.app/ — Get Torrent Cached Availability.
All identities/data are fixtures; no live account calls."""
import pytest
from core.torbox import TorBox
from core import pipeline as P
from core.rules import rule_from_dict
from core.store import Store
from conftest import rule_payload,stream_entry

HASH='a'*40
def entry():return {'hash':HASH,'name':'Show.S01E01.2160p.WEB-DL.mkv','size':5_180_000_000}
def response(client,data):client.route('/torrents/checkcached',{'success':True,'error':None,'detail':'Torrent cache status retrieved successfully.','data':data})

def test_documented_list_member_without_cached_flag_is_cached(fake_client):
    response(fake_client,[entry()])
    assert TorBox(fake_client,'fixture').cached_hashes([HASH,'b'*40])=={HASH}

def test_documented_object_member_without_cached_flag_is_cached(fake_client):
    response(fake_client,{HASH:entry()})
    assert TorBox(fake_client,'fixture').cached_hashes([HASH])=={HASH}

def test_documented_file_object_retains_sizes_and_files(fake_client):
    data={**entry(),'files':[{'id':0,'name':'episode.mkv','size':5_180_000_000}]}
    response(fake_client,{HASH:data})
    value=TorBox(fake_client,'fixture').checkcached([HASH],list_files=True)[HASH]
    assert value['cached'] and value['size']==5_180_000_000 and len(value['files'])==1

@pytest.mark.parametrize('marker',[False,None,1,'false','true'])
def test_explicit_invalid_or_false_marker_overrides_membership(fake_client,marker):
    response(fake_client,[{**entry(),'cached':marker}])
    assert TorBox(fake_client,'fixture').cached_hashes([HASH])==set()

def test_foreign_or_inconsistent_hash_is_never_accepted(fake_client):
    response(fake_client,{HASH:{**entry(),'hash':'b'*40}})
    assert TorBox(fake_client,'fixture').cached_hashes([HASH])==set()
    response(fake_client,[entry()])
    assert TorBox(fake_client,'fixture').cached_hashes(['b'*40])==set()

@pytest.mark.parametrize('value',[{},None,{'hash':HASH},{'hash':HASH,'name':'Show','size':'unknown'}])
def test_ambiguous_object_is_not_assumed_cached(fake_client,value):
    response(fake_client,{HASH:value})
    assert TorBox(fake_client,'fixture').cached_hashes([HASH])==set()

def test_pipeline_uses_documented_cache_shape_at_both_checks(tmp_path,fake_client,fake_torbox,fake_filter,fake_jellyfin):
    fake_client.route('/stream/series/tt001:1:1.json',{'streams':[stream_entry('Show.S01E01.2160p.WEB-DL',HASH)]})
    response(fake_client,[entry()])
    # The pipeline's real cache parser is used for both discovery and the
    # immediate pre-add confirmation; only the actual mutation stays a double.
    fake_torbox.cached_hashes=TorBox(fake_client,'fixture').cached_hashes
    store=Store(str(tmp_path/'watch.db'))
    try:
        deps=P.WatcherDeps(store=store,client=fake_client,torbox=fake_torbox,filter_client=fake_filter,jellyfin=fake_jellyfin,
            addons=[('https://addon.invalid/manifest.json','fixture')],torznab=[],filter_scope='fixture')
        store.upsert_episode('r1:S01E001','r1',1,1,'2026-01-01')
        state=P.process_episode(deps,store.episode('r1:S01E001'),rule_from_dict(rule_payload()),'Show','tt001')
        assert state==P.STATUS_ADDED
        assert len(fake_torbox.adds)==1 and len(fake_filter.posts)==1
        assert len([r for r in fake_client.requests if '/checkcached' in r['url']])==2
    finally:store.close()
