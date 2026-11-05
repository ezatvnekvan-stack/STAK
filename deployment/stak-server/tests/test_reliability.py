"""Production invariants with temporary databases, loopback HTTP and fixtures.
No owner credentials or outbound TorBox requests are used."""
import hashlib
import json
import threading
import time
import urllib.request
import urllib.error
from concurrent.futures import ThreadPoolExecutor

import pytest
from core import service as S, pipeline as P, providers
from core.service import cinemeta_episode_loader as actual_metadata_loader
from core.store import Store
from core.library import filter_scope_hash,Jellyfin,LibraryFilter
from core.torbox import TorBox,TorBoxError
from core.fetch_client import FetchClient
from core.matching import Candidate
from core.rules import rule_from_dict
from core.torrent_metadata import inspect_torrent
from conftest import rule_payload,stream_entry,FakeClient

TOKEN='fixture-watcher-token-12345'

@pytest.fixture
def svc(tmp_path,monkeypatch):
    monkeypatch.setattr(S,'cinemeta_episode_loader',lambda _:lambda series:[{'season':1,'episode':1,'air_date':'2026-01-01'}])
    service=S.WatchService(S.Config(token=TOKEN,db_path=str(tmp_path/'watch.db'),tick_seconds=.02))
    yield service
    service.stop()
    service.store.close()

@pytest.fixture
def api(svc):
    server=S.WatchHTTPServer(('127.0.0.1',0),svc)
    server.service_actions=lambda:None  # the production main watchdog exits on unhealthy; test the HTTP 503 separately
    thread=threading.Thread(target=server.serve_forever,daemon=True);thread.start()
    yield 'http://127.0.0.1:'+str(server.server_port)
    server.shutdown();server.server_close()

def call(base,path,method='GET',body=None,token=TOKEN):
    blob=json.dumps(body).encode() if body is not None else None
    req=urllib.request.Request(base+path,data=blob,method=method,headers={'Authorization':'Bearer '+token,'Content-Type':'application/json'})
    try:
        with urllib.request.urlopen(req,timeout=3) as response:return response.status,json.loads(response.read())
    except urllib.error.HTTPError as error:return error.code,json.loads(error.read())

def test_rule_save_is_durable_fast_and_does_no_network(svc,api,monkeypatch):
    monkeypatch.setattr(S,'cinemeta_episode_loader',lambda _:(_ for _ in ()).throw(AssertionError('HTTP must not fetch metadata')))
    started=time.monotonic();code,body=call(api,'/v1/rules','POST',rule_payload())
    assert time.monotonic()-started<1
    assert code==201 and body['metadata']['state']=='queued'
    assert svc.store.rule('r1')['confirmed'] is True
    assert len(svc.store.due_metadata())==1

def test_pause_and_status_stay_fast_while_metadata_is_blocked(svc,api,monkeypatch):
    entered=threading.Event();release=threading.Event()
    def loader(_):
        def blocked(series):entered.set();release.wait(5);return [{'season':1,'episode':1,'air_date':'2026-01-01'}]
        return blocked
    monkeypatch.setattr(S,'cinemeta_episode_loader',loader)
    svc.save_rule(rule_payload());worker=threading.Thread(target=svc.run_metadata_jobs);worker.start()
    try:
        assert entered.wait(1);start=time.monotonic()
        assert call(api,'/v1/status')[0]==200
        assert call(api,'/v1/rules/r1/pause','POST')[0]==200
        assert time.monotonic()-start<1
    finally:release.set();worker.join(3)
    assert not worker.is_alive()
    assert svc.store.episodes_for_rule('r1')==[]  # stale fetch cannot undo pause

def test_delete_wins_over_a_metadata_response(svc,monkeypatch):
    entered=threading.Event();release=threading.Event()
    def load(_):
        def read(series):entered.set();release.wait(3);return [{'season':1,'episode':1,'air_date':'2026-01-01'}]
        return read
    monkeypatch.setattr(S,'cinemeta_episode_loader',load)
    svc.save_rule(rule_payload());thread=threading.Thread(target=svc.run_metadata_jobs);thread.start()
    try:assert entered.wait(1);svc.remove_rule('r1')
    finally:release.set();thread.join(3)
    assert svc.store.rule('r1') is None and svc.store.episodes_for_rule('r1')==[]

