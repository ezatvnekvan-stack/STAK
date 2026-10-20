import {episodesOf,hasAired} from './episodes';
const time=value=>Date.parse(value || '') || 0;
// A local edit has its own timestamp. Imported episodes must never inherit a
// different episode's title-wide edit time. Read merges never rewrite local data.
export function preferredWatched(local,remote,legacyUpdated='') {
  if(typeof remote?.played!=='boolean')return local || remote;
  if(typeof local?.played!=='boolean')return remote;
  const manual=local.source==='manual' || (local.source!=='jellyfin' && local.type!=='episode');
  const localTime=time(local.updated_at) || (manual?time(legacyUpdated):time(local.last_played));
  const remoteTime=time(remote.last_played);
  if(local.sync_pending===true)return local;
  if(manual && localTime && ((remoteTime && localTime>remoteTime) || (!remoteTime && !local.synced_at)))return local;
  if(!manual && remoteTime && time(local.last_played)>remoteTime)return local;
  return remote;
}
export function mergedEpisodes(local,remote) {
  const states={};
  for(const episode of Array.isArray(remote)?remote:[]){
    const s=Number(episode.season),e=Number(episode.episode);if(Number.isInteger(s)&&s>=0&&Number.isInteger(e)&&e>0)states[`${s}:${e}`]=episode;
  }
  for(const episode of Array.isArray(local?.episodes)?local.episodes:[]){
    const s=Number(episode.season),e=Number(episode.episode);if(!Number.isInteger(s)||s<0||!Number.isInteger(e)||e<=0)continue;
    const key=`${s}:${e}`;states[key]=preferredWatched(episode,states[key],local.item?.updated_at);
  }
  return states;
}
export function jellyfinPlayback(item) {
  if(!item)return null;
  const runtime=Math.max(0,Number(item.RunTimeTicks)||0),position=Math.max(0,Number(item.UserData?.PlaybackPositionTicks)||0);
  return {played:typeof item.UserData?.Played==='boolean'?item.UserData.Played:null,last_played:item.UserData?.LastPlayedDate || null,progress:runtime?Math.min(100,position/runtime*100):null,position_ticks:position,runtime_ticks:runtime};
}
export function jellyfinEpisodes(items) {
  const states=new Map();
  for(const item of items){
    const season=Number(item.ParentIndexNumber),episode=Number(item.IndexNumber);if(!Number.isInteger(season)||season<0||!Number.isInteger(episode)||episode<=0)continue;
    const state={...jellyfinPlayback(item),season,episode,source:'jellyfin'},key=`${season}:${episode}`,old=states.get(key);
    // Identical episode coordinates across verified versions represent one title.
    if(!old || (state.played===true && old.played!==true) || (state.played===old.played && time(state.last_played)>time(old.last_played)))states.set(key,state);
  }
  return [...states.values()].sort((a,b)=>a.season-b.season||a.episode-b.episode);
}
export async function readJellyfinEpisodes(request,userId,seriesIds) {
  const items=[],seen=new Set();let complete=true,error='';
  for(const id of seriesIds.slice(0,4)){
    let start=0,done=false;
    for(let page=0;page<20;page++){
      try{
        const data=await request(`/Shows/${encodeURIComponent(id)}/Episodes`,{params:{UserId:userId,StartIndex:start,Limit:250,EnableUserData:true,EnableTotalRecordCount:true,Fields:'UserData,RunTimeTicks'}});
        if(!Array.isArray(data.Items))throw new Error('Jellyfin returned invalid episode data.');
        const rows=data.Items,total=Number(data.TotalRecordCount);let added=0;
        for(const item of rows){const key=`${id}:${item.Id || `${item.ParentIndexNumber}:${item.IndexNumber}`}`;if(!seen.has(key)){seen.add(key);items.push(item);added++;}}
        start+=rows.length;
        if(data.TotalRecordCount!=null && Number.isInteger(total)&&total>=0&&start>=total){done=true;break;}
        if(!rows.length || !added){complete=false;error='Jellyfin episode coverage could not be confirmed.';break;}
      }catch(failure){complete=false;error=failure.message || 'Jellyfin episode state could not load.';break;}
    }
    if(!done)complete=false;
  }
  if(seriesIds.length>4){complete=false;error='Some alternate versions were not checked.';}
  return {items,complete,error:error || (complete?'':'Episode lookup reached its safety limit.')};
}

// A Jellyfin series Played flag is real watched state, not a decorative badge.
// Keep explicit newer manual unwatch edits; never infer future episodes watched.
function manualUnwatched(local,remote) {
  return local?.played===false && (local.sync_pending===true || (local.source==='manual' && time(local.updated_at)>0 && (!time(remote?.last_played) || time(local.updated_at)>=time(remote.last_played))));
}
export function effectiveTitleWatched(local,remote) {
  if(remote?.played===true && manualUnwatched(local,remote))return local;
  if(remote?.played===true && !manualUnwatched(local,remote) && local?.sync_pending!==true)return remote;
  return preferredWatched(local,remote,local?.updated_at);
}
export function effectiveEpisodes(local,availability,meta,now=Date.now()) {
  const remote=Array.isArray(availability?.episodes)?availability.episodes:[];
  const parent=availability?.state==='available'?availability.title_state:null;
  if(parent?.played!==true)return mergedEpisodes(local,remote);
  const states=Object.fromEntries(remote.map(row=>[`${row.season}:${row.episode}`,row]));
  for(const video of episodesOf(meta?.videos || []).filter(v=>v.season>0 && hasAired(v,now))) {
    const key=`${video.season}:${video.episode}`,row=states[key];
    if(row?.played===false && time(row.last_played)>time(parent.last_played) && time(parent.last_played)>0)continue;
    states[key]={...row,season:video.season,episode:video.episode,played:true,progress:100,source:'jellyfin',from_series:true,last_played:parent.last_played || null};
  }
  // Legacy title-wide timestamps cannot override a confirmed whole-series flag.
  for(const row of local?.episodes || []) {
    const key=`${row.season}:${row.episode}`;
    if(states[key]?.from_series) {if(manualUnwatched(row,parent))states[key]=row;}
    else states[key]=preferredWatched(row,states[key],local.item?.updated_at);
  }
  return states;
}
export function effectiveTracking(records,catalog,observations=[]) {
  const map=new Map((Array.isArray(records)?records:[]).filter(row=>row && typeof row==='object').map(row=>[row.imdb_id,row]));
  const remote=new Map();
  for(const row of catalog?.items || [])for(const id of row.ids || [])if(typeof row.played==='boolean')remote.set(id,{...row,id,last_played:row.last_played});
  for(const row of observations) {
    if(time(row.checked_at)<time(catalog?.checked_at))continue;
    for(const id of new Set([row.id,...(row.ids || [])])) {
      if(row.excluded)remote.delete(id);
      else if(typeof row.played==='boolean')remote.set(id,row);
    }
  }
  for(const [id,row] of remote) {
    const local=map.get(id),episodes=mergedEpisodes({item:local,episodes:local?.episodes},row.episodes);
    const state=effectiveTitleWatched(local,row);
    const played=row.played===true && local?.sync_pending!==true
      ? row.type==='series'?!manualUnwatched(local,row) && !Object.values(episodes).some(ep=>manualUnwatched(ep,row)):state?.played
      :state?.played;
    map.set(id,{...local,imdb_id:id,type:row.type || local?.type,played,episodes:Object.values(episodes),progress:played?100:local?.progress || 0});
  }
  return map;
}
