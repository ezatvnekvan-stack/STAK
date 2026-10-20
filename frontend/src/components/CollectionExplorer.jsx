import { useApp } from '../context/AppContext';
import { releaseOrder } from '../lib/experience';
import NothingSelect from './NothingSelect';
import { useEffect, useState } from 'react';
import { useInfiniteQuery } from '@tanstack/react-query';
import { api, errMsg } from '../lib/api';
import PosterCard from './PosterCard';
import { Dialog, DialogContent, DialogTitle, DialogDescription } from './ui/dialog';
export default function CollectionExplorer({type,expanded=false}) {
  const {tracking}=useApp();
  const [order,setOrder]=useState('release'),[hideWatched,setHideWatched]=useState(false);
  const [open,setOpen]=useState(expanded),[text,setText]=useState(''),[term,setTerm]=useState(''),[selected,setSelected]=useState(null);
  useEffect(()=>{if(expanded)setOpen(true);},[expanded]);
  useEffect(()=>{const timer=setTimeout(()=>setTerm(text.trim()),350);return()=>clearTimeout(timer);},[text]);
  const query=useInfiniteQuery({queryKey:['collection-search',term],enabled:open,initialPageParam:1,staleTime:600000,retry:false,
    queryFn:async({signal,pageParam})=>{const {data}=await api.get('/discover/collections',{signal,params:{q:term,page:pageParam}});return {...data,items:data.items || (data.collections || []).map(c=>({...c,kind:'collection',poster:c.poster_path?`https://image.tmdb.org/t/p/w342${c.poster_path}`:null}))};},
    getNextPageParam:last=>last.page<last.total_pages?last.page+1:undefined});
  const catalog=useInfiniteQuery({queryKey:['collection-catalog',selected?.kind,selected?.id,type],enabled:Boolean(selected),initialPageParam:1,staleTime:600000,retry:false,
    queryFn:async({signal,pageParam})=>(await api.get('/discover/collection-items',{signal,params:{id:selected.id,kind:selected.kind,type,page:pageParam}})).data,
    getNextPageParam:last=>last.page<last.total_pages?last.page+1:undefined});
  const choices=[...new Map((query.data?.pages || []).flatMap(p=>p.items || []).map(c=>[`${c.kind}:${c.id}`,c])).values()];
  const items=[...new Map((catalog.data?.pages || []).flatMap(p=>p.items || []).map(c=>[`${c.type}:${c.id}`,c])).values()];
  const completed=items.filter(item=>tracking?.get(item.imdb_id || item.id)?.played).length;
  const ordered=order==='release'?releaseOrder(items):items;
  const visible=ordered.filter(item=>!hideWatched || !tracking?.get(item.imdb_id || item.id)?.played);
  return <section className="n-card p-3" data-testid="collection-explorer"><button type="button" className="flex min-h-11 w-full items-center justify-between text-left" aria-expanded={open} onClick={()=>setOpen(v=>!v)}><span className="n-label">Collections & universes</span><span aria-hidden="true">{open?'−':'+'}</span></button>{open && <>
    <input className="n-input my-3" aria-label="Search collections and universes" placeholder="MCU, Star Wars, Harry Potter…" value={text} onChange={e=>setText(e.target.value)}/>
    <p className="mb-3 text-[10px] text-neutral-500">Official TMDb movie collections and universe/keyword catalogs. Universe coverage depends on TMDb tagging.</p>
    {query.error && <p role="alert" className="my-3 text-xs">{errMsg(query.error)} <button className="stak-button stak-button--link" onClick={()=>query.refetch()}>Retry</button></p>}
    {query.isPending?<p role="status" className="text-xs">Finding collections…</p>:<div className="no-scrollbar flex gap-2 overflow-x-auto pb-2">{choices.map(c=><button key={`${c.kind}:${c.id}`} className="n-card w-36 shrink-0 overflow-hidden text-left" onClick={()=>setSelected(c)}>{c.poster && <img src={c.poster} alt="" loading="lazy" decoding="async" className="aspect-[2/1] w-full object-cover"/>}<span className="block p-3 text-xs">{c.name}<small className="mt-2 block text-[9px] text-neutral-500">{c.kind==='collection'?'MOVIE COLLECTION':'UNIVERSE / KEYWORD'}</small></span></button>)}{!choices.length && !query.error && <p className="text-xs text-neutral-400">No collections found. Try another name.</p>}</div>}
    {query.data?.pages.some(p=>p.errors?.length>0) && <p role="status" className="text-xs text-neutral-500">Some collection sources could not load. <button className="stak-button stak-button--link" onClick={()=>query.refetch()}>Retry</button></p>}
    {query.hasNextPage && <button className="n-chip mt-2" disabled={query.isFetchingNextPage} onClick={()=>query.fetchNextPage()}>More collections</button>}
  </>}
    <Dialog open={Boolean(selected)} onOpenChange={value=>{if(!value)setSelected(null);}}><DialogContent><DialogTitle className="pr-8 font-dot text-2xl">{selected?.name}</DialogTitle><DialogDescription>{selected?.kind==='collection'?'Movies in this official TMDb collection.':`${type==='series'?'TV shows':'Movies'} tagged with this universe/keyword on TMDb. This is not an official chronological universe listing.`}</DialogDescription><div className="flex flex-wrap items-center gap-2"><NothingSelect aria-label="Collection order" value={order} onChange={e=>setOrder(e.target.value)}><option value="release">Release order</option><option value="provider">Provider order</option></NothingSelect><label className="flex min-h-11 items-center gap-2 text-xs"><input type="checkbox" checked={hideWatched} onChange={e=>setHideWatched(e.target.checked)}/>Hide watched</label></div>{items.length>0 && <p className="font-nmono text-xs">{completed} / {items.length} {catalog.hasNextPage || selected?.kind!=='collection'?'loaded titles':'titles'} watched · {Math.round(completed/items.length*100)}%</p>}{catalog.error && <p role="alert" className="text-xs">{errMsg(catalog.error)} <button onClick={()=>catalog.refetch()}>Retry</button></p>}{catalog.isPending?<p role="status">Loading titles…</p>:<div className="search-four-grid" data-testid="collection-results">{visible.map((item,i)=><PosterCard key={`${item.type}:${item.id}`} item={item} index={i}/>)}</div>}{!catalog.isPending && !catalog.error && !items.length && <p className="text-sm text-neutral-400">No titles indexed for this media type.</p>}{items.length>0 && !visible.length && <p className="text-xs">All loaded titles are watched.</p>}{catalog.hasNextPage && <button className="n-chip" disabled={catalog.isFetchingNextPage} onClick={()=>catalog.fetchNextPage()}>More titles</button>}</DialogContent></Dialog>
  </section>;
}