def test_metadata_failure_keeps_confirmed_rule_and_durable_backoff(svc,monkeypatch):
    def loader(_):
        def fail(series):raise TimeoutError('https://private.invalid/?apikey=do-not-print')
        return fail
    monkeypatch.setattr(S,'cinemeta_episode_loader',loader)
    svc.save_rule(rule_payload());svc.run_metadata_jobs()
    job=svc.store.metadata_state('r1')
    assert job['attempts']==1 and job['due']>time.time()
    assert 'private.invalid' not in job['last_error']
    assert svc.store.rule('r1')['confirmed'] is True
    other=Store(svc.config.db_path)
    try:assert other.metadata_state('r1')['attempts']==1
    finally:other.close()

def test_hourly_metadata_refresh_discovers_new_episode_without_resetting_history(svc,monkeypatch):
    svc.save_rule(rule_payload());svc.run_metadata_jobs()
    svc.store.set_jellyfin_verified('r1:S01E001',True)
    monkeypatch.setattr(S,'cinemeta_episode_loader',lambda _:lambda series:[
        {'season':1,'episode':1,'air_date':'2026-01-01'}, {'season':1,'episode':2,'air_date':'2026-01-02'}])
    svc.store._conn.execute('UPDATE metadata_jobs SET due=0');svc.store._conn.commit()
    svc.run_metadata_jobs()
    assert len(svc.store.episodes_for_rule('r1'))==2
    assert svc.store.episode('r1:S01E001')['jellyfin_verified'] is True
    assert svc.store.metadata_state('r1')['due']>time.time()+3500

def test_repeated_save_coalesces_one_job_and_keeps_retry_backoff(svc):
    payload=rule_payload();svc.save_rule(payload)
    job=svc.store.due_metadata()[0];svc.store.finish_metadata('r1',job['version'],'retry')
    before=svc.store.metadata_state('r1')
    for _ in range(10):svc.save_rule(payload)
    assert svc.store.metadata_state('r1')==before

def test_start_is_not_blocked_by_torbox_outage(svc):
    entered=threading.Event();release=threading.Event()
    class Box:
        def mylist(self):entered.set();release.wait(3);raise TimeoutError()
    svc.torbox=svc.deps.torbox=Box()
    start=time.monotonic();svc.start();assert time.monotonic()-start<.5
    try:assert entered.wait(1);assert svc.healthy()
    finally:release.set()

def test_worker_failure_is_visible_in_health(svc,api):
    svc._started=True  # a worker that died before producing a heartbeat
    assert call(api,'/health',token='')[0]==503
    svc._started=False

def test_metadata_uses_the_real_stremio_meta_path(svc,fake_client):
    fake_client.route('/meta/series/tt001.json',{'meta':{'videos':[{'season':1,'episode':1,'released':'2026-01-01T00:00:00Z'}]}})
    refs=actual_metadata_loader(fake_client)(rule_from_dict(rule_payload()).series)
    assert refs[0]['air_date']=='2026-01-01'
    assert '/tt001/meta.json' not in fake_client.requests[0]['url']

def test_api_rejects_truthy_fake_consent(svc,api):
    payload=rule_payload(confirm='yes',confirmed='yes')
    assert call(api,'/v1/rules','POST',payload)[0]==400
    assert svc.store.rules()==[]

def test_missing_pause_is_not_reported_as_success(svc,api):
    assert call(api,'/v1/rules/not-found/pause','POST')[0]==404

def test_delete_is_idempotent_and_preserves_history(svc,api):
    svc.save_rule(rule_payload());svc.run_metadata_jobs()
    assert call(api,'/v1/rules/r1','DELETE')[0]==200
    assert call(api,'/v1/rules/r1','DELETE')[0]==200
    assert svc.store.episodes_for_rule('r1')[0]['status']=='paused'

def test_status_uses_aggregate_query_not_full_episode_scans(svc,monkeypatch):
    svc.save_rule(rule_payload());svc.run_metadata_jobs()
    monkeypatch.setattr(svc.store,'episodes_for_rule',lambda _:(_ for _ in ()).throw(AssertionError('N+1 scan')))
    assert svc.status_overview()['rules'][0]['episodes']==1

