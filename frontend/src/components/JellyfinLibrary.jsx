import {uniqueJellyfinTitles} from '../lib/jellyfinVersions';
import JellyfinScan from "./JellyfinScan";
import JellyfinLibrarySummary, {JellyfinStorage} from "./JellyfinLibrarySummary";
import { useEffect, useState } from 'react';
import { useInfiniteQuery, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { RefreshCw, Settings2 } from 'lucide-react';
import {CollectionSearch,CollectionLayout,CollectionSheet,CollectionChip} from './CollectionControls';
import { api, errMsg } from '../lib/api';
import JellyfinDetailsLink from './JellyfinDetailsLink';
import JellyfinImage from './JellyfinImage';
import NothingSelect from './NothingSelect';
import { REFRESH_EVENT } from './AppShell';
let connectionEpoch=0;
let libraryView={};
window.addEventListener('dmm-jellyfin-change',()=>{connectionEpoch++;libraryView={};});

function LibraryCollection({ view, compact, epoch, term, sort, filter, layout }) {
  const query=useInfiniteQuery({
    queryKey:['jellyfin-library',epoch,'items',view.Id,term,sort,filter,compact],
    initialPageParam:0, staleTime:60_000, retry:false,
    queryFn:async({pageParam,signal})=>(await api.get('/jellyfin/library-items',{signal,params:{parent_id:view.all?undefined:view.Id,start:pageParam,limit:compact?20:36,search:term,sort,filter}})).data,
    getNextPageParam:(last,pages)=>{const count=pages.reduce((n,p)=>n+(p.items?.length || 0),0);return last.items?.length && count<last.total?count:undefined;},
  });
  const items=uniqueJellyfinTitles((query.data?.pages || []).flatMap(p=>p.items || []));
  return <section className="space-y-3" aria-label={view.Name}>
    <div className="flex items-center justify-between gap-2"><h2 className="n-h2">{view.Name}</h2>{query.data && <span className="font-nmono text-[10px] text-neutral-500">{items.length} titles loaded · {query.data.pages[0]?.total ?? items.length} server items</span>}</div>
    {query.isPending && <div role="status" aria-label="Loading library" className="dot-skel h-48 rounded-xl"/>}
    {query.data?.pages.some(page=>page.offline) && <div role="status" className="n-card space-y-2 p-3 text-xs text-neutral-400"><p>Saved library · {query.data.pages.find(page=>page.offline)?.cached_at?new Date(query.data.pages.find(page=>page.offline).cached_at).toLocaleString():'last visit'}. The server could not refresh this page.</p><p>{query.data.pages.find(page=>page.offline)?.error}</p><button className="n-chip" onClick={()=>query.refetch()}>Retry live library</button></div>}
    {query.error && <div role="alert" className="n-card space-y-3 p-4"><p className="text-sm break-words">{errMsg(query.error)}</p><button className="n-chip" onClick={()=>query.refetch()}>Retry library</button></div>}
    <div data-testid={compact?undefined:'jellyfin-collection'} data-layout={layout} className={compact?'no-scrollbar flex gap-3 overflow-x-auto':layout==='list'?'collection-list':'collection-grid'}>
      {items.map(item=><JellyfinDetailsLink key={item.Id} item={item} className={compact?'w-28 shrink-0':`library-poster min-w-0 ${layout==='list'?'collection-media-row':''}`}>
        <div className="library-art relative"><JellyfinImage item={item} className="h-full w-full object-cover" width={compact?240:320}/>{item.UserData?.Played && <span className="absolute left-1.5 top-1.5 rounded-full bg-black/80 px-2 py-1 text-[10px] text-white" aria-label="Watched" style={{color:"#fff"}}>✓</span>}{!item.UserData?.Played && item.UserData?.PlayedPercentage>0 && <div className="absolute inset-x-0 bottom-0 h-1 bg-black/50"><div className="h-full bg-[color:var(--n-accent)]" style={{width:`${Math.min(100,Math.max(0,item.UserData.PlayedPercentage))}%`}}/></div>}</div>
        <div className="collection-jellyfin-info"><div className="collection-title">{item.Name}</div><div className="collection-note">{[item.ProductionYear,item.Type==='Series'?'Series':'Movie'].filter(Boolean).join(' · ')}</div>{layout==='list' && <span className="collection-note">{item.UserData?.Played?'Watched':item.UserData?.PlayedPercentage>0?`${Math.round(item.UserData.PlayedPercentage)}% watched`:'Unwatched'}</span>}</div>
      </JellyfinDetailsLink>)}
    </div>
    {!query.isPending && !query.error && !items.length && <p className="n-card p-5 text-sm text-neutral-400">No matching titles in this library.</p>}
    {!compact && query.hasNextPage && <button className="n-chip w-full" disabled={query.isFetchingNextPage} onClick={()=>query.fetchNextPage()}> {query.isFetchingNextPage?'Loading…':'Load more'}</button>}
  </section>;
}
export function JellyfinPosterGrid({compact=false}) {
  const client=useQueryClient();
  const [filtersOpen,setFiltersOpen]=useState(false),[serverOpen,setServerOpen]=useState(false),[layout,setLayout]=useState(()=>libraryView.layout || 'grid');
  const [epoch,setEpoch]=useState(()=>connectionEpoch),[selected,setSelected]=useState(()=>compact?'':libraryView.selected || '@all'),[search,setSearch]=useState(()=>compact?'':libraryView.search || ''),[term,setTerm]=useState(()=>compact?'':libraryView.search || ''),[sort,setSort]=useState(()=>compact?'name':libraryView.sort || 'name'),[filter,setFilter]=useState(()=>compact?'all':libraryView.filter || 'all');
  useEffect(()=>{if(!compact && epoch===connectionEpoch)libraryView={selected,search,sort,filter,layout};},[compact,epoch,selected,search,sort,filter,layout]);
  const connection=useQuery({queryKey:['jellyfin-library',epoch,'connection'],queryFn:async({signal})=>(await api.get('/jellyfin',{signal})).data,staleTime:60_000,retry:false});
  const libraries=useQuery({queryKey:['jellyfin-library',epoch,'views'],queryFn:async({signal})=>(await api.get('/jellyfin/libraries',{signal})).data,enabled:Boolean(connection.data?.connected),staleTime:60_000,retry:false});
  useEffect(()=>{const timer=setTimeout(()=>setTerm(search.trim()),300);return()=>clearTimeout(timer);},[search]);
  useEffect(()=>{
    const changed=()=>{client.cancelQueries({queryKey:['jellyfin-library']});client.removeQueries({queryKey:['jellyfin-library']});setEpoch(connectionEpoch);setSelected('@all');setSearch('');setTerm('');setSort('name');setFilter('all');setLayout('grid');setFiltersOpen(false);setServerOpen(false);};
    const refresh=()=>client.invalidateQueries({queryKey:['jellyfin-library']});
    window.addEventListener('dmm-jellyfin-change',changed);window.addEventListener(REFRESH_EVENT,refresh);
    return()=>{window.removeEventListener('dmm-jellyfin-change',changed);window.removeEventListener(REFRESH_EVENT,refresh);};
  },[client]);
  const refresh=()=>client.invalidateQueries({queryKey:['jellyfin-library']});
  const views=libraries.data?.items || [], allView={Id:'@all',Name:'All',all:true},active=selected==='@all'?allView:views.find(v=>v.Id===selected) || allView;
  const error=connection.error || libraries.error;
  return <div className="space-y-5" data-testid="jellyfin-library">
    {!compact && <><div className="collection-server-strip"><JellyfinLibrarySummary key={epoch} epoch={epoch} connected={Boolean(connection.data?.connected)} offline={Boolean(libraries.data?.offline)}/><button className="n-icon-btn" aria-label="Refresh Jellyfin library" onClick={refresh} disabled={connection.isFetching || libraries.isFetching}><RefreshCw size={16} className={connection.isFetching || libraries.isFetching?'animate-spin':''}/></button><button className="n-icon-btn" aria-label="Server details" aria-haspopup="dialog" onClick={()=>setServerOpen(true)}><Settings2 size={17}/></button></div>
    <CollectionSheet open={serverOpen} onOpenChange={setServerOpen} title="Server details" testId="jellyfin-server-details"><p className="collection-note">{views.length} libraries available to this user</p>{connection.data?.connected && <JellyfinStorage epoch={epoch}/>}<Link className="n-btn-ghost" to="/remote">Remote control</Link>{connection.data?.is_admin && <JellyfinScan key={epoch} onRefresh={refresh}/>}<Link className="n-btn-ghost" to="/account?section=jellyfin">Connection settings</Link></CollectionSheet></>}
    {libraries.data?.offline && <p role="status" className="text-xs text-neutral-400">Saved libraries · {new Date(libraries.data.cached_at).toLocaleString()}. {libraries.data.error} <button className="stak-button stak-button--link" onClick={refresh}>Retry connection</button></p>}
    {(connection.isPending || (libraries.isPending && connection.data?.connected)) && <div role="status" className="dot-skel h-36 rounded-xl"/>}
    {error && <div role="alert" className="n-card space-y-3 p-4"><p className="break-words text-sm text-red-400">{errMsg(error)}</p><div className="flex flex-wrap gap-2"><button className="n-chip" onClick={refresh}>Retry</button><Link className="n-chip" to="/account?section=jellyfin">Check / reconnect Jellyfin</Link></div></div>}
    {connection.data && !connection.data.connected && <div className="activity-surface"><h2 className="font-dot text-xl">Your library awaits</h2><p className="my-3 text-sm text-neutral-400">Connect your Jellyfin server to browse your personal collection.</p><Link to="/account?section=jellyfin" className="n-btn-ghost">Connect Jellyfin</Link></div>}
    {!compact && views.length>0 && <><CollectionSearch value={search} onChange={setSearch} label="Search this library" onFilters={()=>setFiltersOpen(true)} count={Number(sort!=='name')+Number(filter!=='all')}/><div className="collection-browse-bar"><div className="collection-segments" aria-label="Libraries">{[allView,...views].map(view=><button key={view.Id} aria-pressed={active?.Id===view.Id} onClick={()=>setSelected(view.Id)}>{view.Name}</button>)}</div><CollectionLayout value={layout} onChange={setLayout}/></div>
    {(filter!=='all' || sort!=='name') && <div className="collection-active-filters">{filter!=='all' && <CollectionChip onRemove={()=>setFilter('all')}>{filter==='watched'?'Watched':'Unwatched'}</CollectionChip>}{sort!=='name' && <CollectionChip onRemove={()=>setSort('name')}>{sort==='recent'?'Recently added':'Newest release'}</CollectionChip>}</div>}
    <CollectionSheet open={filtersOpen} onOpenChange={setFiltersOpen} onReset={()=>{setSort('name');setFilter('all');}} testId="jellyfin-filters"><label className="collection-field">Sort by<NothingSelect aria-label="Library sort" value={sort} onChange={e=>setSort(e.target.value)}><option value="name">Title A–Z</option><option value="recent">Recently added</option><option value="year">Newest release</option></NothingSelect></label><label className="collection-field">Watch status<NothingSelect aria-label="Watch status" value={filter} onChange={e=>setFilter(e.target.value)}><option value="all">All titles</option><option value="unwatched">Unwatched</option><option value="watched">Watched</option></NothingSelect></label></CollectionSheet></>}
    {(compact?views:connection.data?.connected && views.length?[active]:[]).map(view=><LibraryCollection key={`${epoch}:${view.Id}`} view={view} compact={compact} epoch={epoch} term={compact?'':term} sort={sort} filter={filter} layout={layout}/>)}
    {!error && libraries.data && !views.length && <p>No libraries are available to this user. Check library access in Jellyfin.</p>}
  </div>;
}
export default function JellyfinLibrary(){return <JellyfinPosterGrid/>;}
