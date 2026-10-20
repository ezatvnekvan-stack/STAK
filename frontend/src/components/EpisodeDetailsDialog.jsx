import {useMetadataSources} from '../lib/useMetadataSources';
import {useState} from 'react';
import {useMetadataQuery} from '../lib/metadataQuery';
import RatingBadge from './RatingBadge';
import TitleProgress from './TitleProgress';
import {Button} from './StakUI';
import {Dialog,DialogContent,DialogTitle,DialogDescription} from './ui/dialog';
export default function EpisodeDetailsDialog({video,titleId,titleName,meta,onClose,onAdd}){
  const {settings}=useMetadataSources();
  const query=useMetadataQuery('/meta/episode',{imdb_id:titleId,season:video.season,episode:video.episode});
  const [expanded,setExpanded]=useState(false);
  const name=query.data?.name || video.name || video.title || `Episode ${video.episode}`,overview=query.data?.overview || video.overview || video.description;
  const score=settings.ratings?query.data?.imdbRating:null,rating=Number(score)>0 && Number(score)<=10?score:null,thumbnail=query.data?.thumbnail || video.thumbnail || video.image;
  const episodeMeta=meta || {id:titleId,imdb_id:titleId,type:'series',name:titleName,videos:[video]};
  return <Dialog open onOpenChange={open=>{if(!open)onClose();}}><DialogContent className="episode-dialog" data-testid="episode-details-dialog">
    <div className="episode-dialog-heading">{thumbnail && <img src={thumbnail} alt="" className="episode-dialog-thumb" decoding="async"/>}<div className="min-w-0"><DialogTitle>{name}</DialogTitle><DialogDescription>{titleName} · S{video.season} E{video.episode}{query.data?.released || video.released?` · ${String(query.data?.released || video.released).slice(0,10)}`:''}</DialogDescription></div></div>
    <div>{rating?<RatingBadge source="imdb" value={rating} compact/>:<span className="font-nmono text-[10px] text-neutral-500">{query.isFetching?'Checking IMDb episode rating…':'IMDb episode rating unavailable'}</span>}</div>
    <div className="episode-dialog-body"><div><p className={`text-xs leading-relaxed text-neutral-300 ${expanded?'':'line-clamp-3'}`}>{overview || (query.isPending?'Loading episode synopsis…':'No episode synopsis is available from the configured providers.')}</p>{overview?.length>180 && <Button variant="subtle" size="sm" aria-expanded={expanded} onClick={()=>setExpanded(value=>!value)}>{expanded?'Less synopsis':'Full synopsis'}</Button>}</div>
    {(query.error || query.data?.partial) && <p role="status" className="text-xs text-neutral-500">Some episode metadata could not load. <Button variant="outlined" onClick={()=>query.refetch()}>Retry metadata</Button></p>}
    <TitleProgress meta={episodeMeta} ep={{s:video.season,e:video.episode}} episodeOnly/>
    </div><footer className="episode-dialog-footer"><Button data-testid="episode-add-to-library" className="w-full" disabled={!/^tt\d+$/.test(titleId)} onClick={()=>onAdd(video)}>Find episode sources</Button>
    <p className="text-[10px] text-neutral-500">Adds a selected cached release to TorBox. Jellyfin requires your server import workflow.</p></footer>
  </DialogContent></Dialog>;
}
