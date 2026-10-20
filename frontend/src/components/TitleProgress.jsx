import WatchedDateDialog from './WatchedDateDialog';
import {watchedTimestamp} from '../lib/watchedDate';
import { useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { api, errMsg, openExternal } from '../lib/api';
import { useApp } from '../context/AppContext';
import { accountQueryKey, accountRequest } from '../lib/accountQueries';
import {seriesCompletion} from '../lib/progress';
import ProgressIndicator from './ProgressIndicator';
import { useTitleAvailability, effectiveEpisodes, effectiveTitleWatched } from '../lib/titleAvailability';
import { useAccountQuery } from '../lib/useAccountQuery';
import JellyfinLogo from './JellyfinLogo';
import { AlertDialog, AlertDialogContent, AlertDialogTitle, AlertDialogDescription, AlertDialogFooter, AlertDialogCancel, AlertDialogAction } from './ui/alert-dialog';
export function JellyfinAvailability({query,episodeOnly=false}){
  const data=query.data,state=query.error?'unknown':episodeOnly && data?.state==='available'?(data.episode_available===true?'available':data.episode_available===false?'unavailable':'unknown'):data?.state || 'unknown';
  const label=query.isFetching?'Checking library':state==='available'?'Available':state==='unavailable'?'Not in library':state==='disconnected'?'Disconnected':'Availability unknown';
  return <div className="jellyfin-availability" data-state={query.isFetching?'unknown':state} aria-label={`Jellyfin · ${label}`}><JellyfinLogo size={18}/><span>Jellyfin · {label}</span>{data?.web_url && state==='available' && !query.isFetching && !query.error && <button className="ml-auto n-icon-btn jellyfin-open" aria-label="Open in Jellyfin" title="Open in Jellyfin" onClick={()=>openExternal(data.web_url)}><JellyfinLogo/></button>}{query.error && <button className="n-chip nothing-action" title={errMsg(query.error)} onClick={()=>query.refetch()}>Retry</button>}</div>;
}
export default function TitleProgress({meta,ep,episodeOnly=false}){
  const {refreshTracking,session}=useApp(),client=useQueryClient(),id=meta.imdb_id || meta.id;
  const path=`/tracking/title/${id}`,tracking=useAccountQuery(path),availability=useTitleAvailability(meta,ep),[busy,setBusy]=useState(false),[confirmSeason,setConfirmSeason]=useState(null),[dateEpisodes,setDateEpisodes]=useState(null);
  const series=meta.type==='series',watched=effectiveEpisodes(tracking.data,availability.data,meta),completion=seriesCompletion(meta.videos,watched),videos=completion.videos;
  const current=series?watched[`${ep.s}:${ep.e}`]:effectiveTitleWatched(tracking.data?.item,availability.data?.title_state || availability.data?.playback);
  const playback=availability.data?.playback?.position_ticks>0?{...availability.data.playback,season:ep.s,episode:ep.e}:(!episodeOnly && availability.data?.resume_playback) || (!current?.played && Number(current?.progress)>0?current:null);
  const localOnly=['unavailable','disconnected'].includes(availability.data?.state);
  const loadingState=!localOnly && !availability.error && (availability.isPending || availability.isFetching) && !availability.data;
  const known=!!(localOnly || tracking.data?.item || tracking.data?.episodes?.length || availability.data?.title_state?.played===true || availability.data?.episodes?.length || availability.data?.episodes_complete===true),prefix=episodeOnly?'episode-':'';
  const seasons=[...new Set(videos.map(video=>video.season))],incomplete=seasons.filter(season=>videos.some(video=>video.season===season && !watched[`${season}:${video.episode}`]?.played));
  const seasonToMark=incomplete.find(season=>season>=ep.s) || incomplete[0];
  const mark=async(played,episodes,datePayload={})=>{
    if(busy)return;setBusy(true);
    try{
      const {data}=await api.post('/tracking/mark',{imdb_id:id,type:meta.type,name:meta.name,played,episodes,...(series && completion.total?{total_episodes:completion.total}:{}),...datePayload},accountRequest(session));accountRequest(session);
      client.setQueryData(accountQueryKey(session,path),previous=>{
        const editedAt=new Date().toISOString();
        const states=new Map((previous?.episodes || []).map(item=>[`${item.season}:${item.episode}`,item]));
        for(const [season,episode] of episodes)states.set(`${season}:${episode}`,{season,episode,played,progress:played?100:0,...(played?{last_played:watchedTimestamp(datePayload.watched_dates?.find(row=>row.season===season && row.episode===episode)?.date || datePayload.watched_on)}:{}),source:"manual",updated_at:editedAt,sync_pending:!!data?.jellyfin_failed,synced_at:data?.jellyfin_updated?editedAt:null});
        return {...previous,item:{...previous?.item,played:series?previous?.item?.played:played,...(!series?{...(played?{last_played:watchedTimestamp(datePayload.watched_on)}:{}),source:"manual",sync_pending:!!data?.jellyfin_failed,synced_at:data?.jellyfin_updated?editedAt:null}:{}),updated_at:new Date().toISOString()},episodes:[...states.values()]};
      });
      toast.success(played?'Marked watched':'Marked unwatched');if(data?.jellyfin_failed || data?.sync_error)toast.error(data.sync_error || 'Saved locally; some Jellyfin updates failed.');
      setDateEpisodes(null);
      await Promise.allSettled([tracking.refetch(),availability.refetch(),refreshTracking(),client.invalidateQueries({predicate:query=>query.queryKey[0]==='jellyfin-library' || (query.queryKey[0]==='account' && query.queryKey[1]===session.scope && /^\/(tracking|jellyfin)(?:\/|$)/.test(query.queryKey[2] || ''))})]);
    }catch(error){toast.error(errMsg(error));}finally{setBusy(false);}
  };
  const selectedKnown=!series || videos.some(video=>video.season===ep.s && video.episode===ep.e);
  const played=current?.played ?? availability.data?.playback?.played;
  const percent=Math.min(100,Math.max(0,Number(playback?.progress) || 0)),position=Number(playback?.position_ticks),runtime=Number(playback?.runtime_ticks);
  return <div className={episodeOnly?"episode-progress space-y-2":"detail-progress mb-4 space-y-3"}><JellyfinAvailability query={availability} episodeOnly={episodeOnly}/>{availability.data?.episodes_error && availability.data?.title_state?.played!==true && <p role="status" className="text-xs text-neutral-400">Some Jellyfin episode state is unavailable. <button className="stak-button stak-button--link" onClick={()=>availability.refetch()}>Retry watched state</button></p>}<section data-testid={`${prefix}tracking-panel`} className="compact-progress">
    {series && !episodeOnly && <div className="series-completion mb-3"><p className="detail-progress-heading">{known?`${completion.completed} of ${completion.total} watched`:loadingState?'Checking watched state…':'Episode state unavailable'}</p>{known && <><ProgressIndicator label="Series completion" value={completion.percentage} aria-valuetext={`${completion.completed} of ${completion.total} episodes watched, ${completion.remaining} remaining`}/><div className="detail-progress-info" data-testid="tracking-details"><p>{Math.round(completion.percentage)}% complete · {completion.remaining} remaining</p></div></>}</div>}
    {playback && !played && !playback.played && (percent>0 || position>0) && <div className="playback-summary mb-3" data-testid="playback-progress"><div className="mb-2 flex justify-between gap-2 font-nmono text-xs"><span>{series?`S${playback.season || ep.s} E${playback.episode || ep.e}`:'Movie'} playback</span><span>{Math.round(percent)}%</span></div><ProgressIndicator label="Playback progress" value={percent}/>{position>0 && <p className="mt-2 text-xs text-neutral-400">Resume at {Math.floor(position/6e8)} min{runtime>position?` · ${Math.ceil((runtime-position)/6e8)} min remaining`:''}</p>}</div>}
    {tracking.error?<p role="alert" className="text-xs">Tracking could not load. <button className="n-chip nothing-action" onClick={()=>tracking.refetch()}>Retry</button></p>:tracking.isPending && !localOnly?<p className="text-xs text-neutral-500">Loading watched state…</p>:<div className="flex flex-wrap gap-2"><button data-testid={`${prefix}${played?'mark-unwatched-button':'mark-watched-button'}`} className="n-chip nothing-action compact-watch-action" disabled={busy || tracking.isPending || !selectedKnown} onClick={()=>played?mark(false,series?[[ep.s,ep.e]]:[]):setDateEpisodes(series?[[ep.s,ep.e]]:[])}>{busy?'Saving…':played?'✓ Watched · undo':'✓ Mark watched'}{series?` · S${String(ep.s).padStart(2,'0')} E${String(ep.e).padStart(2,'0')}`:''}</button>{series && !episodeOnly && seasonToMark!=null && <button data-testid="mark-season-watched-button" className="n-chip nothing-action compact-watch-action" disabled={busy} onClick={()=>setConfirmSeason(seasonToMark)}>Mark S{String(seasonToMark).padStart(2,'0')} watched</button>}</div>}
  </section><AlertDialog open={confirmSeason!=null} onOpenChange={open=>{if(!open)setConfirmSeason(null);}}><AlertDialogContent><AlertDialogTitle>Mark Season {confirmSeason} watched?</AlertDialogTitle><AlertDialogDescription>Mark all {videos.filter(v=>v.season===confirmSeason).length} episodes watched locally and sync to Jellyfin when connected. Individual episodes can be marked unwatched afterward.</AlertDialogDescription><AlertDialogFooter><AlertDialogCancel>Cancel</AlertDialogCancel><AlertDialogAction data-testid="confirm-season-watched" onClick={()=>{setDateEpisodes(videos.filter(v=>v.season===confirmSeason && !watched[`${v.season}:${v.episode}`]?.played).map(v=>[v.season,v.episode]));setConfirmSeason(null);}}>Mark season watched</AlertDialogAction></AlertDialogFooter></AlertDialogContent></AlertDialog><WatchedDateDialog open={dateEpisodes!==null} onOpenChange={open=>{if(!open)setDateEpisodes(null);}} meta={meta} episodes={dateEpisodes || []} busy={busy} onSave={payload=>mark(true,dateEpisodes || [],payload)}/></div>;
}
