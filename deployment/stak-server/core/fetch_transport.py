"""Bounded transport: no mutation retries or cross-origin credential forwarding."""
import time
import urllib.error
import urllib.parse
import urllib.request

MAX_RESPONSE=8*1024*1024

class FetchFailure(Exception):pass

class FetchResponse:
    def __init__(self,status,body,headers=None):
        self.status=status;self._body=body;self.headers=headers or {}
    def read(self):return self._body

class SafeRedirect(urllib.request.HTTPRedirectHandler):
    max_redirections=3
    def redirect_request(self,req,fp,code,msg,headers,newurl):
        if req.get_method() not in ('GET','HEAD'):return None
        before=urllib.parse.urlsplit(req.full_url);after=urllib.parse.urlsplit(newurl)
        if after.scheme not in ('http','https') or (before.scheme=='https' and after.scheme!='https'):return None
        if after.username or after.password:return None
        out=super().redirect_request(req,fp,code,msg,headers,newurl)
        if out and (before.scheme,before.hostname,before.port)!=(after.scheme,after.hostname,after.port):
            for key in list(out.headers):
                if key.lower() in ('authorization','cookie','x-api-key','x-emby-token','x-emby-authorization'):out.remove_header(key)
        return out

class FetchClient:
    user_agent='STAK-Watch/1.1.4'
    def __init__(self,on_activity=None,cancelled=None):
        self.on_activity=on_activity or (lambda:None)
        self.cancelled=cancelled or (lambda:False)
    def fetch(self,url,headers=None,timeout=30,data=None,method='GET'):
        if self.cancelled():raise FetchFailure('Service is stopping')
        u=urllib.parse.urlsplit(url)
        if u.scheme not in ('http','https') or not u.hostname or u.username or u.password or len(url)>32768:
            raise FetchFailure('Invalid HTTP endpoint')
        request=urllib.request.Request(url,data=data,method=method)
        for name,value in (headers or {}).items():request.add_header(name,value)
        request.add_header('User-Agent',self.user_agent);request.add_header('Connection','close')
        self.on_activity();deadline=time.monotonic()+max(1,min(timeout,30));response=None
        try:
            opener=urllib.request.build_opener(SafeRedirect())
            try:response=opener.open(request,timeout=max(1,min(timeout,30)))
            except urllib.error.HTTPError as error:response=error
            length=response.headers.get('Content-Length')
            torrent=any(k.lower()=='accept' and 'application/x-bittorrent' in v for k,v in (headers or {}).items())
            limit=5*1024*1024 if torrent else MAX_RESPONSE
            if torrent and response.headers.get('Content-Type','').lower().startswith('video/'):
                raise FetchFailure('Video is not torrent metadata')
            if length and int(length)>limit:raise FetchFailure('Response exceeds size limit')
            chunks=[];size=0;read=getattr(response,'read1',response.read)
            while True:
                if self.cancelled():raise FetchFailure('Service is stopping')
                if time.monotonic()>deadline:raise FetchFailure('Service response timed out')
                chunk=read(min(65536,limit-size+1))
                if not chunk:break
                size+=len(chunk)
                if size>limit:raise FetchFailure('Response exceeds size limit')
                chunks.append(chunk);self.on_activity()
            return FetchResponse(int(response.code),b''.join(chunks),dict(response.headers))
        except (urllib.error.URLError,TimeoutError,OSError) as error:
            raise FetchFailure('Service connection failed ('+type(error).__name__+')') from None
        finally:
            if response is not None:response.close()
            self.on_activity()
