import MediaQuickActions from "./MediaQuickActions";
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api, errMsg } from '../lib/api';
import { toast } from 'sonner';
export default function MetadataCards({title,items}) {
 const navigate=useNavigate(),[busy,setBusy]=useState(null);
 const open=async(item)=>{setBusy(item.id);try{const direct=item.imdb_id || item.id;if(/^(tt\d+|tmdb:\d+)$/.test(String(direct)))return navigate(`/title/${item.type||'movie'}/${direct}`);const {data}=await api.get('/meta/resolve',{params:{tmdb_id:item.tmdb_id || item.id,type:item.type||'movie'}});navigate(`/title/${data.type||item.type||'movie'}/${data.imdb_id||`tmdb:${item.tmdb_id||item.id}`}`);}catch(e){toast.error(errMsg(e));}finally{setBusy(null);}};
 if(!items?.length)return null;
 return <section className="stak-media-section"><h2 className="n-h2 mb-3">{title}</h2><div className="stak-media-rail no-scrollbar flex gap-3 overflow-x-auto pb-2">{items.map((item,i)=><MediaQuickActions key={`${item.id}-${i}`} item={item}><button disabled={busy!==null} onClick={()=>open(item)} key={`${item.id}-${i}`} className="w-28 shrink-0 text-left group"><div className="aspect-[2/3] overflow-hidden rounded-xl bg-[color:var(--c-222222)]">{item.poster&&<img src={item.poster} alt="" loading="lazy" className="h-full w-full object-cover transition-transform group-hover:scale-105"/>}</div><div className="mt-2 line-clamp-2 text-xs">{busy===item.id?'Opening…':item.name}</div>{item.credit_role&&<p className="text-[10px] text-neutral-500">{item.credit_role}</p>}{item.year&&<p className="text-[10px] text-neutral-500">{item.year}</p>}</button></MediaQuickActions>)}</div></section>;
}
