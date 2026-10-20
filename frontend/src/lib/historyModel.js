import {preferredWatched} from './watchedState';

const identity=row=>`${row.imdb_id}:${row.type==='episode'?`${Number(row.season)}:${Number(row.episode)}`:'movie'}`;
export function mergeHistory(remote,records=[]) {
  const states=new Map();
  for(const row of [...(remote?.recent || []),...(remote?.continue || [])])states.set(identity(row),row);
  for(const title of records) {
    const rows=title.type==='series'?(title.episodes || []).map(ep=>({...ep,imdb_id:title.imdb_id,series_name:title.name,name:title.name,type:'episode',total_episodes:title.total_episodes})): [{...title,type:'movie'}];
    for(const row of rows) {
      const key=identity(row),other=states.get(key),chosen=preferredWatched(row,other,title.updated_at);
      states.set(key,{...other,...chosen});
    }
  }
  const rows=[...states.values()],sort=(a,b)=>String(b.last_played || '').localeCompare(String(a.last_played || ''));
  return {...remote,recent:rows.filter(row=>row.played===true).sort(sort),continue:rows.filter(row=>!row.played && Number(row.progress)>0).sort(sort)};
}

// One row per title, positioned at its latest watched date, never per episode.
export function compactHistory(rows=[],tracking=new Map()) {
  const groups=new Map();
  for(const row of rows) {
    const type=row.type==='episode'?'series':'movie',key=`${type}:${row.imdb_id}`;
    if(!groups.has(key))groups.set(key,{...row,type,name:row.series_name || row.name,season:undefined,episode:undefined,watched:new Set()});
    const group=groups.get(key);
    if(type==='series')group.watched.add(`${row.season}:${row.episode}`);
    if(String(row.last_played || '')>String(group.last_played || ''))group.last_played=row.last_played;
    if(Number(row.total_episodes)>0)group.total_episodes=Number(row.total_episodes);
  }
  return [...groups.values()].map(group=>{
    const local=tracking.get(group.imdb_id);
    for(const ep of local?.episodes || [])if(ep.played && Number(ep.season)>0)group.watched.add(`${ep.season}:${ep.episode}`);
    const count=group.watched.size,total=Number(group.total_episodes || local?.total_episodes) || null;
    const complete=group.type==='movie' || local?.played===true || !!(total && count>=total);
    return {...group,watched:undefined,watched_count:complete && total?total:count,total_episodes:total && total>=count?total:null,played:complete,progress:complete?100:total?Math.min(100,count/total*100):0};
  }).sort((a,b)=>String(b.last_played || '').localeCompare(String(a.last_played || '')));
}
