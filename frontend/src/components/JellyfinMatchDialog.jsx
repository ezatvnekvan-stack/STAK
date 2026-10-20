import { useEffect, useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { toast } from 'sonner';
import { api, errMsg } from '../lib/api';
import { accountRequest } from '../lib/accountQueries';
import { useApp } from '../context/AppContext';
import { detailsHref } from '../lib/titleIdentity';
import { rankTitles } from '../lib/searchRelevance';
import { Dialog, DialogContent, DialogTitle, DialogDescription } from './ui/dialog';
export default function JellyfinMatchDialog({item,open,onOpenChange}) {
  const {session}=useApp(),client=useQueryClient(),navigate=useNavigate();
  const [text,setText]=useState(item.SeriesName || item.Name || ''),[term,setTerm]=useState(''),[busy,setBusy]=useState(false),[error,setError]=useState('');
  const type=item.Type==='Movie'?'movie':'series';
  useEffect(()=>{const timer=setTimeout(()=>setTerm(text.trim()),350);return()=>clearTimeout(timer);},[text]);
  const query=useQuery({queryKey:['account',session.scope,'manual-match',type,term],enabled:open && term.length>1,retry:false,queryFn:async({signal})=>(await api.get('/discover',{...accountRequest(session,{signal}),params:{q:term,type}})).data});
  const choose=async title=>{
    if(busy)return;setBusy(true);setError('');
    try {
      const identity={id:title.imdb_id || title.id,type};
      await api.put('/jellyfin/match',{item_id:item.Id,identity},accountRequest(session));accountRequest(session);
      await Promise.all([client.invalidateQueries({queryKey:['account',session.scope]}),client.invalidateQueries({queryKey:['jellyfin-library']})]);
      toast.success('Title match saved on this device');onOpenChange(false);
      navigate(detailsHref({...identity,...(item.Type==='Episode'?{season:item.ParentIndexNumber,episode:item.IndexNumber}:{})}));
    }catch(failure){setError(errMsg(failure));}finally{setBusy(false);}
  };
  return <Dialog open={open} onOpenChange={onOpenChange}><DialogContent><DialogTitle className="font-dot text-2xl">Match title</DialogTitle><DialogDescription>Choose the correct {type==='movie'?'movie':'series'} for {item.SeriesName || item.Name}. This saves a local mapping for this Jellyfin server and user; it does not edit server metadata.</DialogDescription><input className="n-input" aria-label="Find matching title" value={text} onChange={e=>setText(e.target.value)}/>{(error || query.error) && <p role="alert" className="text-xs">{error || errMsg(query.error)} <button className="stak-button stak-button--link" onClick={()=>query.refetch()}>Retry search</button></p>}{query.isFetching && <p role="status">Searching metadata…</p>}<div className="space-y-2">{rankTitles(query.data?.items || [],term).slice(0,12).map(title=><button disabled={busy} key={title.id} className="n-card flex min-h-12 w-full items-center gap-3 p-3 text-left text-sm" onClick={()=>choose(title)}>{title.poster && <img src={title.poster} alt="" loading="lazy" className="h-16 w-11 rounded object-cover"/>}<span>{title.name}<small className="mt-1 block text-neutral-500">{title.releaseInfo || title.year} · {title.imdb_id || title.id}</small></span></button>)}</div>{!query.isFetching && query.data && !query.data.items?.length && <p className="text-xs">No matching titles. Try an alternative title or IMDb ID.</p>}</DialogContent></Dialog>;
}
