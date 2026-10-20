import {externalRequest} from '../transport';
import {accountScope} from '../accountScope';
import {cached,cacheGet,cacheSet,DAY} from '../metadata/cache';
export async function omdbRequest(id,context,{season,episode,force=false}={}){
 if(!/^tt\d+$/.test(id || ''))throw new Error('OMDb requires an IMDb identifier.');
 if(!force && !context.settings.ratings)return null;
 const key=context.credentials.omdb;if(!key)throw new Error('Configure your OMDb API key in Metadata sources.');
 const scope=await accountScope(key),cooldown=`omdb-quota:${scope}`;
 return cached(`omdb:${scope}:${id}:${season || ''}:${episode || ''}`,DAY,async()=>{
  if(await cacheGet(cooldown)){const error=new Error('OMDb daily quota reached. Cached ratings remain available; try after the quota resets.');error.response={status:429};throw error;}
  let data;try{data=await externalRequest('https://www.omdbapi.com/',{params:{apikey:key,i:id,plot:'full',...(season?{Season:season,Episode:episode}:{})},signal:context.signal,noRetry:true});}catch(error){if(error.response?.status===429)await cacheSet(cooldown,true,DAY);throw error;}
  if(data?.Response==='False'){if(/limit|quota/i.test(data.Error || '')){await cacheSet(cooldown,true,DAY);const e=new Error('OMDb daily quota reached. Wait for the quota to reset.');e.response={status:429};throw e;}throw new Error(/api key/i.test(data.Error || '')?'OMDb rejected the API key.':'OMDb rating unavailable.');}
  if(!data || typeof data!=='object' || !('imdbRating' in data || 'Ratings' in data))throw new Error('OMDb returned malformed rating data.');
  return data;
 });
}
export function omdbRatings(data){return {imdb:Number(data?.imdbRating)>0?data.imdbRating:null,rt:data?.Ratings?.find(r=>r.Source==='Rotten Tomatoes')?.Value || null,metacritic:Number(data?.Metascore)>0?String(data.Metascore):null};}
