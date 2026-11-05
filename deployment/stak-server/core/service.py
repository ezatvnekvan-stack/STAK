"""Watch watcher service: HTTP API + serialized scheduler.

Endpoints (Bearer `STAK_WATCHER_TOKEN`, Authorization header required):

    GET    /health                     liveness (no auth; no sensitive data)
    GET    /v1/status                  rules overview + per-status counters
    GET    /v1/rules                   stored rule payloads
    POST   /v1/rules                   save + validate a rule, sync episode rows
    DELETE /v1/rules/{rule_id}         pause rule (history is kept, never deleted)
    POST   /v1/rules/{rule_id}/pause   pause all pending episodes of the rule
    POST   /v1/rules/{rule_id}/resume  resume with the rule's check interval
    GET    /v1/episodes?rule_id=...    episode rows and statuses
    POST   /v1/reconcile               reconcile ledger against the TorBox account

Design rules:
- one scheduler thread processes due episodes serially and gently;
- startup reconciles the ledger against the real account once;
- the service never deletes account-side links and never mutates Jellyfin
  watched state;
- configuration comes from environment variables; secrets never appear in
  logs, events, or API responses.
"""

from __future__ import annotations

import json
import os
import threading
import time
from dataclasses import dataclass, field
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

from . import pipeline, providers, rules as rules_module
from .fetch_client import FetchClient
from .library import FilterError, JellyfinApiError, Jellyfin, LibraryFilter, filter_scope_hash
from .store import Store, now
from .torbox import TorBox

VERSION = "1.1.4"
MAX_EPISODES_PER_TICK = 6
RECONCILE_INTERVAL_SECONDS = 6 * 3600


@dataclass
class Config:
    """Environment-derived configuration. Secrets stay in memory only."""

    token: str = field(repr=False)
    db_path: str = "/data/stak-watch.db"
    bind_host: str = "0.0.0.0"
    bind_port: int = 8055
    addons: list[tuple[str, str]] = field(default_factory=list,repr=False)   # (base_url, name)
    torznab: list[dict] = field(default_factory=list,repr=False)             # id/url/apiKey/enabled
    torbox_key: str = field(default="",repr=False)
    filter_base: str = ""
    filter_token: str = field(default="",repr=False)
    jellyfin_base: str = ""
    jellyfin_user: str = ""
    jellyfin_token: str = field(default="",repr=False)
    jellyfin_scan_enabled: bool = False
    negative_ttl_seconds: float = 15 * 60
    tick_seconds: float = 20.0

    @staticmethod
    def parse_addons(raw: str) -> list[tuple[str, str]]:
        """`Name|https://manifest.example/manifest.json` entries, comma separated."""
        out: list[tuple[str, str]] = []
        for part in raw.split(","):
            part = part.strip()
            if not part:
                continue
            if "|" in part:
                name, _, base = part.partition("|")
                name = name.strip()
                base = base.strip()
            else:
                name, base = "", part
            from .security import service_url
            base=service_url(base)
            name = name or base.split("//", 1)[1].split("/")[0]
            if not base.rstrip("/").endswith("/manifest.json"):
                raise ValueError("Addon URL must end with /manifest.json")
            out.append((base.rstrip("/") + "/", name))
        return out

    @staticmethod
    def parse_torznab(path: str) -> list[dict]:
        """JSON file of {"id", "url", "apiKey", "enabled"?}; kept out of Git."""
        if not path:
            return []
        with open(path, "r", encoding="utf-8") as handle:
            data = json.load(handle)
        if not isinstance(data, list):
            raise ValueError("torznab file must contain a JSON array")
        out: list[dict] = []
        for row in data:
            if not isinstance(row, dict) or not row.get("id") or not row.get("url"):
                continue
            out.append({
                "id": str(row["id"]), "url": str(row["url"]).rstrip("/"),
                "apiKey": str(row.get("apiKey", "")),
                "enabled": bool(row.get("enabled", True)),
            })
        return out

    @classmethod
    def from_env(cls, env=None) -> "Config":
        env = os.environ if env is None else env
        token = env.get("STAK_WATCHER_TOKEN", "")
        if not token:
            raise ValueError("STAK_WATCHER_TOKEN is required (bearer token for the API)")
        addons_raw = env.get("STAK_ADDONS", "")
        config = cls(
            token=token,
            db_path=env.get("STAK_DB_PATH", "/data/stak-watch.db"),
            bind_host=env.get("STAK_BIND_HOST", "0.0.0.0"),
            bind_port=int(env.get("STAK_BIND_PORT", "8055")),
            addons=cls.parse_addons(addons_raw),
            torznab=cls.parse_torznab(env.get("STAK_TORZNAB_FILE", "")),
            torbox_key=env.get("STAK_TORBOX_KEY", ""),
            filter_base=env.get("STAK_FILTER_URL", ""),
            filter_token=env.get("STAK_FILTER_TOKEN", ""),
            jellyfin_base=env.get("STAK_JELLYFIN_URL", ""),
            jellyfin_user=env.get("STAK_JELLYFIN_USER", ""),
            jellyfin_token=env.get("STAK_JELLYFIN_TOKEN", ""),
            jellyfin_scan_enabled=env.get("STAK_JELLYFIN_SCAN", "0") == "1",
            negative_ttl_seconds=float(env.get("STAK_NEGATIVE_TTL_HOURS", "0.25")) * 3600,
            tick_seconds=float(env.get("STAK_TICK_SECONDS", "20")),
        )
        if not 1<=config.bind_port<=65535 or not 1<=config.tick_seconds<=300 or not 0<=config.negative_ttl_seconds<=86400:
            raise ValueError("Invalid port or scheduler interval")
        if len(token)<12 or len(token)>4096 or any(ord(c)<33 or ord(c)>126 for c in token):
            raise ValueError("Watcher token must be at least 12 printable characters without spaces")
        from .security import service_url
        for attr in ('filter_base','jellyfin_base'):
            if getattr(config,attr):setattr(config,attr,service_url(getattr(config,attr)))
        return config


