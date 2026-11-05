"""STAK's optional TorBox approval filter. Metadata GETs and symlinks only."""
import collections, contextlib, hashlib, hmac, json, os, re, sqlite3, stat, threading, time
import urllib.request, urllib.parse
from pathlib import Path, PurePosixPath
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

PROTOCOL = 'stak-library-filter/1'
VIDEO = {'.mkv','.mp4','.m4v','.avi','.mov','.ts','.m2ts','.webm'}
MAX_BODY = 65536

def hash_value(value):
    value = (value or '').lower()
    if not re.fullmatch(r'[0-9a-f]{40}|[0-9a-f]{64}', value):
        raise ValueError('A verified torrent hash is required')
    return value

def safe_name(value):
    return re.sub(r'[<>:"/\\|?*\x00-\x1f]', '_', value).strip(' .')[:160] or 'Untitled'

def signature(files):
    result = []
    for item in files:
        raw = item.get('name','').replace('\\','/')
        path = PurePosixPath(raw)
        if path.is_absolute() or '..' in path.parts or '\x00' in raw:
            raise ValueError('Unsafe provider filename')
        size = item.get('size')
        if path.suffix.lower() in VIDEO:
            if not isinstance(size,int) or isinstance(size,bool) or size <= 0:
                raise ValueError('Media size is unavailable')
            result.append((path.name,size))
    if not result: raise ValueError('No media files available yet')
    return collections.Counter(result)

def index_source(root, limit=100000):
    """Only filesystem metadata is inspected. Never open a media file."""
    root=Path(root).resolve(strict=True); groups={}; count=0
    for folder in sorted(root.iterdir()):
        if folder.is_symlink(): continue
        if folder.is_file():
            groups[folder.name]=[folder] if folder.suffix.lower() in VIDEO else []
            continue
        if not folder.is_dir(): continue
        found=[]
        for parent, dirs, names in os.walk(folder,followlinks=False):
            dirs[:]=[d for d in dirs if not (Path(parent)/d).is_symlink()]
            if len(Path(parent).relative_to(folder).parts)>8: dirs[:]=[]; continue
            for name in names:
                count+=1
                if count>limit: raise ValueError('Mount listing limit exceeded')
                path=Path(parent)/name
                if path.suffix.lower() in VIDEO and not path.is_symlink() and stat.S_ISREG(path.stat().st_mode): found.append(path)
        groups[folder.name]=found
    return groups

def match_source(groups, expected, torrent_hash):
    expected=signature(expected)
    # Prefer a hash-bearing folder, but never accept an incomplete file manifest.
    matches=[]
    for name, paths in groups.items():
        actual=collections.Counter((p.name,p.stat().st_size) for p in paths)
        if actual == expected: matches.append((name,paths))
    scoped=[m for m in matches if re.search(r'(?<![0-9a-f])'+re.escape(torrent_hash)+r'(?![0-9a-f])',m[0],re.I)]
    if len(scoped)==1: return scoped[0][1], 'hash-and-manifest'
    if len(matches)!=1: raise ValueError('Source mapping is missing or ambiguous')
    if any(n>1 for n in expected.values()): raise ValueError('Duplicate filenames need explicit mapping')
    return matches[0][1], 'unique-full-manifest'

class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl): return None

def request_json(url,headers=None,method='GET',body=None):
    req=urllib.request.Request(url,data=None if body is None else json.dumps(body).encode(),headers={"User-Agent":"STAK-Library-Filter/1.3.6",**(headers or {})},method=method)
    if body is not None: req.add_header('Content-Type','application/json')
    with urllib.request.build_opener(NoRedirect).open(req,timeout=20) as response:
        data=response.read(8*1024*1024+1)
        if len(data)>8*1024*1024: raise ValueError('Metadata response too large')
        return json.loads(data) if data.strip() else {}

