import {episodesOf,hasAired} from './episodes';
export function clampProgress(value){const number=Number(value);return Number.isFinite(number)?Math.min(100,Math.max(0,number)):0;}
export function seriesCompletion(videos,watched={}){
  const episodes=episodesOf(Array.isArray(videos)?videos:[]).filter(video=>video.season>0 && hasAired(video));
  const completed=episodes.filter(video=>watched[`${video.season}:${video.episode}`]?.played===true).length,total=episodes.length;
  return {videos:episodes,completed,total,remaining:total-completed,percentage:total?completed/total*100:0};
}