def cinemeta_episode_loader(client):
    """Metadata loader for `pipeline.sync_rule_episodes`.

    Uses the official Cinemeta series meta (private, read-only, no API key):
    https://v3-cinemeta.strem.io/meta/series/{imdb}/meta.json. A rule without
    an IMDb id cannot be synced (never guessed from the title).
    """

    def loader(series) -> list[dict]:
        if not series.imdb:
            raise ValueError(
                "rule has no IMDb id; episode sync requires the series IMDb id "
                "(tmdb/tvdb-only rules are not supported by the metadata source)")
        url = f"https://v3-cinemeta.strem.io/meta/series/{series.imdb}.json"
        response = client.fetch(url, headers={"Accept": "application/json"}, timeout=30)
        if response.status != 200:
            raise providers.ProviderError(f"Cinemeta metadata HTTP {response.status}")
        document = json.loads(response.read().decode("utf-8", "replace"))
        if not isinstance(document,dict) or not isinstance(document.get("meta"),dict):
            raise providers.ProviderError("Invalid metadata response")
        meta = document.get("meta") or {}
        out: list[dict] = []
        videos=meta.get('videos') or []
        if not isinstance(videos,list):raise providers.ProviderError('Invalid episode list')
        for video in videos[:10000]:
            if not isinstance(video,dict):continue
            season, number = video.get("season"), video.get("episode")
            if not isinstance(season, int) or not isinstance(number, int):
                continue  # malformed entries are skipped, never guessed
            released = video.get("released") or video.get("firstAired") or ""
            out.append({"season": season, "episode": number, "air_date": released[:10] if isinstance(released,str) and released else None})
        return out

    return loader


