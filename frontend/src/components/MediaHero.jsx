import {useMetadataSources} from '../lib/useMetadataSources';
import {useMetadataRevision} from '../lib/metadata/runtime';
import { useEffect, useRef, useState } from 'react';
import useEmblaCarousel from 'embla-carousel-react';
import { useMotionPolicy } from './design/MotionSystem';
import { useNavigate } from 'react-router-dom';
import { api } from '../lib/api';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { metadataOptions, useMetadataQuery } from '../lib/metadataQuery';
import { REFRESH_EVENT } from './AppShell';
import RatingBadge from './RatingBadge';

function HeroSlide({item,onOpen,onDown,onUp,onCancel}) {
  const [logoFailed,setLogoFailed]=useState(false);
  const {data:info={}}=useMetadataQuery('/meta/title-logo',{imdb_id:item.imdb_id || item.id,type:item.type});
  const {data:certificate}=useMetadataQuery('/meta/certification',{imdb_id:item.imdb_id || item.id,type:item.type});
  const {settings}=useMetadataSources();
  const imdb=info.imdb_id || item.imdb_id;
  const {data:ratingData}=useMetadataQuery(`/ratings/${imdb}`,{}, {enabled:settings.ratings && settings.imdbOnLists && /^tt\d+$/.test(imdb || '')});
  const rating=ratingData?.imdb,cert=certificate?.certification;
  const genres=(info.genres || item.genres || []).map(g=>typeof g==='string'?g:g.name).filter(Boolean).slice(0,2);
  return <><img src={item.background || item.poster} alt="" draggable="false" decoding="async" fetchpriority="high" className="absolute inset-0 h-full w-full object-cover"/><div className="home-hero-shade absolute inset-0"/>
    <button className="hero-open stak-hero-content relative flex h-full w-full flex-col items-start justify-end p-4 pb-10 text-left" aria-label={`Open ${item.name}`} onClick={onOpen} onPointerDown={onDown} onPointerUp={onUp} onPointerCancel={onCancel}>
      {info.logo && !logoFailed ? <img src={info.logo} decoding="async" draggable="false" alt={item.name} className="mb-3 max-h-14 max-w-[74%] object-contain object-left" onError={()=>setLogoFailed(true)}/> : <span className="mb-3 max-w-[92%] font-dot text-3xl text-white">{item.name}</span>}
      {(genres.length>0 || cert) && <span className="mb-2 flex flex-wrap items-center gap-2 text-[10px] text-white/80">{genres.join(' · ')}{cert && <span className="hero-certificate">{cert}</span>}</span>}
      <span className="flex items-center gap-2 text-xs text-white"><span>{item.year}</span>{rating ? <RatingBadge source="imdb" value={rating} compact/>:<RatingBadge source="tmdb" value={item.tmdbRating} compact/>}</span>
    </button></>;
}
function FeaturedSlide({item,active,adjacent,...handlers}) {
  const content=active?<HeroSlide item={item} {...handlers}/>:<div className="hero-preview">{adjacent && <img src={item.background || item.poster} alt="" decoding="async" draggable="false" className="absolute inset-0 h-full w-full object-cover"/>}<div className="home-hero-shade absolute inset-0"/><span className="absolute bottom-16 left-5 font-dot text-2xl">{item.name}</span></div>;
  return <div className="hero-slide" aria-hidden={!active}>{content}</div>;
}
export default function MediaHero() {
  const client=useQueryClient(),revision=useMetadataRevision(),{settings}=useMetadataSources();
  const navigate=useNavigate(),{reduced:minimal}=useMotionPolicy();
  const [index,setIndex]=useState(0);
  const touch=useRef(null),ignoreClickUntil=useRef(0),viewportFocused=useRef(false);
  const [viewport,carousel]=useEmblaCarousel({loop:true,align:'start',duration:minimal?0:25});
  useEffect(()=>{if(!carousel)return undefined;const selected=()=>setIndex(carousel.selectedScrollSnap());selected();carousel.on('select',selected);carousel.on('reInit',selected);return()=>{carousel.off('select',selected);carousel.off('reInit',selected);};},[carousel]);
  const {data:items=[]}=useQuery({queryKey:['metadata','hero',revision],staleTime:600000,gcTime:1800000,retry:false,queryFn:async()=>{
    const results=await Promise.allSettled(['movie','series'].map(type=>api.get('/discover/tmdb',{params:{type,category:'trending',period:'week',recent_only:true}})));
    const lists=results.map(r=>r.status==='fulfilled'?r.value.data.items || []:[]);
    return Array.from({length:3},(_,i)=>lists.map(list=>list[i])).flat().filter(Boolean);
  }});
  useEffect(()=>{const refresh=()=>client.invalidateQueries({queryKey:['metadata','hero',revision]});window.addEventListener(REFRESH_EVENT,refresh);return()=>window.removeEventListener(REFRESH_EVENT,refresh);},[client,revision]);
  // Warm just the adjacent slide, not every poster in the catalog. Respect data saver.
  useEffect(()=>{
    if(items.length<2 || navigator.connection?.saveData)return undefined;
    const next=items[(index+1)%items.length];
    const timer=setTimeout(()=>{
      const image=new Image();image.decoding='async';image.src=next.background || next.poster || '';
      client.prefetchQuery(metadataOptions('/meta/certification',{imdb_id:next.imdb_id || next.id,type:next.type}));
      client.fetchQuery(metadataOptions('/meta/title-logo',{imdb_id:next.imdb_id || next.id,type:next.type})).then(data=>{if(data?.logo){const logo=new Image();logo.decoding='async';logo.src=data.logo;}const imdb=data?.imdb_id || next.imdb_id;if(settings.ratings && settings.imdbOnLists && /^tt\d+$/.test(imdb || ''))client.prefetchQuery(metadataOptions(`/ratings/${imdb}`));}).catch(()=>{});
    },350);
    return()=>clearTimeout(timer);
  },[items,index,client,settings.ratings,settings.imdbOnLists]);
  useEffect(()=>{if(items.length<2 || minimal || !carousel)return undefined;const timer=setInterval(()=>{if(document.visibilityState!=='hidden'&&!touch.current && !viewportFocused.current)carousel.scrollNext();},9000);return()=>clearInterval(timer);},[index,items.length,minimal,carousel]);
  const item=items[index%Math.max(1,items.length)];
  if(!item)return null;
  const select=next=>carousel?.scrollTo((next+items.length)%items.length,Boolean(minimal));
  const up=event=>{const start=touch.current;touch.current=null;if(start && Math.hypot(event.clientX-start.x,event.clientY-start.y)>10)ignoreClickUntil.current=Date.now()+500;};
  return <section data-testid="media-hero" data-theme="dark" className="compact-home-hero animated-home-hero section-gap" aria-label="Featured titles" aria-roledescription="carousel" onFocusCapture={()=>{viewportFocused.current=true;}} onBlurCapture={event=>{if(!event.currentTarget.contains(event.relatedTarget))viewportFocused.current=false;}}>
    <div ref={viewport} className="hero-viewport" onKeyDown={event=>{if(['ArrowLeft','ArrowRight'].includes(event.key)){event.preventDefault();select(index+(event.key==='ArrowRight'?1:-1));}}}>
      <div className="hero-container">{items.map((entry,i)=><FeaturedSlide key={`${entry.type}:${entry.id}`} item={entry} active={i===index} adjacent={Math.abs(i-index)===1 || Math.abs(i-index)===items.length-1} onOpen={()=>{if(Date.now()>=ignoreClickUntil.current)navigate(`/title/${entry.type}/${entry.imdb_id || entry.id}`);}} onDown={event=>{touch.current={x:event.clientX,y:event.clientY};}} onUp={up} onCancel={()=>{touch.current=null;}}/>)}</div>
    </div>
    {items.length>1 && <div className="hero-pagination" aria-label="Featured title selection">{items.map((entry,i)=><button type="button" key={`${entry.type}:${entry.id}`} aria-label={`Show ${entry.name}`} aria-pressed={i===index} onClick={()=>select(i)}><span className={`hero-dot ${i===index?'is-active':''}`}/></button>)}<span className="hero-count" aria-hidden="true">{String(index+1).padStart(2,'0')} / {String(items.length).padStart(2,'0')}</span></div>}
  </section>;
}
