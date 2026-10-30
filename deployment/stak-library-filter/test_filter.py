import json, os, tempfile, threading, unittest, urllib.request, urllib.error
from pathlib import Path
from unittest.mock import patch
from http.server import ThreadingHTTPServer
import filter_server as module

class FilterTest(unittest.TestCase):
    def test_metadata_request_identifies_stak_and_preserves_authentication(self):
        import io
        with patch.object(module.urllib.request,'build_opener') as opener:
            opener.return_value.open.return_value=io.BytesIO(b'{"success":true,"data":[]}')
            response=module.request_json('https://api.torbox.app/v1/api/torrents/mylist',{'Authorization':'Bearer synthetic-test-only'})
            request=opener.return_value.open.call_args.args[0]
            self.assertEqual('STAK-Library-Filter/1.3.6',request.get_header('User-agent'))
            self.assertEqual('Bearer synthetic-test-only',request.get_header('Authorization'))
            self.assertEqual('GET',request.method)
            self.assertTrue(response['success'])
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory();self.root=Path(self.tmp.name);self.source=self.root/'source';self.source.mkdir();self.output=self.root/'output'
        self.filter=module.Filter({'source_root':str(self.source),'output_root':str(self.output),'target_root':'/media/zurg/__all__','db':str(self.root/'state.db'),'token':'a'*40,'torbox_key':'test-only'})
        self.hash='a'*40
    def tearDown(self):self.filter.db.close();self.tmp.cleanup()
    def job(self,identity='1',hash=None,approved=True):return {'id':identity,'scope':self.filter.scope,'hash':hash or self.hash,'name':'A film','type':'movie','approved':approved}
    def media(self,folder='Release',name='film.mkv',size=11):
        p=self.source/folder/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(b'x'*size);return p
    def torrent(self,identity='1',hash=None):return {'id':identity,'hash':hash or self.hash,'download_finished':True,'files':[{'id':1,'name':'Release/film.mkv','size':11}]}
    def test_approvals_are_idempotent_and_account_scoped(self):
        self.filter.approve(self.job());self.filter.approve(self.job());self.assertEqual(1,len(self.filter.get()))
        with self.assertRaises(ValueError):self.filter.approve(dict(self.job(),scope='other'))
    def test_provider_torrent_cannot_be_rebound(self):
        self.filter.approve(self.job())
        with self.assertRaises(ValueError):self.filter.approve(self.job(hash='b'*40))
    def test_only_approved_content_is_published(self):
        self.media();self.media('Stremio','other.mkv',12);job=self.filter.approve(self.job())
        with patch.object(module,'request_json',return_value={'success':True,'data':[self.torrent()]}):self.filter.reconcile()
        links=list(self.output.rglob('*.mkv'));self.assertEqual(1,len(links));self.assertTrue(links[0].is_symlink());self.assertEqual('/media/zurg/__all__/Release/film.mkv',os.readlink(links[0]));self.assertEqual('published',self.filter.get('1')['state'])
    def test_unapproved_content_never_generates_jobs(self):
        self.media('Stremio')
        with patch.object(module,'request_json')as request:self.filter.reconcile();request.assert_not_called()
        self.assertEqual([],list(self.output.rglob('*.mkv')))
    def test_ambiguous_manifest_is_rejected(self):
        self.media('First');self.media('Second');self.filter.approve(self.job())
        with self.assertRaises(ValueError):self.filter.publish(self.job(),self.torrent(),module.index_source(self.source))
        self.assertEqual([],list(self.output.rglob('*.mkv')))
    def test_hash_hint_resolves_duplicate_metadata(self):
        self.media('Release '+self.hash);self.media('Other');paths,confidence=module.match_source(module.index_source(self.source),self.torrent()['files'],self.hash);self.assertEqual('hash-and-manifest',confidence);self.assertEqual(1,len(paths))
    def test_hash_mismatch_never_publishes(self):
        self.media();self.filter.approve(self.job())
        with self.assertRaises(ValueError):self.filter.publish(self.job(),self.torrent(hash='b'*40),module.index_source(self.source))
    def test_revoke_removes_links_but_never_media(self):
        original=self.media();self.filter.approve(self.job());self.filter.publish(self.job(),self.torrent(),module.index_source(self.source));self.filter.approve(self.job(approved=False));self.filter.reconcile();self.assertTrue(original.exists());self.assertEqual([],list(self.output.rglob('*.mkv')));self.assertEqual('revoked',self.filter.get('1')['state'])
    def test_inflight_old_approval_cannot_beat_revoke(self):
        self.media();old=self.filter.approve(self.job());self.filter.approve(self.job(approved=False));self.assertFalse(self.filter.publish(old,self.torrent(),module.index_source(self.source)));self.assertEqual([],list(self.output.rglob('*.mkv')))
    def test_restart_keeps_membership_and_revocation(self):
        self.filter.approve(self.job());self.filter.approve(self.job(approved=False));self.filter.db.close();self.filter=module.Filter(self.filter.c);self.assertFalse(self.filter.get('1')['approved'])
    def test_path_traversal_rejected(self):
        with self.assertRaises(ValueError):module.signature([{'name':'../film.mkv','size':11}])
    def test_symlinked_source_is_not_followed(self):
        outside=self.root/'private';outside.mkdir();(outside/'film.mkv').write_bytes(b'x'*11);(self.source/'escape').symlink_to(outside,target_is_directory=True);self.assertEqual({},module.index_source(self.source))
    def test_source_outage_does_not_delete_published_links(self):
        self.media();self.filter.approve(self.job());self.filter.publish(self.job(),self.torrent(),module.index_source(self.source));os.rename(self.source,self.root/'offline');self.filter.reconcile();self.assertTrue(list(self.output.rglob('*.mkv'))[0].is_symlink())
    def test_duplicate_hash_has_one_publication(self):
        self.media();self.filter.approve(self.job());self.filter.publish(self.job(),self.torrent(),module.index_source(self.source));self.filter.approve(self.job('2'));self.filter.publish(self.job('2'),self.torrent('2'),module.index_source(self.source));self.assertEqual(1,len(list(self.output.rglob('*.mkv'))));self.assertEqual('duplicate',self.filter.get('2')['state'])
    def test_existing_regular_files_are_never_overwritten(self):
        self.media();self.filter.approve(self.job());folder=self.output/'movies'/'Release';folder.mkdir(parents=True);file=folder/'film.mkv';file.write_bytes(b'personal')
        with self.assertRaises(ValueError):self.filter.publish(self.job(),self.torrent(),module.index_source(self.source))
        self.assertEqual(b'personal',file.read_bytes())
    def test_output_symlink_cannot_escape_root(self):
        self.media();self.filter.approve(self.job());outside=self.root/'outside';outside.mkdir();(self.output/'movies'/'Release').symlink_to(outside,target_is_directory=True)
        with self.assertRaises(ValueError):self.filter.publish(self.job(),self.torrent(),module.index_source(self.source))
        self.assertEqual([],list(outside.iterdir()))
    def test_partial_collision_rolls_back_new_links(self):
        a=self.media('Release','a.mkv',11);b=self.media('Release','b.mkv',13);self.filter.approve(self.job());folder=self.output/'movies'/'Release';folder.mkdir();(folder/'b.mkv').write_bytes(b'personal')
        torrent=dict(self.torrent(),files=[{'name':'a.mkv','size':11},{'name':'b.mkv','size':13}])
        with self.assertRaises(ValueError):self.filter.publish(self.job(),torrent,{'Release':[a,b]})
        self.assertFalse((folder/'a.mkv').is_symlink());self.assertEqual(b'personal',(folder/'b.mkv').read_bytes())
    def test_publish_never_opens_media_bytes(self):
        self.media();self.filter.approve(self.job());original=Path.open
        def guard(path,*args,**kwargs):
            if path.suffix.lower() in module.VIDEO:raise AssertionError('Media file opened')
            return original(path,*args,**kwargs)
        with patch.object(Path,'open',guard):self.filter.publish(self.job(),self.torrent(),module.index_source(self.source))
    def test_api_requires_auth_and_has_no_shell_route(self):
        server=ThreadingHTTPServer(('127.0.0.1',0),module.handler(self.filter));thread=threading.Thread(target=server.serve_forever,daemon=True);thread.start()
        try:
            base=f'http://127.0.0.1:{server.server_port}'
            with self.assertRaises(urllib.error.HTTPError)as error:urllib.request.urlopen(base+'/v1/approvals')
            self.assertEqual(401,error.exception.code)
            req=urllib.request.Request(base+'/shell',data=b'{}',headers={'Authorization':'Bearer '+'a'*40})
            with self.assertRaises(urllib.error.HTTPError)as error:urllib.request.urlopen(req)
            self.assertEqual(404,error.exception.code)
        finally:server.shutdown();server.server_close();thread.join()
    def test_path_verification_rejects_broad_old_mount(self):
        self.filter.c.update(jellyfin_url='https://jellyfin.invalid',jellyfin_key='test-only',jellyfin_movies='/media/stak/movies',jellyfin_shows='/media/stak/shows')
        rows=[{'Locations':['/media/stak/movies']},{'Locations':['/media/stak/shows']}]
        with patch.object(module,'request_json',side_effect=lambda url,headers: rows if '/Library/' in url else [{'Name':self.filter.marker_name,'Type':'File'}]):self.assertTrue(self.filter.jellyfin_verified())
        with patch.object(module,'request_json',side_effect=lambda url,headers: rows if '/Library/' in url else []):self.assertFalse(self.filter.jellyfin_verified())
        with patch.object(module,'request_json',return_value=rows+[{'Locations':['/media/zurg']} ]):self.assertFalse(self.filter.jellyfin_verified())

if __name__=='__main__':unittest.main()