class WatchService:
    """HTTP writes only persist intent. One worker owns all external operations."""
    def __init__(self, config):
        from .security import Redactor
        self.config=config
        secrets=[config.token,config.torbox_key,config.filter_token,config.jellyfin_token]
        secrets += [x.get('apiKey','') for x in config.torznab]
        self.redact=Redactor(secrets)
        self.store=Store(config.db_path, self.redact)
        self._stop=threading.Event(); self._wake=threading.Event()
        self._work=threading.Lock(); self._control=threading.RLock()
        self._started=False; self._heartbeat=time.monotonic(); self._failures=0
        self._last_reconcile=0.; self._reconcile_due=0.
        self._last_maintenance=0.
        self._state={'worker':'idle','metadata':'unknown','torbox':'unknown','filter':'unknown','jellyfin':'unknown'}
        client=FetchClient(on_activity=self.touch,cancelled=self._stop.is_set)
        self.torbox=TorBox(client,config.torbox_key) if config.torbox_key else None
        self.filter_client=LibraryFilter(client,config.filter_base,config.filter_token) if config.filter_base else None
        self.jellyfin=Jellyfin(client,config.jellyfin_base,config.jellyfin_user,config.jellyfin_token) if all((config.jellyfin_base,config.jellyfin_user,config.jellyfin_token)) else None
        if config.jellyfin_scan_enabled and self.jellyfin is None:
            raise ValueError('Jellyfin scan requires URL, user id and token')
        scope=filter_scope_hash(config.torbox_key) if config.torbox_key else ''
        saved_scope=self.store.get_meta('torbox_account_scope')
        if scope and saved_scope and scope!=saved_scope:
            raise ValueError('TorBox account differs from this database. Restore the original account configuration; history was not changed.')
        if scope:self.store.set_meta('torbox_account_scope',scope)
        self.deps=pipeline.WatcherDeps(store=self.store,client=client,torbox=self.torbox,filter_client=self.filter_client,
            jellyfin=self.jellyfin,addons=config.addons,torznab=config.torznab,jellyfin_scan_enabled=config.jellyfin_scan_enabled,
            filter_scope=scope,negative_ttl_seconds=config.negative_ttl_seconds)
        self.deps.verify_filter=True
        self.deps.rule_is_current=self.rule_is_current
        self.deps.reserve_if_current=self.reserve_if_current
        self.deps.reuse_if_current=self.reuse_if_current
        self._scheduler=threading.Thread(target=self._scheduler_loop,name='stak-watch-worker',daemon=True)

    def touch(self): self._heartbeat=time.monotonic()

    def healthy(self):
        return not self._started or (self._scheduler.is_alive() and time.monotonic()-self._heartbeat<300 and self._failures<3)

    def start(self):
        if self._started:return
        self.store.ensure_metadata_jobs()
        for rule in self.store.rules():
            if rule.get("enabled") and not rule.get("paused"):self.store.reset_rule_schedule(rule["ruleId"])
        self.store.event('service_start',f'stak-watch/{VERSION}')
        self._started=True; self.touch(); self._scheduler.start(); self._wake.set()

    def stop(self):
        self._stop.set();self._wake.set()
        if self._started and self._scheduler is not threading.current_thread():self._scheduler.join(timeout=35)

    def _scheduler_loop(self):
        while not self._stop.is_set():
            self._wake.wait(self.config.tick_seconds);self._wake.clear()
            if self._stop.is_set():break
            self.touch()
            try:
                self.run_pending();self._failures=0
            except Exception as error:
                self._failures+=1
                try:self.store.event('worker_error',self.redact(type(error).__name__+': '+str(error)))
                except Exception:pass
                self._stop.wait(min(5*self._failures,30))
            finally:self.touch()

    def rule_is_current(self, rule):
        if self._stop.is_set():return False
        current=self.store.rule(rule.rule_id)
        return bool(current and current.get('enabled') and current.get('confirmed') is True and not current.get('paused') and
            self.store.version(current)==self.store.version(rules_module.rule_to_dict(rule)))

    def reserve_if_current(self,rule,info_hash,key,identity):
        with self._control:
            if not self.rule_is_current(rule):return False
            return self.store.reserve_add(info_hash,key,identity)

    def reuse_if_current(self,rule,info_hash,key,torbox_id):
        with self._control:
            if not self.rule_is_current(rule):return False
            return self.store.attach_owned(info_hash,key,torbox_id)

    def run_metadata_jobs(self, limit=2):
        count=0
        for job in self.store.due_metadata(limit):
            if self._stop.is_set():break
            payload=self.store.rule(job['rule_id'])
            if not payload or payload.get('paused') or not payload.get('enabled') or payload.get('confirmed') is not True:
                self.store.finish_metadata(job['rule_id'],job['version']);continue
            if self.store.version(payload)!=job['version']:
                self.store.put_rule(payload['ruleId'],payload,queue_metadata=True);continue
            self.touch();self._state['worker']='metadata'
            try:
                rule=rules_module.rule_from_dict(payload)
                refs=cinemeta_episode_loader(self.deps.client)(rule.series)
                # Network ran outside the control lock. A pause/edit/delete during
                # that request wins; an old response cannot resurrect the old rule.
                with self._control:
                    if self.rule_is_current(rule):
                        pipeline.sync_rule_episodes(self.deps,payload,lambda _:refs)
                        self.store.finish_metadata(rule.rule_id,job['version'])
                        self._state['metadata']='ok';count+=1
            except Exception as error:
                self.store.finish_metadata(job['rule_id'],job['version'],'Metadata unavailable; retry scheduled ('+type(error).__name__+')')
                self.store.event('metadata_retry',type(error).__name__,job['rule_id'])
                self._state['metadata']='retrying'
            finally:self.touch()
        return count

    def run_pending(self):
        if not self._work.acquire(blocking=False):return 0
        try:
            self.run_metadata_jobs()
            # A new ambiguous mutation must not inherit the six-hour idle
            # account scan schedule. Reconcile on the next worker tick.
            if self.store.torrents_unconfirmed() and self._reconcile_due>now()+60:
                self._reconcile_due=now()
            if self.torbox is not None and now()>=self._reconcile_due:
                self.safe_reconcile()
            count=0
            for row in self.store.due_episodes(limit=MAX_EPISODES_PER_TICK):
                if self._stop.is_set():break
                payload=self.store.rule(row['rule_id'])
                if not payload or payload.get('paused') or not payload.get('enabled'):continue
                self.touch();self._state['worker']='episodes'
                try:
                    rule=rules_module.rule_from_dict(payload)
                    pipeline.process_episode(self.deps,row,rule,rule.series.name,rule.series.imdb)
                except Exception as error:
                    self.store.event('episode_error',type(error).__name__ + (': '+str(error) if type(error).__name__=='FetchFailure' else ''),row['episode_key'])
                    self.store.set_episode_status(row['episode_key'],pipeline.STATUS_CONNECTION,
                        'A service request failed; retry scheduled',next_check=now()+min(60*2**min(row.get('attempts',0),6),3600),
                        attempts=row.get('attempts',0)+1,last_check=True)
                finally:self.touch()
                count+=1
                if self._stop.wait(.2):break
            self._state['worker']='idle'
            if now()-self._last_maintenance>3600:
                self.store.checkpoint();self._last_maintenance=now()
            return count
        finally:self._work.release()

    def save_rule(self,payload):
        import re
        if payload.get('confirm') is not True and payload.get('confirmed') is not True:raise ValueError('Auto-add must be explicitly confirmed')
        rule=rules_module.rule_from_dict(payload)
        if not re.fullmatch(r'[A-Za-z0-9_-]{1,160}',rule.rule_id):raise ValueError('Invalid rule id')
        if not re.fullmatch(r'tt\d{3,12}',rule.series.imdb or ''):raise ValueError('A series IMDb id is required')
        if not rule.confirmed:raise ValueError('Auto-add must be explicitly confirmed')
        canonical=rules_module.rule_to_dict(rule)
        with self._control:
            if self.store.rule(rule.rule_id) is None and len(self.store.rules())>=200:raise ValueError('Maximum of 200 rules reached')
            self.store.put_rule(rule.rule_id,canonical,queue_metadata=rule.enabled and not rule.paused)
            if rule.paused or not rule.enabled:pipeline.pause_rule_episodes(self.deps,rule.rule_id)
            else:
                pipeline.resume_rule_episodes(self.deps,rule.rule_id,rule.check_minutes)
                self.store.reset_rule_schedule(rule.rule_id)
            self.store.event('rule_saved',rule.rule_id)
        self._wake.set()
        return {'rule':canonical,'issues':[i.__dict__ for i in rules_module.warn_implausible(rule)],
                'episodes':len(self.store.episodes_for_rule(rule.rule_id)),'metadata':{'state':'queued' if not rule.paused else 'paused'}}

    def rule_payload(self,rule_id):return self.store.rule(rule_id)

    def set_paused(self,rule_id,paused):
        with self._control:
            payload=self.store.rule(rule_id)
            if payload is None:return None
            canonical=rules_module.rule_to_dict(rules_module.rule_from_dict({**payload,'paused':paused}))
            self.store.put_rule(rule_id,canonical,queue_metadata=not paused)
            if paused:pipeline.pause_rule_episodes(self.deps,rule_id)
            else:
                pipeline.resume_rule_episodes(self.deps,rule_id,canonical['checkMinutes'])
                self.store.reset_rule_schedule(rule_id)
            self.store.event('rule_paused' if paused else 'rule_resumed',rule_id)
        self._wake.set();return canonical

    def remove_rule(self,rule_id):
        with self._control:
            self.store.delete_rule(rule_id);self.store.event('rule_stopped',rule_id)
        return {'paused':True,'note':'history kept'}

    def status_overview(self):
        counts=self.store.episode_counts()
        return {'status':'ok','worker':self._state['worker'],'rules':[
            {'ruleId':p['ruleId'],'series':p.get('series',{}).get('name',''),'paused':bool(p.get('paused')),
             'confirmed':bool(p.get('confirmed')),'episodes':sum(counts.get(p['ruleId'],{}).values()),
             'statuses':counts.get(p['ruleId'],{}),'metadata':self.store.metadata_state(p['ruleId'])}
            for p in self.store.rules()]}

    def diagnostics(self):
        return {'worker':{'healthy':self.healthy(),'state':self._state['worker'],'heartbeatAgeSeconds':round(time.monotonic()-self._heartbeat,1)},
            'configured':{'sources':bool(self.config.addons or any(t.get('enabled',True) for t in self.config.torznab)),
                          'torbox':self.torbox is not None,'filter':self.filter_client is not None,'jellyfin':self.jellyfin is not None},
            'checks':dict(self._state),'lastReconcile':self._last_reconcile}

    def episodes(self,rule_id=None):
        return self.store.episodes_for_rule(rule_id) if rule_id else self.store.all_episodes(limit=10000)

    def request_reconcile(self):
        self._reconcile_due=0.;self._wake.set()
        return {'queued':True}

    def request_check(self,rule_id):
        with self._control:
            rule=self.store.rule(rule_id)
            if not rule:raise ValueError('Unknown rule')
            if not rule.get('confirmed') or not rule.get('enabled') or rule.get('paused'):raise ValueError('Rule must be active before checking')
            self.store.check_now(rule_id)
        self._wake.set();return {'queued':True}

    def safe_reconcile(self):
        if self.torbox is None:return {'reconciled':0,'note':'no TorBox credentials configured'}
        result=pipeline.reconcile_with_account(self.deps)
        failed='error' in result
        self._state['torbox']='retrying' if failed else 'ok'
        self._last_reconcile=now()
        self._reconcile_due=now()+(60 if failed or self.store.torrents_unconfirmed() else RECONCILE_INTERVAL_SECONDS)
        return result


