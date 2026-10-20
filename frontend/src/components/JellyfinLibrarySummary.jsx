import {useQuery} from '@tanstack/react-query';
import {api,errMsg} from '../lib/api';
import JellyfinLogo from './JellyfinLogo';
export default function JellyfinLibrarySummary({epoch,connected,offline=false}) {
  const counts=useQuery({queryKey:['jellyfin-library',epoch,'counts'],queryFn:async({signal})=>(await api.get('/jellyfin/counts',{signal})).data,enabled:connected,staleTime:60000,retry:false});
  return <div data-testid="jellyfin-library-summary" className="collection-server-summary"><JellyfinLogo size={24} label="Jellyfin"/><div><div className="collection-server-name">Jellyfin <span className="collection-connection" data-connected={connected && !offline && !!counts.data && !counts.error}>{offline?'Saved libraries':connected?counts.error?'Connection issue':counts.data?'Connected':'Configured':'Not connected'}</span></div><p className="collection-note">{connected?counts.data?`${counts.data.movies ?? '—'} movies · ${counts.data.series ?? '—'} series`:counts.error?'Counts unavailable':'Loading counts…':'Your personal collection'}</p>{counts.error && <button className="stak-button stak-button--link" onClick={()=>counts.refetch()}>Retry counts</button>}</div></div>;
}
export function JellyfinStorage({epoch}) {
  const query=useQuery({queryKey:['jellyfin-library',epoch,'storage'],queryFn:async({signal})=>(await api.get('/jellyfin/storage',{signal})).data,staleTime:15*60000,gcTime:30*60000,retry:false});
  const bytes=Number(query.data?.bytes);
  return <div className="collection-server-storage"><span className="n-label">Media files</span><p>{query.data && Number.isFinite(bytes)?`${(bytes/1e9).toLocaleString(undefined,{maximumFractionDigits:1})} GB${query.data.complete?'':' · partial total'}`:query.isPending?'Loading storage…':'Storage unavailable'}</p>{query.error && <p role="status" className="collection-note">{errMsg(query.error)} <button className="stak-button stak-button--link" onClick={()=>query.refetch()}>Retry storage</button></p>}</div>;
}
