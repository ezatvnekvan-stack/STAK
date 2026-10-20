import {mapLimit} from './cache';
import {resolveIdentity} from './resolveIdentity';
import {tmdbRequest,tmdbType} from '../providers/tmdb';
import {orderedCalendarEvents,validDate} from '../calendarModel';
import {image} from './normalize';
// Select seasons overlapping the requested window; never fetch a show's full history.
export function seasonsForRange(summary,start,end) {
 const listed=(summary.seasons || []).filter(season=>Number(season.season_number)>0 && validDate(season.air_date)).sort((a,b)=>a.air_date.localeCompare(b.air_date));
 const selected=listed.filter((season,index)=>season.air_date<=end && (!listed[index+1] || listed[index+1].air_date>=start)).map(season=>Number(season.season_number));
 for(const episode of [summary.next_episode_to_air,summary.last_episode_to_air])if(episode && Number(episode.season_number)>0 && (!listed.length || (episode.air_date>=start && episode.air_date<=end)))selected.push(Number(episode.season_number));
 const unique=[...new Set(selected)];return {seasons:unique.slice(0,4),limited:unique.length>4};
}
export async function calendarEntries(followed,{today,start=today,end},context) {
 const events=[],errors=[];
 await mapLimit(followed,async item=>{try{
  const identity=await resolveIdentity({id:item.imdb_id || item.id,type:item.type},context);
  if(!identity.tmdb_id)throw new Error('TMDb identity unavailable.');
  const type=tmdbType(identity.type),summary=await tmdbRequest(`/${type}/${identity.tmdb_id}`,context),candidates=[];
  if(type==='tv') {
   for(const episode of [summary.next_episode_to_air,summary.last_episode_to_air])if(episode)candidates.push(episode);
   const {seasons,limited}=seasonsForRange(summary,start,end);
   if(limited)errors.push({imdb_id:item.imdb_id,error:'Season coverage limited for this date range.'});
   if(!seasons.length && !summary.next_episode_to_air && !summary.last_episode_to_air && (summary.seasons || []).some(season=>Number(season.season_number)>0 && !validDate(season.air_date)))errors.push({imdb_id:item.imdb_id,error:'Season release dates have not been announced.'});
   for(const season of seasons) {
    try {const data=await tmdbRequest(`/tv/${identity.tmdb_id}/season/${season}`,context);candidates.push(...(data.episodes || []));}
    catch(error){if(error.code==='ERR_CANCELED')throw error;errors.push({imdb_id:item.imdb_id,error:'A season could not be checked.'});}
   }
  } else candidates.push({air_date:summary.release_date,name:summary.title});
  for(const episode of candidates) {
   const date=String(episode.air_date || '').slice(0,10);if(!validDate(date) || date<start || date>end)continue;
   events.push({date,released:date,type:identity.type,imdb_id:identity.imdb_id || (identity.tmdb_id?`tmdb:${identity.tmdb_id}`:item.imdb_id),name:item.name || summary.name || summary.title,poster:item.poster || image(summary.poster_path),background:image(episode.still_path || summary.backdrop_path),title:episode.name,season:episode.season_number,episode:episode.episode_number});
  }
 } catch(error){if(error.code==='ERR_CANCELED')throw error;errors.push({imdb_id:item.imdb_id,error:'Calendar metadata unavailable. Configure TMDb or retry this title.'});}});
 return {today,range_start:start,range_end:end,events:orderedCalendarEvents(events),errors,checked_titles:followed.length,coverage:'Dates reported by TMDb for the selected titles and matching seasons; unannounced dates may be missing.'};
}