def jellyfin_headers(token):
    value = str(token).strip()
    if not value or any(ord(c) < 33 or ord(c) > 126 or c in '"\\' for c in value):
        raise ValueError('Invalid Jellyfin access token format')
    # Jellyfin 12 disables legacy token headers by default. Use the same
    # supported mechanism as the watcher, never both authentication methods.
    return {'Authorization': 'MediaBrowser Token="' + value + '"'}


class Filter:
    def __init__(self,config):
        self.c=config;self.lock=threading.RLock();self.wake=threading.Event();self.stop=threading.Event()
        self.source=Path(config['source_root']).resolve();self.output=Path(config['output_root']).resolve()
        if self.source==self.output or self.source in self.output.parents or self.output in self.source.parents: raise ValueError('Source and output must be separate')
        if not PurePosixPath(config['target_root']).is_absolute(): raise ValueError('Jellyfin source path must be absolute')
        if len(config['token'])<32 or not config['torbox_key'] or config['token'].startswith('REPLACE_') or config['torbox_key'].startswith('REPLACE_'): raise ValueError('Configure a strong access token and TorBox key')
        self.scope=hashlib.sha256(('torbox:'+config['torbox_key']).encode()).hexdigest()
        self.output.mkdir(parents=True,exist_ok=True)
        marker=self.output/'.stak-filter-v1'
        if marker.is_symlink(): raise ValueError('Output marker must be a regular file')
        if not marker.exists():
            if any(self.output.iterdir()): raise ValueError('Use an empty dedicated output folder')
            marker.write_text(PROTOCOL)
        if marker.read_text()!=PROTOCOL: raise ValueError('Unrecognized output folder')
        self.marker_name='STAK-FILTER-'+self.scope[:16]+'.marker'
        for category in ['movies','shows']:
            directory=self.output/category
            if directory.is_symlink():raise ValueError('Output category must not be a symlink')
            directory.mkdir(exist_ok=True);(directory/self.marker_name).write_text(PROTOCOL)
        Path(config['db']).parent.mkdir(parents=True,exist_ok=True)
        self.db=sqlite3.connect(config['db'],check_same_thread=False)
        self.db.execute('CREATE TABLE IF NOT EXISTS approvals (id TEXT PRIMARY KEY, body TEXT NOT NULL, state TEXT NOT NULL, note TEXT NOT NULL, links TEXT NOT NULL DEFAULT "[]")');self.db.commit()

    def approve(self,body):
        if body.get('scope')!=self.scope: raise ValueError('TorBox account mismatch')
        identity=body.get('id','')
        if not isinstance(identity,str) or not re.fullmatch(r'\d{1,20}',identity): raise ValueError('Invalid torrent identifier')
        supplied=body.get('hash')
        supplied=hash_value(supplied) if supplied else None
        if not isinstance(body.get('approved'),bool): raise ValueError('Approval decision required')
        clean={'id':identity,'scope':self.scope,'approved':body['approved'],'hash':supplied,'name':safe_name(str(body.get('name',''))),'type':body.get('type')if body.get('type')in('movie','series')else 'unknown'}
        with self.lock:
            old=self.db.execute('SELECT body,state FROM approvals WHERE id=?',(identity,)).fetchone()
            if not old and self.db.execute('SELECT COUNT(*) FROM approvals').fetchone()[0]>=10000: raise ValueError('Approval limit reached')
            if old:
                before=json.loads(old[0])
                if before.get('hash') and supplied and before['hash']!=supplied: raise ValueError('Torrent identity changed')
                clean['hash']=before.get('hash') or supplied
                if before==clean: return self.get(identity)
            self.db.execute('INSERT INTO approvals(id,body,state,note) VALUES (?,?,?,?) ON CONFLICT(id) DO UPDATE SET body=excluded.body,state=excluded.state,note=excluded.note',(identity,json.dumps(clean),'pending','Waiting for reconciliation'))
            self.db.commit();self.wake.set();return self.get(identity)

    def get(self,identity=None):
        with self.lock:
            rows=self.db.execute('SELECT id,body,state,note FROM approvals'+(' WHERE id=?'if identity else ' ORDER BY id LIMIT 10000'),(identity,)if identity else ()).fetchall()
            result=[dict(json.loads(b),state=s,note=n)for i,b,s,n in rows]
            return result[0]if identity and result else result

    def update(self,identity,state,note):
        with self.lock:self.db.execute('UPDATE approvals SET state=?,note=? WHERE id=?',(state,note,identity));self.db.commit()

    def unlink_owned(self,links):
        for raw in links:
            link=Path(raw)
            if self.output not in link.absolute().parents: raise ValueError('Invalid owned path')
            if self.output not in link.parent.resolve().parents and link.parent.resolve()!=self.output: raise ValueError('Owned path escaped its root')
            if link.is_symlink(): link.unlink()
            elif link.exists(): raise ValueError('A regular file replaced a managed link')

    def publish(self,job,torrent,groups):
        actual=hash_value(torrent.get('hash'))
        if job.get('hash') and job['hash']!=actual: raise ValueError('TorBox hash mismatch')
        if torrent.get('download_finished')is not True: self.update(job['id'],'processing','Waiting for TorBox completion');return False
        files=torrent.get('files',[]);paths,confidence=match_source(groups,files,actual)
        label=safe_name(job['name'] or torrent.get('name','Untitled'))
        category='shows'if job['type']=='series'else 'movies'if job['type']=='movie'else 'unclassified'
        if category=='unclassified': self.update(job['id'],'needs_type','Choose Movies or Series in STAK');return False
        relative=paths[0].relative_to(self.source)
        release=relative.parts[0] if len(relative.parts)>1 else safe_name(torrent.get('name','Release'))
        release_root=self.source/release
        folder=self.output/category/release
        with self.lock:
            row=self.db.execute('SELECT links,body FROM approvals WHERE id=?',(job['id'],)).fetchone()
            latest=json.loads(row[1])
            if not latest['approved']: return False
            peers=self.db.execute('SELECT id,body,state FROM approvals WHERE id!=?',(job['id'],)).fetchall()
            if any(json.loads(body).get('approved') and json.loads(body).get('hash')==actual and state=='published' for identity,body,state in peers):
                self.update(job['id'],'duplicate','The same content is already published');return False
            if folder.is_symlink() or self.output not in folder.resolve().parents:raise ValueError('Output folder escaped its root')
            folder.mkdir(parents=True,exist_ok=True)
            links=[];created=[]
            try:
                for path in paths:
                    target=PurePosixPath(self.c['target_root'])/path.relative_to(self.source).as_posix()
                    link=folder/path.relative_to(release_root) if len(path.relative_to(self.source).parts)>1 else folder/path.name
                    if self.output not in link.parent.resolve().parents and link.parent.resolve()!=self.output:raise ValueError('Output path escaped its root')
                    link.parent.mkdir(parents=True,exist_ok=True)
                    if link.is_symlink():
                        if os.readlink(link)!=str(target): raise ValueError('Destination collision')
                    elif link.exists(): raise ValueError('Destination collision')
                    else: link.symlink_to(target);created.append(str(link))
                    links.append(str(link))
                old=json.loads(row[0]);self.unlink_owned([x for x in old if x not in links])
                latest['hash']=actual
                self.db.execute('UPDATE approvals SET body=?,links=?,state=?,note=? WHERE id=?',(json.dumps(latest),json.dumps(links),'published',confidence,job['id']));self.db.commit()
            except Exception:
                self.unlink_owned(created);raise
        return set(old)!=set(links)

    def jellyfin_verified(self):
        c=self.c
        if not all(c.get(k)for k in ['jellyfin_url','jellyfin_key','jellyfin_movies','jellyfin_shows']):return False
        data=request_json(c['jellyfin_url'].rstrip('/')+'/Library/VirtualFolders',jellyfin_headers(c['jellyfin_key']))
        if not isinstance(data,list):return False
        paths=[p.rstrip('/')for row in data for p in row.get('Locations',[])]
        expected={c['jellyfin_movies'].rstrip('/'),c['jellyfin_shows'].rstrip('/')}
        source=c.get('jellyfin_zurg_base') or str(PurePosixPath(c['target_root']).parent)
        if not expected.issubset(paths) or any(p==source or p.startswith(source+'/') or source.startswith(p+'/')for p in paths): return False
        # Same Jellyfin paths can still point at the OLD broad Docker mounts. Check a per-instance marker in both roots.
        for directory in expected:
            url=c['jellyfin_url'].rstrip('/')+'/Environment/DirectoryContents?'+urllib.parse.urlencode({'path':directory,'includeFiles':'true','includeDirectories':'false'})
            contents=request_json(url,jellyfin_headers(c['jellyfin_key']))
            if not isinstance(contents,list) or not any(x.get('Name')==self.marker_name and x.get('Type') in ('File',0) for x in contents): return False
        return True

    def status(self):
        try:verified=self.jellyfin_verified()
        except Exception:verified=False
        return {'protocol':PROTOCOL,'scope':self.scope,'source_available':self.source.is_dir(),'jellyfin_paths_verified':verified,'expected_zurg':'2026.10.10.2240-nightly','approval_count':len(self.get())}

    def clear_publication(self,identity):
        with self.lock:
            row=self.db.execute('SELECT links FROM approvals WHERE id=?',(identity,)).fetchone()
            links=json.loads(row[0]);self.unlink_owned(links)
            self.db.execute('UPDATE approvals SET links="[]" WHERE id=?',(identity,));self.db.commit()
            return bool(links)

    def catalog(self):
        catalog={};offset=0;limit=200
        for page in range(100):
            url='https://api.torbox.app/v1/api/torrents/mylist?'+urllib.parse.urlencode({'bypass_cache':'true','offset':offset,'limit':limit})
            data=request_json(url,{'Authorization':'Bearer '+self.c['torbox_key']})
            if data.get('success')is not True:raise ValueError('TorBox metadata unavailable')
            rows=data.get('data');rows=rows.get('torrents')if isinstance(rows,dict)else rows
            if not isinstance(rows,list):raise ValueError('Incomplete TorBox catalog')
            before=len(catalog)
            for row in rows:
                if not isinstance(row,dict) or not str(row.get('id','')).isdigit():raise ValueError('Invalid TorBox catalog entry')
                catalog[str(row['id'])]=row
            if len(rows)<limit or len(catalog)==before:return catalog
            offset+=len(rows)
        raise ValueError('TorBox catalog limit exceeded')

    def reconcile(self):
        jobs=self.get();groups=None;changed=False;catalog=None
        if any(job['approved']for job in jobs):
            try:catalog=self.catalog()
            except Exception:
                for job in jobs:
                    if job['approved']:self.update(job['id'],'waiting','TorBox catalog unavailable; existing links retained')
        for job in jobs:
            try:
                if not job['approved']:
                    with self.lock:
                        links=json.loads(self.db.execute('SELECT links FROM approvals WHERE id=?',(job['id'],)).fetchone()[0]);self.unlink_owned(links)
                        self.db.execute('UPDATE approvals SET links="[]",state="revoked",note="Only STAK links removed" WHERE id=?',(job['id'],));self.db.commit()
                    changed=changed or bool(links);continue
                # A transient mount outage must never erase existing publication links.
                if not self.source.is_dir(): raise ValueError('Zurg mount unavailable')
                if catalog is None:continue
                torrent=catalog.get(job['id'])
                if torrent is None:
                    changed=self.clear_publication(job['id'])or changed
                    raise ValueError('Approved torrent not found')
                if groups is None:groups=index_source(self.source)
                changed=self.publish(job,torrent,groups)or changed
            except Exception as error:
                # No provider bodies, URLs or credentials enter client-visible errors.
                if isinstance(error,ValueError) and str(error)in ['TorBox hash mismatch','Source mapping is missing or ambiguous'] and groups:
                    changed=self.clear_publication(job['id'])or changed
                note=str(error)if isinstance(error,ValueError)else 'Connection or filesystem check failed; retrying'
                self.update(job['id'],'waiting',note[:160])
        if changed and self.c.get('scan_on_change'):
            try:
                if self.jellyfin_verified():request_json(self.c['jellyfin_url'].rstrip('/')+'/Library/Refresh',jellyfin_headers(self.c['jellyfin_key']),method='POST')
            except Exception:pass # Published is not a claim that indexing completed.

    def run(self):
        while not self.stop.is_set():self.wake.clear();self.reconcile();self.wake.wait(self.c.get('interval',60))

