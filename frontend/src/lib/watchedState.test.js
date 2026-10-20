import {mergedEpisodes,preferredWatched,jellyfinPlayback,jellyfinEpisodes} from './watchedState';
test('Jellyfin played state survives missing runtime and last-play dates',()=>{
 const remote=jellyfinPlayback({UserData:{Played:true}});expect(remote.played).toBe(true);expect(remote.progress).toBeNull();
 expect(preferredWatched({played:false},remote).played).toBe(true);
 expect(mergedEpisodes({item:{updated_at:'2026-10-19'},episodes:[{season:1,episode:1,type:'episode',played:false}]},[{season:1,episode:1,played:true}])['1:1'].played).toBe(true);
});
test('an unrelated manual episode edit never overrides remote watched state',()=>{
 const local={item:{updated_at:'2026-10-19'},episodes:[{season:1,episode:1,source:'manual',played:false,updated_at:'2026-10-01',synced_at:'2026-10-01'},{season:1,episode:2,source:'manual',played:false,sync_pending:true,updated_at:'2026-10-19'}]};
 const merged=mergedEpisodes(local,[{season:1,episode:1,played:true,last_played:'2026-10-18'},{season:1,episode:2,played:true,last_played:'2026-10-18'}]);
 expect(merged['1:1'].played).toBe(true);expect(merged['1:2'].played).toBe(false);expect(local.episodes[0].played).toBe(false);
});
test('successfully synced local state does not mask later undated remote changes',()=>{
 expect(preferredWatched({source:'manual',played:false,updated_at:'2026-10-18',synced_at:'2026-10-18'},{played:true,last_played:null}).played).toBe(true);
 expect(preferredWatched({source:'manual',played:false,sync_pending:true},{played:true}).played).toBe(false);
});
test('alternate versions deduplicate coordinates and preserve confirmed watched state',()=>{
 const items=[{Id:'one',ParentIndexNumber:1,IndexNumber:2,UserData:{Played:false}},{Id:'two',ParentIndexNumber:1,IndexNumber:2,UserData:{Played:true}}];
 expect(jellyfinEpisodes(items)).toHaveLength(1);expect(jellyfinEpisodes(items)[0].played).toBe(true);
 expect(jellyfinEpisodes([{ParentIndexNumber:0,IndexNumber:1,UserData:{}}])[0].played).toBeNull();
});

test('confirmed series Played state fills missing aired episodes and ignores legacy title-wide false flags',()=>{
 const {effectiveEpisodes}=require('./watchedState'),meta={videos:[{season:1,episode:1},{season:1,episode:2},{season:2,episode:1,released:'2099-01-01'}]};
 const result=effectiveEpisodes({item:{updated_at:'2026-10-19'},episodes:[{season:1,episode:1,played:false}]},{state:'available',title_state:{played:true},episodes:[]},meta);
 expect(result['1:1'].played).toBe(true);expect(result['1:2'].played).toBe(true);expect(result['2:1']).toBeUndefined();
});
test('explicit manual unwatch survives parent fallback, until a newer dated server watched update',()=>{
 const {effectiveEpisodes}=require('./watchedState'),local={episodes:[{season:1,episode:1,played:false,source:'manual',updated_at:'2026-10-19',sync_pending:true}]},meta={videos:[{season:1,episode:1}]};
 expect(effectiveEpisodes(local,{state:'available',title_state:{played:true}},meta)['1:1'].played).toBe(false);
 local.episodes[0].sync_pending=false;
 expect(effectiveEpisodes(local,{state:'available',title_state:{played:true,last_played:'2026-10-20'}},meta)['1:1'].played).toBe(true);
});
test('app-wide tracking uses Jellyfin watched flags, aliases and fresh observations without changing stored records',()=>{
 const {effectiveTracking}=require('./watchedState'),saved=[{imdb_id:'tt123',type:'series',played:false,episodes:[]}],catalog={checked_at:'2026-10-18',items:[{ids:['tt123','tmdb:1'],type:'series',played:true}]};
 const map=effectiveTracking(saved,catalog);expect(map.get('tt123').played).toBe(true);expect(map.get('tmdb:1').played).toBe(true);expect(saved[0].played).toBe(false);
 expect(effectiveTracking(saved,catalog,[{id:'tt123',type:'series',played:false,checked_at:'2026-10-19'}]).get('tt123').played).toBe(false);
 expect(effectiveTracking(saved,catalog,[{id:'tt123',type:'series',played:false,checked_at:'2026-10-17'}]).get('tt123').played).toBe(true);
});
test('pending manual episode unwatch prevents app-wide whole-series watched filtering',()=>{
 const {effectiveTracking}=require('./watchedState');
 const map=effectiveTracking([{imdb_id:'tt123',type:'series',episodes:[{season:1,episode:1,played:false,source:'manual',updated_at:'2026-10-19',sync_pending:true}]}],{items:[{ids:['tt123'],type:'series',played:true}]});
 expect(map.get('tt123').played).toBe(false);
});

test('movie and app-wide flags prefer confirmed watched over legacy false while preserving explicit unwatch edits',()=>{
 const {effectiveTitleWatched,effectiveTracking}=require('./watchedState'),remote={played:true},legacy={played:false,updated_at:'2026-10-19'};
 expect(effectiveTitleWatched(legacy,remote).played).toBe(true);
 expect(effectiveTitleWatched({...legacy,source:'manual',synced_at:'2026-10-19'},remote).played).toBe(false);
 expect(effectiveTracking([{...legacy,imdb_id:'tt123',type:'movie'}],{items:[{type:'movie',ids:['tt123'],played:true}]}).get('tt123').played).toBe(true);
});
