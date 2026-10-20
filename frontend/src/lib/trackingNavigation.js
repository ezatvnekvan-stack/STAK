export const NAV_PREF_KEY='dmm-v3-navigation'; // Preserve the existing key and user ordering.
export const DEFAULT_NAV=['home','discover','watchlist','tracking','library','account'];
export function migrateNavigation(ids) {
  const allowed=new Set([...DEFAULT_NAV,'torbox']);
  const result=[...new Set(ids.map(id=>['history','calendar','progress'].includes(id)?'tracking':id).filter(id=>allowed.has(id)))];
  for(const id of ['home','account'])if(!result.includes(id))result.push(id);
  return result;
}
export function readNavPrefs() {
  try {
    const current=localStorage.getItem(NAV_PREF_KEY),legacy=localStorage.getItem('dmm-v2-navigation');
    const value=JSON.parse(current || legacy || 'null');
    if(Array.isArray(value))return migrateNavigation(!current && !value.some(id=>['history','calendar','progress','tracking'].includes(id))?[...value,'tracking']:value);
  } catch { /* Keep navigation available when saved values are malformed. */ }
  return [...DEFAULT_NAV];
}