def handler(service):
    class Handler(BaseHTTPRequestHandler):
        server_version='STAK-Filter/1'
        def setup(self):
            super().setup();self.connection.settimeout(20)
        def log_message(self,*args):pass
        def send_json(self,code,body):
            raw=json.dumps(body).encode();self.send_response(code);self.send_header('Content-Type','application/json');self.send_header('Cache-Control','no-store');self.send_header('Content-Length',str(len(raw)));self.end_headers();self.wfile.write(raw)
        def authorized(self):return hmac.compare_digest(self.headers.get('Authorization',''),'Bearer '+service.c['token'])
        def do_GET(self):
            if not self.authorized():return self.send_json(401,{'error':'Authentication required'})
            if self.path=='/v1/status':return self.send_json(200,service.status())
            if self.path=='/v1/approvals':return self.send_json(200,{'items':service.get()})
            self.send_json(404,{'error':'Unknown operation'})
        def do_POST(self):
            if not self.authorized():return self.send_json(401,{'error':'Authentication required'})
            if self.path!='/v1/approvals':return self.send_json(404,{'error':'Unknown operation'})
            try:
                n=int(self.headers.get('Content-Length','0'))
                if not 0<n<=MAX_BODY:raise ValueError('Invalid request size')
                body=json.loads(self.rfile.read(n))
                if not isinstance(body,dict):raise ValueError('Expected object')
                result=service.approve(body)
                self.send_json(200,result)
            except (ValueError,TypeError,KeyError):self.send_json(400,{'error':'Invalid approval or account mismatch'})
    return Handler

