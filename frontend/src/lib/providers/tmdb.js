import {externalRequest} from '../transport';
import {accountScope} from '../accountScope';
import {cached,DAY,mapLimit} from '../metadata/cache';
import {normalizeTitleShape,normalizeEpisode} from '../metadata/normalize';
export function tmdbAuth(key){const value=String(key || '').replace(/^Bearer\s+/i,'').trim();if(!value || /[\r\n]/.test(value))throw new Error('Configure your TMDb API key or read access token in Metadata sources.');return value.includes('.')?{headers:{Authorization:`Bearer ${value}`},params:{}}:{headers:{},params:{api_key:value}};}
export async function tmdbRequest(route,context,options={}){
 const auth=tmdbAuth(context.credentials.tmdb),scope=await accountScope(context.credentials.tmdb);
 // Metadata is always English, including legacy preferences and caller overrides.
 const params={...options.params,language:'en-US'};const key=`tmdb:${scope}:${route}:${JSON.stringify(params)}`;
 return cached(key,DAY,async()=>{const data=await externalRequest(`https://api.themoviedb.org/3${route}`,{...options,headers:auth.headers,params:{...auth.params,...params},signal:context.signal});if(!data || typeof data!=='object' || Array.isArray(data))throw new Error('TMDb returned malformed metadata.');return data;});
}
export const tmdbType=type=>type==='series' || type==='tv'?'tv':'movie';
export async function tmdbDetails(identity,context,{episodes=true}={}){
 const type=tmdbType(identity.type),route=`/${type}/${identity.tmdb_id}`;
 const data=await tmdbRequest(route,context,{params:{append_to_response:'external_ids,credits'}});
 let videos=[];const errors=[];
 if(type==='tv' && episodes){const seasons=await mapLimit((data.seasons || []).filter(s=>Number(s.season_number)>0),async s=>{try{
  const season=await tmdbRequest(`${route}/season/${s.season_number}`,context);
  return (season.episodes || []).map(e=>normalizeEpisode(e,identity));
 }catch{errors.push(`Season ${s.season_number} could not load.`);return [];}});videos=seasons.flat();}
 return normalizeTitleShape({...data,runtime:data.runtime || data.episode_run_time?.[0],videos,notices:errors},identity,'tmdb');
}