def _handler_class(service):
    from urllib.parse import urlsplit,parse_qs
    class Handler(BaseHTTPRequestHandler):
        protocol_version='HTTP/1.1'
        def setup(self):
            super().setup();self.connection.settimeout(10)
        def log_message(self,*args):pass
        def _send(self,status,body):
            blob=json.dumps(body,allow_nan=False).encode()
            self.close_connection=True
            try:
                self.send_response(status);self.send_header('Content-Type','application/json')
                self.send_header('Content-Length',str(len(blob)));self.send_header('Connection','close')
                self.send_header('Cache-Control','no-store');self.end_headers();self.wfile.write(blob)
            except (BrokenPipeError,ConnectionResetError,TimeoutError):pass
        def _authorized(self):
            import hmac
            return hmac.compare_digest(self.headers.get('Authorization','').encode(),('Bearer '+service.config.token).encode())
        def _read_json(self):
            if self.headers.get('Transfer-Encoding'):raise ValueError('Chunked requests are not supported')
            length=int(self.headers.get('Content-Length','0'))
            if length<=0 or length>200000:raise ValueError('Invalid request size')
            blob=self.rfile.read(length)
            if len(blob)!=length:raise ValueError('Incomplete request')
            payload=json.loads(blob.decode('utf-8'),parse_constant=lambda _:(_ for _ in ()).throw(ValueError('Non-finite numbers are not supported')))
            if not isinstance(payload,dict):raise ValueError('JSON object required')
            return payload
        def _dispatch(self,method):
            parsed=urlsplit(self.path);path=parsed.path
            if method=='GET' and path=='/health':
                ok=service.healthy();return self._send(200 if ok else 503,{'ok':ok,'version':VERSION})
            if not self._authorized():return self._send(401,{'error':'unauthorized'})
            try:
                if method=='GET':
                    if path=='/v1/status':return self._send(200,service.status_overview())
                    if path=='/v1/diagnostics':return self._send(200,service.diagnostics())
                    if path=='/v1/rules':return self._send(200,{'items':service.store.rules()})
                    if path.startswith('/v1/rules/'):
                        payload=service.rule_payload(path[len('/v1/rules/'):])
                        return self._send(200,payload) if payload else self._send(404,{'error':'no such rule'})
                    query=parse_qs(parsed.query,keep_blank_values=True)
                    if path=='/v1/episodes':
                        rid=query.get('rule_id',[None])[0]
                        if rid=='':raise ValueError('Empty rule id')
                        return self._send(200,{'items':service.episodes(rid)})
                    if path=='/v1/events':
                        limit=max(1,min(int(query.get('limit',['200'])[0]),1000))
                        return self._send(200,{'items':service.store.recent_events(limit)})
                if method=='POST':
                    if path=='/v1/rules':
                        payload=self._read_json()
                        if payload.get('confirm') is not True and payload.get('confirmed') is not True:raise ValueError('Auto-add must be explicitly confirmed')
                        return self._send(201,service.save_rule(payload))
                    if path=='/v1/reconcile':return self._send(202,service.request_reconcile())
                    parts=path.strip('/').split('/')
                    if len(parts)==4 and parts[:2]==['v1','rules'] and parts[3]=='check':return self._send(202,service.request_check(parts[2]))
                    if len(parts)==4 and parts[:2]==['v1','rules'] and parts[3] in ('pause','resume'):
                        rule=service.set_paused(parts[2],parts[3]=='pause')
                        return self._send(200,{'rule':rule}) if rule else self._send(404,{'error':'no such rule'})
                if method=='DELETE' and path.startswith('/v1/rules/'):
                    return self._send(200,service.remove_rule(path[len('/v1/rules/'):]))
                return self._send(404,{'error':'unknown operation'})
            except (ValueError,TypeError,UnicodeError) as error:
                return self._send(400,{'error':service.redact(str(error))})
            except Exception as error:
                try:service.store.event('api_error',type(error).__name__)
                except Exception:pass
                return self._send(503,{'error':'Storage is temporarily unavailable; the request was not acknowledged'})
        def do_GET(self):self._dispatch('GET')
        def do_POST(self):self._dispatch('POST')
        def do_DELETE(self):self._dispatch('DELETE')
    return Handler


