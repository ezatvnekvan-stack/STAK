export function localDay(value) {
  const date = new Date(typeof value === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(value) ? `${value}T12:00:00` : value);
  if (!value || Number.isNaN(date.getTime())) return null;
  return `${date.getFullYear()}-${String(date.getMonth()+1).padStart(2,'0')}-${String(date.getDate()).padStart(2,'0')}`;
}
export function groupActivity(items=[]) {
  const groups=new Map();
  for(const item of items){const day=localDay(item.last_played);const key=day || 'undated';if(!groups.has(key))groups.set(key,[]);groups.get(key).push(item);}
  return [...groups.entries()].sort(([a],[b])=>a==='undated'?1:b==='undated'?-1:b.localeCompare(a));
}
export function activityHref(item) {
  const id=String(item.imdb_id || '');
  if(!/^(tt\d+|tmdb:\d+)$/.test(id))return `/search?q=${encodeURIComponent(item.series_name || item.name || '')}`;
  const episode=item.type==='episode' && Number.isInteger(Number(item.season)) && Number(item.season)>=0 && Number(item.episode)>0;
  return `/title/${item.type==='movie'?'movie':'series'}/${id}${episode?`?season=${Number(item.season)}&episode=${Number(item.episode)}`:''}`;
}
export const boundedProgress=value=>Math.max(0,Math.min(100,Number(value)||0));