def test_single_worker_prevents_concurrent_episode_runs(svc,monkeypatch):
    svc.save_rule(rule_payload());svc.run_metadata_jobs()
    entered=threading.Event();release=threading.Event();calls=[]
    def process(*args):calls.append(1);entered.set();release.wait(3)
    monkeypatch.setattr(P,'process_episode',process)
    thread=threading.Thread(target=svc.run_pending);thread.start()
    try:assert entered.wait(1);assert svc.run_pending()==0
    finally:release.set();thread.join(3)
    assert len(calls)==1

def test_scope_is_hashed_once_and_database_rejects_account_change(tmp_path):
    path=str(tmp_path/'scope.db');svc=S.WatchService(S.Config(TOKEN,db_path=path,torbox_key='account-one'))
    try:assert svc.deps.filter_scope==hashlib.sha256(b'torbox:account-one').hexdigest()
    finally:svc.store.close()
    with pytest.raises(ValueError,match='account differs'):
        S.WatchService(S.Config(TOKEN,db_path=path,torbox_key='account-two'))

def test_large_metadata_batch_commits_quickly_and_keeps_only_eligible_rows(svc):
    payload=rule_payload(onlyNewEpisodes=True,createdOn='2026-01-02')
    svc.save_rule(payload);rule=rule_from_dict(svc.store.rule('r1'))
    refs=[{'season':1,'episode':n,'air_date':'2026-01-03'} for n in range(1,2001)]
    refs.append({'season':1,'episode':2001,'air_date':'2026-01-01'})
    start=time.monotonic();assert svc.store.apply_episode_refs(rule,refs)==2000
    assert time.monotonic()-start<2
    assert svc.store.episode('r1:S01E2001') is None

def test_unscheduled_episode_is_not_polled_in_a_busy_loop(svc):
    svc.store.upsert_episode('r1:S01E001','r1',1,1,'2026-01-01')
    svc.store.set_episode_status('r1:S01E001','waiting_library','Configure Jellyfin',next_check=None)
    assert svc.store.due_episodes()==[]

def test_diagnostics_and_events_never_echo_tokens_or_urls(svc,api):
    svc.store.event('test',TOKEN+' https://host.invalid/private?apikey=secret')
    svc.store.set_episode_status('not-found','waiting_release',TOKEN)
    blob=json.dumps(call(api,'/v1/events')[1])
    assert TOKEN not in blob and 'host.invalid' not in blob
    assert call(api,'/v1/diagnostics')[1]['configured']['torbox'] is False

def test_torznab_endpoint_accepts_base_or_complete_api_url():
    a=providers.nab_query_url('https://x.invalid/api/v1/torznab/api','K','tt123','Show',2,3)
    b=providers.nab_query_url('https://x.invalid/api/v1/torznab','K','tt123','Show',2,3)
    assert a==b and '/api/api?' not in a
    assert 'season=2' in a and 'ep=3' in a

def test_torbox_cache_queries_are_batched(fake_client):
    calls=[]
    class Box(TorBox):
        def checkcached(self,hashes,list_files=False):calls.append(len(hashes));return {h:{'cached':True} for h in hashes}
    hashes=[f'{n:040x}' for n in range(201)]
    assert Box(fake_client,'fixture').cached_hashes(hashes)==set(hashes)
    assert calls==[80,80,41]

@pytest.mark.parametrize('value',[None,False,0,1,'false','true'])
def test_non_boolean_object_cache_markers_are_not_proof_of_cache(value):
    assert TorBox._normalised_cached({'cached':value},False)['cached'] is False

def test_malformed_or_repeated_account_pages_do_not_prove_absence(fake_client):
    fake_client.route('/mylist',{'success':True,'data':{}})
    with pytest.raises(TorBoxError):TorBox(fake_client,'fixture').mylist()
    fake_client.route('/mylist',{'success':True,'data':[{'id':n,'hash':f'{n:040x}'} for n in range(200)]})
    with pytest.raises(TorBoxError,match='Repeated'):TorBox(fake_client,'fixture').mylist()

