import {useEffect,useState} from 'react';
import {useQuery} from '@tanstack/react-query';
import {Link} from 'react-router-dom';
import {api,errMsg} from '../lib/api';
import {FilterDisclosure} from './StakUI';
export default function DiscoverDirectors(){
  const [search,setSearch]=useState(''),[term,setTerm]=useState(''),[open,setOpen]=useState(false);
  useEffect(()=>{const timer=setTimeout(()=>setTerm(search.trim()),350);return()=>clearTimeout(timer);},[search]);
  const query=useQuery({queryKey:['discovery-directors',term],enabled:open,staleTime:600000,retry:false,queryFn:async({signal})=>(await api.get('/discover/directors',{signal,params:{q:term}})).data});
  return <FilterDisclosure title="Directors" onToggle={e=>setOpen(e.currentTarget.open)}>{open && <div className="n-card mt-3 p-3"><input className="n-input" aria-label="Search directors" placeholder="Director name" value={search} onChange={e=>setSearch(e.target.value)}/>{query.isPending?<p role="status" className="mt-3 text-xs">Finding directors…</p>:query.error?<p role="alert" className="mt-3 text-xs">{errMsg(query.error)} <button className="n-chip nothing-action" onClick={()=>query.refetch()}>Retry</button></p>:<div className="no-scrollbar mt-3 flex gap-3 overflow-x-auto">{(query.data?.items || []).map(person=><Link className="w-20 shrink-0 text-xs" key={person.id} to={`/person/${person.id}`}>{person.photo && <img alt="" loading="lazy" src={person.photo} className="mb-2 aspect-square w-full rounded-full object-cover"/>}{person.name}</Link>)}{!query.data?.items?.length && <p className="text-xs text-neutral-500">No directors found. Try another name.</p>}</div>}</div>}</FilterDisclosure>;
}
