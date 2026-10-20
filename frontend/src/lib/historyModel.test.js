import {mergeHistory,compactHistory} from './historyModel';
test('history merges identities without duplicates, preserves local-only dates and explicit unwatch',()=>{
 const remote={recent:[{imdb_id:'tt1',type:'movie',played:true,last_played:'2020-01-01'},{imdb_id:'tt2',type:'episode',season:1,episode:1,played:true,last_played:'2020-02-01'}],continue:[]};
 const local=[{imdb_id:'tt1',type:'movie',played:false,source:'manual',sync_pending:true},{imdb_id:'tt2',type:'series',episodes:[{season:1,episode:1,played:true,source:'manual',last_played:'2020-02-01'},{season:1,episode:2,played:true,last_played:'2020-03-01'}]},{imdb_id:'tt3',type:'movie',played:true,last_played:'2010-01-01'}];
 const result=mergeHistory(remote,local);expect(result.recent).toHaveLength(3);expect(result.recent.some(row=>row.imdb_id==='tt1')).toBe(false);expect(result.recent.find(row=>row.imdb_id==='tt3').last_played).toBe('2010-01-01');
});
test('compact history groups a series once at its latest date and keeps known progress honest',()=>{
 const rows=[{imdb_id:'tt2',type:'episode',season:1,episode:1,series_name:'Show',last_played:'2020-01-01',total_episodes:4},{imdb_id:'tt2',type:'episode',season:1,episode:2,series_name:'Show',last_played:'2020-02-01',total_episodes:4},{imdb_id:'tt2',type:'episode',season:1,episode:2,series_name:'Show',last_played:'2020-02-01'},{imdb_id:'tt3',type:'movie',name:'Movie',last_played:'2020-01-05'}];
 const result=compactHistory(rows);expect(result).toHaveLength(2);expect(result[0]).toEqual(expect.objectContaining({type:'series',name:'Show',watched_count:2,total_episodes:4,progress:50,last_played:'2020-02-01'}));expect(result[0].season).toBeUndefined();
 expect(compactHistory([{imdb_id:'tt2',type:'episode',season:1,episode:1}])[0].total_episodes).toBeNull();
});
test('confirmed series completion stays complete when the history page contains only part of its episodes',()=>{
 const rows=[{imdb_id:'tt1',type:'episode',season:1,episode:2,total_episodes:8}],tracking=new Map([['tt1',{played:true}]]);
 expect(compactHistory(rows,tracking)[0]).toEqual(expect.objectContaining({played:true,progress:100,watched_count:8,total_episodes:8}));
});
