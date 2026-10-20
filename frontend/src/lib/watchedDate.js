import {dateKey,validDate} from './calendarModel';

// Keep the viewing date separate from the time the user edited their progress.
// Civil dates are represented at local noon to avoid UTC midnight day shifts.
export function watchedTimestamp(value,now=new Date()) {
  if(value==null)return now.toISOString();
  if(typeof value!=='string' || !validDate(value) || value>dateKey(now))throw new Error('Choose a valid watched date that is not in the future.');
  return value===dateKey(now)?now.toISOString():new Date(`${value}T12:00:00`).toISOString();
}
export function releaseDay(meta,episode) {
  const video=episode && (meta.videos || []).find(v=>Number(v.season)===Number(episode[0]) && Number(v.episode)===Number(episode[1]));
  const raw=video?(video.released || video.air_date):(meta.released || meta.release_date || meta.releaseDate || meta.releaseInfo);
  const date=typeof raw==='string'?raw.slice(0,10):'';
  return validDate(date) && date<=dateKey()?date:null;
}
export function watchedDates(meta,episodes=[],date,release=false) {
  if(episodes.length)return {watched_dates:episodes.map(([season,episode])=>{
    const day=release?releaseDay(meta,[season,episode]):date;
    if(!day)throw new Error('A released date is required for every selected episode.');
    watchedTimestamp(day);return {season,episode,date:day};
  })};
  const day=release?releaseDay(meta):date;
  if(!day)throw new Error('The release date is unavailable. Choose a watched date instead.');
  watchedTimestamp(day);return {watched_on:day};
}
