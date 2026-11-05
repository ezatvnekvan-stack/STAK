"""Bounded v1 torrent metadata validation. Never re-encode a private torrent."""
import hashlib

MAX_BYTES=5*1024*1024

def inspect_torrent(blob):
    if not isinstance(blob,bytes) or not 1<len(blob)<=MAX_BYTES:raise ValueError('Invalid torrent size')
    index=0;nodes=0;info_range=None
    def parse(depth=0):
        nonlocal index,nodes,info_range
        nodes+=1
        if depth>32 or nodes>100000 or index>=len(blob):raise ValueError('Invalid torrent structure')
        marker=blob[index:index+1]
        if marker==b'i':
            end=blob.index(b'e',index+1);raw=blob[index+1:end]
            if not raw or len(raw)>20:raise ValueError('Invalid integer')
            value=int(raw);index=end+1;return value
        if marker in (b'd',b'l'):
            index+=1;result={} if marker==b'd' else []
            while index<len(blob) and blob[index:index+1]!=b'e':
                if marker==b'd':
                    key=parse(depth+1)
                    if not isinstance(key,bytes) or key in result:raise ValueError('Invalid torrent dictionary')
                    start=index;result[key]=parse(depth+1)
                    if depth==0 and key==b'info':info_range=(start,index)
                else:result.append(parse(depth+1))
            if index>=len(blob):raise ValueError('Truncated torrent')
            index+=1;return result
        colon=blob.find(b':',index,index+12)
        if colon<0:raise ValueError('Invalid torrent string')
        raw=blob[index:colon]
        if not raw.isdigit():raise ValueError('Invalid string length')
        n=int(raw);index=colon+1
        if n>MAX_BYTES or index+n>len(blob):raise ValueError('Truncated torrent string')
        value=blob[index:index+n];index+=n;return value
    try:document=parse()
    except (IndexError,OverflowError):raise ValueError('Invalid torrent') from None
    if index!=len(blob) or not isinstance(document,dict) or not info_range:raise ValueError('Invalid torrent root')
    info=document.get(b'info')
    if not isinstance(info,dict) or not isinstance(info.get(b'name'),bytes) or (b'length' not in info and b'files' not in info):raise ValueError('Torrent info missing')
    if b'pieces' not in info:raise ValueError('A v1 torrent identity is required')
    return hashlib.sha1(blob[slice(*info_range)]).hexdigest(),info

def fetch_torrent(client,url):
    from urllib.parse import urlsplit
    path=urlsplit(url).path.lower()
    if path.endswith(('.mkv','.mp4','.avi','.m4v','.ts','.webm')):raise ValueError('Video URLs are not torrent metadata')
    response=client.fetch(url,headers={'Accept':'application/x-bittorrent'},timeout=20)
    if response.status!=200:raise ValueError('Torrent metadata unavailable')
    blob=response.read();identity,_=inspect_torrent(blob)
    return blob,identity

def validate_episode(blob,season,episode):
    from .episode import parse_episode_marker
    _,info=inspect_torrent(blob)
    paths=[]
    for row in info.get(b'files',[]):
        if isinstance(row,dict):paths.append('/'.join(p.decode('utf-8','replace') for p in row.get(b'path',[]) if isinstance(p,bytes)))
    if not paths:paths=[info[b'name'].decode('utf-8','replace')]
    videos=[p for p in paths if p.lower().endswith(('.mkv','.mp4','.m4v','.avi','.webm')) and 'sample' not in p.lower()]
    if len(videos)>1:raise ValueError('Automatic season/multi-video packs are not supported')
    for path in videos:
        marker=parse_episode_marker(path)
        if marker is not None and marker!=(season,episode):raise ValueError('Torrent episode does not match')
