import NothingSelect from '../components/NothingSelect';
import {useMemo,useState,useEffect} from 'react';
import {RefreshCw} from 'lucide-react';
import {IS_CAPACITOR_OFFLINE} from '../lib/api';
import {useApp} from '../context/AppContext';
import PosterCard from '../components/PosterCard';
import {Empty,NeedKey,PageTitle} from '../components/Bits';
import {useAvailability} from '../lib/useAvailability';
import {useT} from '../lib/i18n';
import {PosterGridSkeleton} from '../components/Skeletons';
import {REFRESH_EVENT} from '../components/AppShell';
import {CollectionSearch,CollectionLayout,CollectionSheet,CollectionChip} from '../components/CollectionControls';
import {Switch} from '../components/ui/switch';
import {useExperience} from '../lib/useExperience';
import {useCollectionSearch} from '../lib/useCollectionSearch';
import {watchlistItems} from '../lib/collectionModel';

const TABS=[['all','All'],['movie','Movies'],['series','Series']];
export default function WatchlistPage() {
  const t=useT();
  const {key,session,watchlist,tracking,watchLoading,watchError,refreshWatchlist}=useApp();
  const [prefs,setPrefs]=useExperience('watchlist',{});
  const update=patch=>setPrefs(previous=>({...previous,...patch}));
  const [search,setSearch]=useCollectionSearch('watchlist',session?.scope||'local');
  const [filtersOpen,setFiltersOpen]=useState(false),[tick,setTick]=useState(0),[refreshing,setRefreshing]=useState(false);
  // Availability is queried only when requested, not for every saved title on entry.
  const [avail,resetAvail]=useAvailability(watchlist,!!key && prefs.cachedOnly,tick);
  const refresh=async()=>{setRefreshing(true);try{await refreshWatchlist();resetAvail();setTick(n=>n+1);}finally{setRefreshing(false);}};
  useEffect(()=>{const run=()=>refresh().catch(()=>{});window.addEventListener(REFRESH_EVENT,run);return()=>window.removeEventListener(REFRESH_EVENT,run);});
  const shown=useMemo(()=>watchlistItems(watchlist,tracking,avail,{...prefs,cachedOnly:!!key && prefs.cachedOnly,search}),[watchlist,tracking,avail,prefs,key,search]);
  const reset=()=>{update({sort:'added',hideWatched:false,cachedOnly:false});setSearch('');};
  const checking=key && prefs.cachedOnly && watchlist.some(item=>avail[item.imdb_id]===undefined);
  const unknown=key && prefs.cachedOnly && watchlist.some(item=>avail[item.imdb_id]===-1);
  if(!key && !IS_CAPACITOR_OFFLINE)return <><PageTitle title="Watchlist" testId="watchlist-title"/><NeedKey text="Your watchlist is tied to your TorBox key so it syncs across devices."/></>;
  return <div className="stak-screen stak-screen-watchlist collection-page">
    <PageTitle title={t('Watchlist')} testId="watchlist-title" right={<button data-testid="watchlist-refresh-availability" aria-label="Refresh watchlist" disabled={refreshing} onClick={()=>refresh().catch(()=>{})} className="n-icon-btn"><RefreshCw size={17} className={refreshing?'animate-spin':''}/></button>}/>
    <p className="collection-subtitle">{watchlist.length} saved {watchlist.length===1?'title':'titles'}</p>
    <CollectionSearch value={search} onChange={setSearch} label="Search watchlist" testId="watchlist-search" onFilters={()=>setFiltersOpen(true)} count={Number(prefs.hideWatched)+Number(prefs.cachedOnly)+Number(prefs.sort!=='added')}/>
    <div className="collection-browse-bar"><div className="collection-segments" aria-label="Media type">{TABS.map(([id,label])=><button key={id} data-testid={`watchlist-tab-${id}`} aria-pressed={prefs.tab===id} onClick={()=>update({tab:id})}>{t(label)}</button>)}</div><CollectionLayout value={prefs.layout} onChange={layout=>update({layout})}/></div>
    {(prefs.hideWatched || (key && prefs.cachedOnly)) && <div className="collection-active-filters">{prefs.hideWatched && <CollectionChip onRemove={()=>update({hideWatched:false})}>Unwatched</CollectionChip>}{key && prefs.cachedOnly && <CollectionChip onRemove={()=>update({cachedOnly:false})}>Cached sources</CollectionChip>}</div>}
    <CollectionSheet open={filtersOpen} onOpenChange={setFiltersOpen} onReset={reset} testId="watchlist-filters">
      <label className="collection-field">Sort by<NothingSelect aria-label="Watchlist sort" data-testid="watchlist-sort-select" value={prefs.sort} onChange={e=>update({sort:e.target.value})}><option value="added">Date added</option><option value="year">Year</option><option value="rating">IMDb rating</option><option value="name">Name</option></NothingSelect></label>
      <label className="collection-toggle"><span>Hide watched</span><Switch data-testid="watchlist-hide-watched" checked={prefs.hideWatched} onCheckedChange={hideWatched=>update({hideWatched})}/></label>
      {key && <><label className="collection-toggle"><span>Cached sources only</span><Switch data-testid="watchlist-cached-only" checked={prefs.cachedOnly} onCheckedChange={cachedOnly=>update({cachedOnly})}/></label><p className="collection-note">Checks supported sources. Series checks sample season 1, episode 1; they do not confirm the whole series is cached.</p></>}
    </CollectionSheet>
    {checking && <p role="status" className="collection-note">Checking cached sources… Results appear as checks finish.</p>}
    {unknown && <p role="status" className="collection-note">Some cache checks failed. Unknown titles are excluded by this filter. <button className="stak-button stak-button--link" onClick={resetAvail}>Retry checks</button></p>}
    {watchLoading && !watchlist.length?<PosterGridSkeleton className="collection-grid"/>:watchError?<Empty title={t('Error')} text={watchError} testId="watchlist-error" actionLabel={t('Retry')} onAction={()=>refreshWatchlist().catch(()=>{})}/>:!shown.length?<Empty title={watchlist.length?'No matching titles':'Your next watch starts here'} text={watchlist.length?'Try another search or clear your filters.':'Save a movie or series from Discover to keep it here.'} testId="watchlist-empty" {...(watchlist.length?{actionLabel:'Clear filters',onAction:()=>{setSearch('');update({tab:'all',sort:'added',hideWatched:false,cachedOnly:false});}}:{to:'/discover',actionLabel:'Browse Discover'})}/>:<>
      <div className="collection-results-meta"><span>{shown.length} {shown.length===1?'title':'titles'}</span><span>{({added:'Date added',year:'Newest year',rating:'IMDb rating',name:'Title A–Z'})[prefs.sort]}</span></div>
      <div data-testid="watchlist-grid" data-layout={prefs.layout} className={prefs.layout==='list'?'collection-list':'collection-grid'}>{shown.map((w,i)=><PosterCard key={w.imdb_id} item={{...w,id:w.imdb_id,releaseInfo:w.year}} index={i} layout={prefs.layout} hideWatchlistMarker/>)}</div>
    </>}
  </div>;
}
