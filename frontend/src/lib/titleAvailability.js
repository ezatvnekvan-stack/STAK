import {useEffect} from 'react';
import {useApp} from '../context/AppContext';
import {useAccountQuery} from './useAccountQuery';
export {mergedEpisodes as mergedWatched,preferredWatched,effectiveEpisodes,effectiveTitleWatched} from './watchedState';
export function useTitleAvailability(meta,ep) {
  const id=meta?.imdb_id || meta?.id || '';
  const {rememberTitleAvailability}=useApp() || {};
  const query=useAccountQuery(`/jellyfin/title-availability?imdb_id=${encodeURIComponent(id)}&type=${meta?.type || ''}&season=${ep.s}&episode=${ep.e}${meta?.tmdb_id?`&tmdb_id=${meta.tmdb_id}`:''}`,{enabled:!!id,staleTime:10000,refetchOnMount:'always'});
  useEffect(()=>{if(query.data && !query.error)rememberTitleAvailability?.(id,meta?.type,query.data,query.dataUpdatedAt,meta?.tmdb_id?[`tmdb:${meta.tmdb_id}`]:[]);},[id,meta?.type,query.data,query.dataUpdatedAt,query.error,rememberTitleAvailability,meta?.tmdb_id]);
  return query;
}
