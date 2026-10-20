import { compactCrew } from '../lib/titlePresentation';
import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { api, errMsg } from '../lib/api';
import MetadataCards from './MetadataCards';
import { Dialog, DialogContent, DialogTitle, DialogDescription } from './ui/dialog';
import GestureRail from './design/GestureRail';

export default function DetailExtras({imdbId,type,meta}) {
  const query = useQuery({queryKey:['title-extras',type,imdbId],queryFn:async({signal})=>(await api.get('/meta/extras',{signal,params:{imdb_id:imdbId,type}})).data || {},staleTime:600000,retry:false});
  const data = query.data;
  const [image,setImage] = useState(null),[allCredits,setAllCredits]=useState(false);
  const images = data?.images?.length ? data.images : [...new Set([meta.background,meta.poster].filter(Boolean))].map(url=>({thumb:url,full:url}));
  const allCrew=compactCrew(data?.crew,Infinity),crew=allCredits?allCrew:allCrew.slice(0,4);
  return <>
    {crew.length > 0 && <section className="stak-credits"><h2 className="n-h2 mb-3">Behind the Camera</h2><div className="crew-grid">{crew.map(p=><Link className="n-card min-w-0 p-3" key={`${p.id}-${p.job}`} to={`/person/${p.id}`}><p className="truncate text-sm">{p.name}</p><p className="mt-1 text-xs text-neutral-500">{p.job.split(' · ')[0]}</p>{p.jobs.size>1 && <p className="crew-other-roles">{[...p.jobs].slice(1).join(' · ')}</p>}</Link>)}</div>{allCrew.length>4 && <button className="stak-button stak-button--link credits-expand" aria-expanded={allCredits} onClick={()=>setAllCredits(v=>!v)}>{allCredits?'Fewer credits':`All ${allCrew.length} people`}</button>}</section>}
    {images.length > 0 && <section className="stak-credits"><h2 className="n-h2 mb-3">Gallery</h2><GestureRail label="Artwork gallery" className="artwork-gallery no-scrollbar">{images.map((picture,i)=><button type="button" className="shrink-0 overflow-hidden rounded-xl" key={picture.full} aria-label={`View artwork ${i+1} for ${meta.name}`} onClick={()=>setImage(picture.full)}><img src={picture.thumb} alt="" loading="lazy" decoding="async" className="aspect-video h-20 object-cover"/></button>)}</GestureRail></section>}
    {data?.cast?.length > 0 && <section className="stak-credits"><h2 className="n-h2 mb-3">Cast</h2><div className="no-scrollbar flex gap-3 overflow-x-auto pb-2">{data.cast.map(p=><Link className="cast-person w-20 shrink-0" key={p.id} to={`/person/${p.id}`}>{p.photo ? <img src={p.photo} alt="" className="aspect-[3/4] w-full rounded-xl object-cover" loading="lazy"/> : <div className="aspect-[3/4] rounded-xl bg-[color:var(--c-222222)]"/>}<p className="mt-2 text-xs">{p.name}</p><p className="text-[10px] text-neutral-500">{p.character}</p></Link>)}</div></section>}
    <MetadataCards title="Collection" items={data?.collection}/>
    <MetadataCards title="Related titles" items={data?.similar}/>
    {query.error && <p role="status" className="mb-4 text-xs text-neutral-500">Additional artwork and credits unavailable: {errMsg(query.error)} <button className="stak-button stak-button--link" onClick={()=>query.refetch()}>Retry</button></p>}
    <Dialog open={!!image} onOpenChange={open=>{if(!open)setImage(null);}}><DialogContent className="artwork-viewer"><DialogTitle className="sr-only">{meta.name} artwork</DialogTitle><DialogDescription className="sr-only">Fullscreen artwork. Close to return to Details.</DialogDescription>{image && <img alt={`${meta.name} artwork`} src={image} className="max-h-[85dvh] w-full object-contain"/>}</DialogContent></Dialog>
  </>;
}