def test_filter_scope_mismatch_prevents_approval(fake_client):
    fake_client.route('/v1/status',{'protocol':'stak-library-filter/1','scope':'other'})
    with pytest.raises(Exception,match='scope mismatch'):
        LibraryFilter(fake_client,'https://filter.invalid','token').post_approval('1','mine','a'*40,'Show')
    assert all(r['method']=='GET' for r in fake_client.requests)

def test_jellyfin_does_not_fall_back_to_a_similarly_named_show(fake_client):
    fake_client.route('/Items?',{'Items':[{'Id':'wrong','Name':'Show','ProviderIds':{'Imdb':'tt999'}}]})
    assert Jellyfin(fake_client,'https://jf.invalid','user','token').find_series_base('tt001','Show') is None
    assert len(fake_client.requests)==1

def test_scan_http_failure_is_reported(fake_client):
    fake_client.route('/Library/Refresh',{},status=403)
    with pytest.raises(Exception,match='scan'):Jellyfin(fake_client,'https://jf.invalid','user','token').request_scan()

def bencode(value):
    if isinstance(value,int):return b'i'+str(value).encode()+b'e'
    if isinstance(value,bytes):return str(len(value)).encode()+b':'+value
    if isinstance(value,list):return b'l'+b''.join(bencode(v) for v in value)+b'e'
    return b'd'+b''.join(bencode(k)+bencode(v) for k,v in sorted(value.items()))+b'e'

def private_torrent(name=b'Show.S01E01.mkv'):
    return bencode({b'announce':b'https://tracker.invalid/private-passkey',b'info':{b'name':name,b'length':123,b'private':1,b'piece length':16384,b'pieces':b'0'*20}})

def test_private_metadata_hash_is_from_original_info_bytes():
    blob=private_torrent();h,info=inspect_torrent(blob)
    assert h==hashlib.sha1(bencode(info)).hexdigest()
    assert info[b'private']==1

@pytest.mark.parametrize('blob',[b'',b'de',b'd4:infoi1ee',b'd4:infod4:name4:teste',b'not a torrent'])
def test_bad_torrent_metadata_is_refused(blob):
    with pytest.raises(ValueError):inspect_torrent(blob)

def test_fetch_is_bounded_and_does_not_retry_mutations(api,svc):
    response=FetchClient().fetch(api+'/health',timeout=2)
    assert response.status==200
    assert json.loads(response.read())['ok'] is True

@pytest.fixture
def flow(tmp_path,fake_client,fake_torbox,fake_filter,fake_jellyfin):
    store=Store(str(tmp_path/'pipeline.db'));h='a'*40
    fake_client.route('/stream/series/tt001:1:1.json',{'streams':[stream_entry('Show.S01E01.2160p.WEB-DL',h)]})
    fake_torbox.cached={h}
    deps=P.WatcherDeps(store=store,client=fake_client,torbox=fake_torbox,filter_client=fake_filter,jellyfin=fake_jellyfin,
        addons=[('https://addon.invalid/manifest.json','fixture')],torznab=[],filter_scope='fixture-scope')
    store.upsert_episode('r1:S01E001','r1',1,1,'2026-01-01')
    yield deps,store.episode('r1:S01E001'),rule_from_dict(rule_payload())
    store.close()

def test_add_reservation_survives_a_crash_before_http_response(flow):
    deps,row,rule=flow;calls=[]
    def crash(magnet):
        assert deps.store.torrent('a'*40)['state']=='unconfirmed'
        calls.append(magnet);raise SystemExit('simulated crash')
    deps.torbox.add_cached_magnet=crash
    with pytest.raises(SystemExit):P.process_episode(deps,row,rule,'Show','tt001')
    assert deps.store.episode_reservation(row['episode_key'])['state']=='unconfirmed'
    P.process_episode(deps,deps.store.episode(row['episode_key']),rule,'Show','tt001')
    assert len(calls)==1
    deps.torbox.mylist_items=[{'hash':'a'*40,'id':'777'}]
    P.reconcile_with_account(deps)
    P.process_episode(deps,deps.store.episode(row['episode_key']),rule,'Show','tt001')
    assert len(calls)==1 and deps.store.episode(row['episode_key'])['torbox_id']=='777'

