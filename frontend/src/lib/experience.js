import {normalizeTracking} from './trackingModel';
import {collectionPreferences} from './collectionModel';
// Only non-secret, bounded view preferences are accepted. Account data and
// credentials never belong in this object. Older backups remain unchanged.
export function experienceSection(section, value) {
  const v=value && typeof value==='object' && !Array.isArray(value)?value:{};
  if(section==='tracking')return normalizeTracking(v);
  if(section==='watchlist' || section==='torbox')return collectionPreferences(section,v);
  if(section==='discovery') {
    const filters={};
    for(const key of ['genre','year_from','year_to','imdb_rating','certification','language','provider','sort']) filters[key]=typeof v.filters?.[key]==='string'?v.filters[key].slice(0,80):'';
    return {type:v.type==='series'?'series':'movie',category:['trending','popular','top','new','upcoming','hidden','explore','catalog'].includes(v.category)?v.category:'trending',period:v.period==='day'?'day':'week',region:/^[A-Z]{2}$/.test(v.region)?v.region:'US',view:['browse','services'].includes(v.view)?v.view:'browse',hideWatched:v.hideWatched===true,hideLibrary:v.hideLibrary===true,filters};
  }
  if(section==='sources') return {res:['All','4K','1080p'].includes(v.res)?v.res:'All',sort:['best','size','seeders'].includes(v.sort)?v.sort:'best',maxSize:Number(v.maxSize)>0 && Number(v.maxSize)<=10000?String(v.maxSize):'',preferHun:v.preferHun===true,cachedOnly:v.cachedOnly===true,hdr:v.hdr===true,dv:v.dv===true,atmos:v.atmos===true,remux:v.remux===true,webdl:v.webdl===true};
  throw new Error('Unknown preference section.');
}
export function catalogMatch(item, catalog) {
  const identities=new Set([item.imdb_id,item.id,item.tmdb_id && `tmdb:${item.tmdb_id}`].filter(Boolean).map(String));
  return (catalog?.items || []).find(entry=>entry.type===item.type && entry.ids?.some(id=>identities.has(id)));
}
export function filterKnownTitles(items, tracking, catalog, {hideWatched=false,hideLibrary=false}={}) {
  return items.filter(item=>!(hideWatched && tracking?.get(item.imdb_id || item.id)?.played) && !(hideLibrary && catalogMatch(item,catalog)));
}
export function releaseOrder(items) {
  return [...items].sort((a,b)=>String(a.release_date || a.first_air_date || a.releaseInfo || a.year || '9999').localeCompare(String(b.release_date || b.first_air_date || b.releaseInfo || b.year || '9999')));
}
export function snapshotItem(item) {
  const result={};
  for(const name of ['Id','Name','Type','ProductionYear','SeriesId','SeriesName','ParentIndexNumber','IndexNumber','PrimaryImageAspectRatio','PrimaryImageItemId','PresentationUniqueKey']) if(['string','number'].includes(typeof item[name]))result[name]=item[name];
  for(const name of ['ProviderIds','ImageTags','UserData']) {
    const value=item[name];if(!value || typeof value!=='object')continue;
    const allowed=name==='ProviderIds'?['Imdb','Tmdb','imdb','tmdb']:name==='ImageTags'?['Primary','Backdrop','Thumb','Logo']:['Played','PlayedPercentage','PlaybackPositionTicks','LastPlayedDate','IsFavorite'];
    result[name]=Object.fromEntries(allowed.filter(key=>['string','number','boolean'].includes(typeof value[key])).map(key=>[key,value[key]]));
  }
  if(Array.isArray(item.BackdropImageTags))result.BackdropImageTags=item.BackdropImageTags.filter(x=>typeof x==='string').slice(0,2);
  return result;
}
