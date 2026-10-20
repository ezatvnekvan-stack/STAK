import {cleanName} from './format';

export function collectionPreferences(section,value={}) {
  const v=value && typeof value==='object'?value:{};
  if(section==='watchlist')return {tab:['all','movie','series'].includes(v.tab)?v.tab:'all',sort:['added','year','rating','name'].includes(v.sort)?v.sort:'added',layout:v.layout==='list'?'list':'grid',hideWatched:v.hideWatched===true,cachedOnly:v.cachedOnly===true};
  return {tab:['all','active','done','error'].includes(v.tab)?v.tab:'all',sort:['date','name','size'].includes(v.sort)?v.sort:'date'};
}

// Filename cleanup only: never claim a metadata match from a release filename.
export function torrentPresentation(torrent) {
  const original=String(torrent.name || '').trim();
  const unidentified=!original || /^(?:[a-f\d]{40}|[a-f\d]{64})$/i.test(original) || original.toLowerCase()===String(torrent.hash || '').toLowerCase();
  const season=original.match(/\bS(\d{1,2})(?:E(\d{1,3}))?\b/i);
  const coordinates=season?`Season ${Number(season[1])}${season[2]?` · Episode ${Number(season[2])}`:''}`:'';
  const title=unidentified?'Unidentified torrent':cleanName(original);
  return {title,coordinates,original,identifier:String(torrent.hash || original).slice(0,12),unidentified};
}

export function watchlistItems(items,tracking,availability,{tab='all',sort='added',hideWatched=false,cachedOnly=false,search=''}={}) {
  const q=search.trim().toLocaleLowerCase();
  const year=item=>parseInt(String(item.year).slice(0,4),10)||0;
  const sorts={added:(a,b)=>String(b.added_at || b.created_at || '').localeCompare(String(a.added_at || a.created_at || '')),year:(a,b)=>year(b)-year(a),rating:(a,b)=>(parseFloat(b.rating)||0)-(parseFloat(a.rating)||0),name:(a,b)=>String(a.name||'').localeCompare(String(b.name||''))};
  return items.filter(item=>(tab==='all'||item.type===tab)&&(!q||String(item.name||'').toLocaleLowerCase().includes(q))&&(!hideWatched||!tracking.get(item.imdb_id)?.played)&&(!cachedOnly||availability[item.imdb_id]>0)).sort(sorts[sort]||sorts.added);
}
