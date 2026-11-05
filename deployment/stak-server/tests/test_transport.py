import threading
from http.server import BaseHTTPRequestHandler,ThreadingHTTPServer
from contextlib import contextmanager
import pytest
from core.fetch_transport import FetchClient,FetchFailure,MAX_RESPONSE

@contextmanager
def endpoint(callback):
    class Handler(BaseHTTPRequestHandler):
        def log_message(self,*args):pass
        def do_GET(self):callback(self)
        def do_POST(self):callback(self)
    server=ThreadingHTTPServer(('127.0.0.1',0),Handler)
    t=threading.Thread(target=server.serve_forever,daemon=True);t.start()
    try:yield f'http://127.0.0.1:{server.server_port}'
    finally:server.shutdown();server.server_close()

def reply(h,code=200,body=b'{}',headers=None):
    h.send_response(code)
    for k,v in (headers or {}).items():h.send_header(k,v)
    h.send_header('Content-Length',str(len(body)));h.end_headers()
    try:h.wfile.write(body)
    except (BrokenPipeError,ConnectionResetError):pass

def test_redirect_drops_authorization_at_origin_boundary():
    received=[]
    def target(h):received.append(h.headers.get('Authorization'));reply(h)
    with endpoint(target) as dest:
        with endpoint(lambda h:reply(h,302,headers={'Location':dest+'/target'})) as source:
            assert FetchClient().fetch(source+'/redirect',headers={'Authorization':'Bearer fixture-secret'}).status==200
    assert received==[None]

def test_post_is_never_retried_or_redirected():
    methods=[]
    def handler(h):methods.append(h.command);reply(h,302,headers={'Location':'/again'})
    with endpoint(handler) as base:
        assert FetchClient().fetch(base+'/add',method='POST',data=b'fixture').status==302
    assert methods==['POST']

def test_oversized_response_is_refused_before_reading_it():
    def handler(h):h.send_response(200);h.send_header('Content-Length',str(MAX_RESPONSE+1));h.end_headers()
    with endpoint(handler) as base:
        with pytest.raises(FetchFailure,match='size limit'):FetchClient().fetch(base)

def test_metadata_endpoint_cannot_stream_video():
    with endpoint(lambda h:reply(h,headers={'Content-Type':'video/mp4'})) as base:
        with pytest.raises(FetchFailure,match='Video'):FetchClient().fetch(base,headers={'Accept':'application/x-bittorrent'})

def test_shutdown_cancels_before_opening_a_new_request():
    with pytest.raises(FetchFailure,match='stopping'):
        FetchClient(cancelled=lambda:True).fetch('http://127.0.0.1:1/never-open')
