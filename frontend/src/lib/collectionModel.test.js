import {collectionPreferences,torrentPresentation,watchlistItems} from './collectionModel';
const items=[{imdb_id:'tt1',name:'Zebra',type:'movie',year:2020,added_at:'2026-01-01'},{imdb_id:'tt2',name:'Alpha',type:'series',year:2025,added_at:'2026-02-01'}];
test('readable torrent names retain original identity and episode coordinates',()=>{
 const name='Crystal.Lake.S01E02.2160p.WEB-DL.HDR';
 expect(torrentPresentation({name,hash:'a'.repeat(40)})).toMatchObject({title:'Crystal Lake',coordinates:'Season 1 · Episode 2',original:name,unidentified:false});
});
test('hash-only and missing names remain honestly unidentified',()=>{
 expect(torrentPresentation({name:'a'.repeat(40),hash:'a'.repeat(40)})).toMatchObject({title:'Unidentified torrent',unidentified:true});
 expect(torrentPresentation({})).toMatchObject({title:'Unidentified torrent',unidentified:true});
});
test('watchlist combines search, media, watched and known cache filters without mutating input',()=>{
 const track=new Map([['tt1',{played:true}]]);
 expect(watchlistItems(items,track,{}, {search:' ALP ',hideWatched:true}).map(i=>i.imdb_id)).toEqual(['tt2']);
 expect(watchlistItems(items,track,{}, {tab:'movie',hideWatched:true})).toEqual([]);
 expect(watchlistItems(items,track,{tt1:-1,tt2:0},{cachedOnly:true})).toEqual([]);
 expect(watchlistItems(items,track,{tt2:1},{cachedOnly:true})).toEqual([items[1]]);
 expect(items[0].imdb_id).toBe('tt1');
});
test('watchlist sorting is deterministic and falls back safely',()=>{
 expect(watchlistItems(items,new Map(),{},{}).map(i=>i.name)).toEqual(['Alpha','Zebra']);
 expect(watchlistItems(items,new Map(),{},{sort:'name'}).map(i=>i.name)).toEqual(['Alpha','Zebra']);
 expect(watchlistItems(items,new Map(),{},{sort:'invalid'})).toEqual(watchlistItems(items,new Map(),{},{}));
});
test('collection preferences accept supported controls only and never store search or secrets',()=>{
 expect(collectionPreferences('watchlist',{sort:'wrong',layout:'list',hideWatched:true,search:'private',token:'private'})).toEqual({tab:'all',sort:'added',layout:'list',hideWatched:true,cachedOnly:false});
 expect(collectionPreferences('torbox',{tab:'error',sort:'size',selected:[1]})).toEqual({tab:'error',sort:'size'});
});