def config_from_env():
    return {'source_root':os.getenv('SOURCE_ROOT','/source'),'output_root':os.getenv('OUTPUT_ROOT','/library'),'target_root':os.environ['JELLYFIN_ZURG_ROOT'],'db':os.getenv('STATE_DB','/state/filter.db'),'token':os.environ['STAK_ACCESS_TOKEN'],'torbox_key':os.environ['TORBOX_API_KEY'],'interval':max(30,int(os.getenv('CHECK_SECONDS','60'))),'jellyfin_url':os.getenv('JELLYFIN_URL',''),'jellyfin_key':os.getenv('JELLYFIN_API_KEY',''),'jellyfin_movies':os.getenv('JELLYFIN_MOVIES_PATH',''),'jellyfin_shows':os.getenv('JELLYFIN_SHOWS_PATH',''),'jellyfin_zurg_base':os.getenv('JELLYFIN_ZURG_BASE',''),'scan_on_change':os.getenv('SCAN_ON_CHANGE','false').lower()=='true'}

if __name__=='__main__':
    service=Filter(config_from_env());threading.Thread(target=service.run,daemon=True).start()
    server=ThreadingHTTPServer((os.getenv('BIND_ADDRESS','127.0.0.1'),int(os.getenv('PORT','8787'))),handler(service))
    server.serve_forever()
