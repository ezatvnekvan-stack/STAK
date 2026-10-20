import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { api, errMsg } from '../lib/api';
export default function JellyfinDiagnostics() {
  const [open,setOpen]=useState(false);
  const query=useQuery({queryKey:['jellyfin-library','diagnostics'],enabled:open,retry:false,staleTime:0,queryFn:async({signal})=>(await api.get('/jellyfin/diagnostics',{signal})).data});
  return <details className="n-card p-3" onToggle={event=>setOpen(event.currentTarget.open)}><summary className="n-label min-h-7 cursor-pointer">Connection diagnostics</summary>{open && <div className="mt-3 space-y-2 text-xs">{query.isFetching && <p role="status">Checking server, session and library access…</p>}{query.error && <p role="alert">{errMsg(query.error)}</p>}{query.data && <><p>{query.data.transport} · {query.data.elapsed_ms} ms</p>{(query.data.checks || []).map(check=><p key={check.name} className={check.ok?'text-emerald-500':'text-red-400'}>{check.ok?'✓':'!'} {check.name}{check.error?` · ${check.error}`:''}</p>)}</>}<button className="n-chip" disabled={query.isFetching} onClick={()=>query.refetch()}>Check again</button></div>}</details>;
}