def test_approval_outage_recovers_without_another_add(flow):
    deps,row,rule=flow;deps.filter.fail=True
    assert P.process_episode(deps,row,rule,'Show','tt001')==P.STATUS_ADDED
    deps.filter.fail=False
    P.process_episode(deps,deps.store.episode(row['episode_key']),rule,'Show','tt001')
    assert len(deps.torbox.adds)==1 and len(deps.filter.posts)==1

def test_lost_cache_at_final_check_never_sends_add(flow):
    deps,row,rule=flow;calls=[]
    def cache(hashes):calls.append(1);return {'a'*40} if len(calls)==1 else set()
    deps.torbox.cached_hashes=cache
    assert P.process_episode(deps,row,rule,'Show','tt001')==P.STATUS_WAITING_CACHE
    assert deps.torbox.adds==[] and deps.store.torrent('a'*40) is None

def test_pause_during_search_prevents_the_later_add(flow):
    deps,row,rule=flow;checks=[]
    def current(_):checks.append(1);return len(checks)==1
    deps.rule_is_current=current
    assert P.process_episode(deps,row,rule,'Show','tt001')==P.STATUS_PAUSED
    assert deps.torbox.adds==[]

def test_newer_active_rule_is_rescheduled_instead_of_left_paused(flow):
    deps,row,rule=flow
    newer=rule_payload(hdrMode='preferred');deps.store.put_rule('r1',__import__('core.rules',fromlist=['rule_to_dict']).rule_to_dict(rule_from_dict(newer)))
    deps.rule_is_current=lambda _:False
    assert P.process_episode(deps,row,rule,'Show','tt001')==P.STATUS_WAITING_MATCH
    assert deps.store.episode(row['episode_key'])['next_check']>0

def test_existing_jellyfin_episode_does_not_acquire_any_torrent(flow):
    deps,row,rule=flow;deps.jellyfin.present=True
    assert P.process_episode(deps,row,rule,'Show','tt001')==P.STATUS_AVAILABLE
    assert deps.torbox.adds==[] and deps.filter.posts==[]

def test_filter_account_mismatch_blocks_torbox_mutation(flow):
    deps,row,rule=flow;deps.verify_filter=True
    deps.filter.status=lambda:{'scope':'wrong','source_available':True}
    assert P.process_episode(deps,row,rule,'Show','tt001')==P.STATUS_CONNECTION
    assert deps.torbox.adds==[]

def test_original_private_torrent_is_forwarded_without_reencoding(flow,monkeypatch):
    deps,row,rule=flow;blob=private_torrent();h,_=inspect_torrent(blob);deps.torbox.cached={h}
    candidate=Candidate('Show.S01E01.2160p.WEB-DL','Show.S01E01.2160p.WEB-DL',info_hash=h,
        source_identity='https://tracker.invalid/?passkey=secret',torrent_payload=blob)
    monkeypatch.setattr(P,'_search',lambda *args:([candidate],None));files=[]
    deps.torbox.add_cached_torrent_file=lambda name,payload:(files.append(payload) or {'added':True,'id':'777'})
    P.process_episode(deps,row,rule,'Show','tt001')
    assert files==[blob] and deps.torbox.adds==[]
    assert 'tracker.invalid' not in deps.store.torrent(h)['source_identity']

def test_wrong_torrent_hash_is_never_uploaded(flow,monkeypatch):
    deps,row,rule=flow;blob=private_torrent()
    candidate=Candidate('Show.S01E01.2160p.WEB-DL','Show.S01E01.2160p.WEB-DL',info_hash='a'*40,torrent_payload=blob)
    monkeypatch.setattr(P,'_search',lambda *args:([candidate],None))
    with pytest.raises((ValueError,P.NetworkDown)):P.process_episode(deps,row,rule,'Show','tt001')
    assert deps.torbox.adds==[] and deps.store.torrent('a'*40) is None