class WatchHTTPServer(ThreadingHTTPServer):
    daemon_threads=True
    def __init__(self,address,service):
        self.service=service;self._slots=threading.BoundedSemaphore(16)
        super().__init__(address,_handler_class(service))
    def process_request(self,request,address):
        if not self._slots.acquire(blocking=False):
            try:request.sendall(b'HTTP/1.1 503 Busy\r\nContent-Length: 0\r\nConnection: close\r\n\r\n')
            finally:self.shutdown_request(request)
            return
        try:super().process_request(request,address)
        except Exception:self._slots.release();raise
    def process_request_thread(self,request,address):
        try:super().process_request_thread(request,address)
        finally:self._slots.release()
    def service_actions(self):
        if not self.service._stop.is_set() and not self.service.healthy():raise RuntimeError('Worker is unhealthy; restarting preserves pending work')


def main():
    import signal
    from pathlib import Path
    config=Config.from_env()
    Path(config.db_path).parent.mkdir(parents=True,exist_ok=True)
    # Linux Docker: exclude a second writer even across separate containers.
    import fcntl
    lock=open(config.db_path+'.instance.lock','a')
    try:fcntl.flock(lock,fcntl.LOCK_EX|fcntl.LOCK_NB)
    except BlockingIOError:raise SystemExit('Another watcher already owns this database') from None
    service=WatchService(config)
    server=WatchHTTPServer((config.bind_host,config.bind_port),service)
    def shutdown(*_):
        service._stop.set();service._wake.set()
        threading.Thread(target=server.shutdown,daemon=True).start()
    signal.signal(signal.SIGTERM,shutdown);signal.signal(signal.SIGINT,shutdown)
    service.start()
    print(f'stak-watch/{VERSION} ready on port {config.bind_port}',flush=True)
    try:server.serve_forever(poll_interval=.5)
    finally:
        service.stop();server.server_close()
        if not service._scheduler.is_alive():service.store.checkpoint();service.store.close()
        lock.close()

if __name__=='__main__':main()