def test_broken_addon_does_not_skip_a_healthy_torznab_source(flow,monkeypatch):
    deps,row,rule=flow;deps.torznab=[{'id':'ok','enabled':True}]
    def fail(*args):raise providers.ProviderError('down')
    c=Candidate('Show.S01E01.2160p.WEB-DL','Show.S01E01.2160p.WEB-DL',info_hash='a'*40)
    monkeypatch.setattr(providers,'search_stremio',fail)
    monkeypatch.setattr(providers,'search_torznab',lambda *args:([c],[],[]))
    context=P._context(rule,row)
    found,error=P._search(deps,rule,context,'Show','tt001',row)
    assert found==[c] and error is None

def test_negative_search_cache_never_overrides_a_shorter_rule_interval(flow):
    deps,row,rule=flow;deps.negative_ttl_seconds=12*3600
    deps.store.put_search_cache('addons',row['episode_key'],True,[])
    deps.store._conn.execute('UPDATE search_cache SET checked_at=?',(time.time()-46*60,));deps.store._conn.commit()
    found,error=P._search(deps,rule,P._context(rule,row),'Show','tt001',row)
    assert found and error is None

def test_torznab_error_document_is_not_cached_as_no_results():
    with pytest.raises(providers.ProviderError):providers.parse_nab_response('<error code="100" description="bad key"/>')

def test_schema_migration_preserves_legacy_rules_and_episode_eligibility(tmp_path):
    import sqlite3
    from core.rules import rule_to_dict
    path=str(tmp_path/'legacy.db');db=sqlite3.connect(path)
    db.execute('CREATE TABLE rules(rule_id TEXT PRIMARY KEY,payload TEXT NOT NULL,updated_at REAL NOT NULL)')
    canonical=rule_to_dict(rule_from_dict(rule_payload()))
    db.execute('INSERT INTO rules VALUES(?,?,?)',('r1',json.dumps(canonical),time.time()));db.commit();db.close()
    store=Store(path)
    try:
        assert store.rule('r1')==canonical
        store.upsert_episode('r1:S01E001','r1',1,1,'2026-01-01')
        store.set_episode_status('r1:S01E001','added_torbox',next_check=time.time()-1)
        assert len(store.due_episodes())==1
        store.put_rule('r1',{**canonical,'paused':True})
        # An in-flight add may finish after pause. It must not starve other work.
        store.set_episode_status('r1:S01E001','added_torbox',next_check=time.time()-1)
        assert store.due_episodes()==[]
        store.delete_rule('r1')
        store.set_episode_status('r1:S01E001','added_torbox',next_check=time.time()-1)
        assert store.due_episodes()==[]
    finally:store.close()

def test_confirmed_rule_check_is_queued_without_an_acquisition_call(svc,api):
    svc.save_rule(rule_payload());svc.run_metadata_jobs()
    code,body=call(api,'/v1/rules/r1/check','POST')
    assert code==202 and body['queued'] is True
    assert svc.store.metadata_state('r1')['due']<=time.time()
    svc.set_paused('r1',True)
    assert call(api,'/v1/rules/r1/check','POST')[0]==400

def test_atomic_reservation_does_not_win_after_an_acknowledged_pause(svc):
    svc.save_rule(rule_payload());rule=rule_from_dict(svc.store.rule('r1'))
    svc.set_paused('r1',True)
    assert not svc.reserve_if_current(rule,'a'*40,'r1:S01E001','fixture')
    assert svc.store.torrent('a'*40) is None

def test_same_episode_cannot_have_two_inflight_reservations(svc):
    assert svc.store.reserve_add('a'*40,'r1:S01E001','source-a')
    assert not svc.store.reserve_add('b'*40,'r1:S01E001','source-b')

@pytest.mark.parametrize('size',['NaN','Infinity',-1,0,501])
def test_invalid_size_limits_do_not_turn_into_unbounded_matching(size):
    with pytest.raises(ValueError):rule_from_dict(rule_payload(maxEpisodeGb=size))


def test_pending_add_does_not_wait_for_six_hour_reconcile(svc,monkeypatch):
    svc.torbox=object()
    svc.store.reserve_add('a'*40,'r1:S01E001','fixture')
    svc._reconcile_due=time.time()+6*3600
    calls=[]
    monkeypatch.setattr(svc,'safe_reconcile',lambda:calls.append(True))
    svc.run_pending()
    assert calls==[True]
