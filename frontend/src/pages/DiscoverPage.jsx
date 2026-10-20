import {useMetadataRevision} from '../lib/metadata/runtime';
import {useMemo,useState} from 'react';
import {useInfiniteQuery,useQuery} from '@tanstack/react-query';
import {useSearchParams} from 'react-router-dom';
import {useApp} from '../context/AppContext';
import {useExperience} from '../lib/useExperience';
import {filterKnownTitles} from '../lib/experience';
import {streamingServices} from '../lib/streamingServices';
import {discoveryCategory,discoveryChannels,discoveryFilterChips,emptyDiscoveryFilters,selectedProvider} from '../lib/discovery';
import {api,errMsg} from '../lib/api';
import {readStored} from '../lib/storage';
import SearchPage from './SearchPage';
import PosterCard from '../components/PosterCard';
import NothingSelect from '../components/NothingSelect';
import {SlidersHorizontal} from 'lucide-react';
import {Sheet,SheetContent,SheetHeader,SheetTitle,SheetDescription} from '../components/ui/sheet';
import DiscoveryServices from '../components/DiscoveryServices';
import {discoverySorts} from '../lib/discoveryPresentation';
import {PosterGridSkeleton} from '../components/Skeletons';
import {Button,Chip,SegmentedControl} from '../components/StakUI';
import DiscoverDirectors from '../components/DiscoverDirectors';
import {AnimatedSelection} from '../components/design/MotionSystem';
import DiscoveryFiltersPanel from '../components/DiscoveryFiltersPanel';

function BrowseResults({type,filters,hideWatched,hideLibrary,category,label,onReset}){
  const {tracking,catalog}=useApp(),metadataRevision=useMetadataRevision();
  const query=useInfiniteQuery({queryKey:['unified-discovery',metadataRevision,type,category,filters],initialPageParam:1,staleTime:300000,retry:false,refetchOnWindowFocus:false,
    queryFn:async({signal,pageParam})=>(await api.get('/discover/tmdb',{signal,params:{type,category,...filters,page:pageParam}})).data,
    getNextPageParam:last=>Number(last.page)<Math.min(500,Number(last.total_pages))?Number(last.page)+1:undefined});
  const items=filterKnownTitles([...new Map((query.data?.pages || []).flatMap(page=>page.items || []).map(item=>[`${item.type}:${item.id}`,item])).values()],tracking,catalog,{hideWatched,hideLibrary});
  return <section className="discovery-results space-y-3" aria-label="Discover results" aria-busy={query.isFetching}><div className="discovery-results-heading flex items-center justify-between gap-3"><h2 className="sr-only">{label}</h2>{query.isFetching && !query.isPending && <span role="status" className="text-xs text-neutral-500">Updating…</span>}</div>{query.error && <p role="alert" className="text-xs text-neutral-400">{errMsg(query.error)} <Button variant="outlined" onClick={()=>query.refetch()}>Retry</Button></p>}{query.data?.pages.some(page=>page.imdb_filter) && <p className="text-[10px] text-neutral-500">IMDb ratings verified for loaded results. Unknown ratings are excluded.</p>}{query.data?.pages.some(page=>page.rating_errors>0) && <p role="status" className="text-xs text-neutral-400">Some ratings could not be checked. <Button variant="outlined" onClick={()=>query.refetch()}>Retry ratings</Button></p>}{query.isPending?<PosterGridSkeleton count={9} className="service-catalog-grid"/>:<div className="service-catalog-grid" data-testid="discover-results-catalog">{items.map((item,i)=><PosterCard key={`${item.type}:${item.id}`} item={item} index={i}/>)}</div>}{!query.isPending && !query.error && !items.length && <div className="discovery-empty"><h3>No matches yet</h3><p>No matching titles in the loaded results. Try fewer filters or load the next page.</p><Button variant="outlined" onClick={onReset}>Clear filters</Button></div>}{query.hasNextPage && <Button variant="outlined" className="w-full" disabled={query.isFetching} onClick={()=>query.fetchNextPage()}>Load more</Button>}{query.data?.pages.find(page=>page.filter_scope)?.filter_scope && <p className="text-[10px] text-neutral-500">{query.data.pages.find(page=>page.filter_scope).filter_scope}</p>}</section>;
}
export default function DiscoverPage(){
  const {catalog,catalogError,refreshCatalog}=useApp(),metadataRevision=useMetadataRevision();
  const [saved,setSaved]=useExperience('discovery',{filters:emptyDiscoveryFilters,category:'popular',region:readStored('dmm-discover-region','US')});
  const {type,region,filters,hideWatched,hideLibrary}=saved,category=discoveryCategory(saved.category);
  const update=patch=>setSaved(previous=>({...previous,...patch})),change=(name,value)=>setSaved(previous=>({...previous,filters:{...previous.filters,[name]:value}}));
  const providers=useQuery({queryKey:['streaming-providers',metadataRevision,type,region],staleTime:1800000,retry:false,queryFn:async({signal})=>(await api.get('/discover/providers',{signal,params:{type,region}})).data});
  const genres=useQuery({queryKey:['discover-genres',metadataRevision],staleTime:3600000,retry:false,queryFn:async({signal})=>(await api.get('/meta/genres',{signal})).data});
  const [resultsContainer,setResultsContainer]=useState(null),[filtersOpen,setFiltersOpen]=useState(false),[draft,setDraft]=useState(null);
  const [params]=useSearchParams(),searching=!!params.get('q')?.trim();
  const services=streamingServices(providers.data?.providers);
  const selected=selectedProvider(filters.provider,services,providers.data?.providers),providerIds=selected?.providerIds || '';
  const activeFilters=useMemo(()=>({...filters,region,provider:providerIds}),[filters,region,providerIds]);
  const searchFilters=useMemo(()=>({...activeFilters,hideWatched,hideLibrary,blocked:!!(filters.provider && !selected?.available) || !!(filters.year_from && filters.year_to && Number(filters.year_from)>Number(filters.year_to))}),[activeFilters,hideWatched,hideLibrary,filters.provider,selected?.available,filters.year_from,filters.year_to]);
  const genreList=genres.data?.[type] || [],chips=discoveryFilterChips({filters,selected,hideWatched,hideLibrary},genreList);
  const reset=()=>{setDraft(null);update({filters:{...emptyDiscoveryFilters},hideWatched:false,hideLibrary:false,category:'popular'});};
  const remove=name=>name==='year'?setSaved(previous=>({...previous,filters:{...previous.filters,year_from:'',year_to:''}})):['hideWatched','hideLibrary'].includes(name)?update({[name]:false}):change(name,'');
  const openFilters=()=>{setDraft(previous=>previous || {filters:{...filters},hideWatched,hideLibrary});setFiltersOpen(true);};
  const draftValue=draft || {filters,hideWatched,hideLibrary};
  const invalidYears=draftValue.filters.year_from && draftValue.filters.year_to && Number(draftValue.filters.year_from)>Number(draftValue.filters.year_to);
  const invalidYearBounds=[draftValue.filters.year_from,draftValue.filters.year_to].some(value=>value!=='' && (!/^\d{4}$/.test(String(value)) || Number(value)<1880 || Number(value)>2100));
  const applyDraft=()=>{update(draftValue);setFiltersOpen(false);setDraft(null);};
  const channel=discoveryChannels.find(item=>item.value===category);
  return <div data-testid="discover-page" className="discovery-page discovery-refresh">
    <header className="discovery-heading"><div><h1 className="stak-page-title font-dot">Discover</h1><p className="discovery-subtitle">Find your next watch</p></div><Button variant="outlined" data-testid="open-filters-button" aria-haspopup="dialog" aria-expanded={filtersOpen} onClick={openFilters}><SlidersHorizontal size={16} aria-hidden="true"/>Filters{chips.length>0 && <span className="discovery-filter-count">{chips.length}</span>}</Button></header>
    <SearchPage embedded hideFilters mediaType={type} discoveryFilters={searchFilters} onFiltersRequest={openFilters} resultsContainer={resultsContainer} onResetFilters={reset}/>
    <div className="discover-browse-type" hidden={searching}><SegmentedControl label="Discover media type" value={type} options={[{value:'movie',label:'Movies'},{value:'series',label:'Series'}]} onChange={value=>{setDraft(null);setSaved(previous=>({...previous,type:value,filters:{...previous.filters,genre:'',certification:''}}));}}/></div>
    <Sheet open={filtersOpen} onOpenChange={setFiltersOpen}><SheetContent side="bottom" className="discovery-sheet" data-testid="discovery-filters-sheet">
      <SheetHeader className="discovery-sheet-header"><SheetTitle className="font-dot">Filters</SheetTitle><SheetDescription className="sr-only">Genre, release, rating and library preferences.</SheetDescription></SheetHeader>
      <div className="discovery-sheet-body"><DiscoveryFiltersPanel filters={draftValue.filters} genreList={genreList} type={type} hideWatched={draftValue.hideWatched} hideLibrary={draftValue.hideLibrary} change={(name,value)=>setDraft(previous=>({...previous,filters:{...previous.filters,[name]:value}}))} update={patch=>setDraft(previous=>({...previous,...patch}))} setSaved={setDraft}/>{genres.error && <p role="alert" className="discovery-help">{errMsg(genres.error)} <Button variant="outlined" onClick={()=>genres.refetch()}>Retry genres</Button></p>}{(invalidYears || invalidYearBounds) && <p role="alert">{invalidYearBounds?'Use years between 1880 and 2100.':'Choose a start year before the end year.'}</p>}</div>
      <div className="discovery-sheet-footer"><Button variant="outlined" onClick={()=>setDraft({filters:{...emptyDiscoveryFilters},hideWatched:false,hideLibrary:false})}>Reset filters</Button><Button disabled={Boolean(invalidYears || invalidYearBounds)} onClick={applyDraft}>Show results</Button></div>
    </SheetContent></Sheet>
    {chips.length>0 && <div className="stak-active-filters" aria-label="Active Discover filters">{chips.map(([name,label])=><Chip key={name} onClick={()=>{setDraft(null);remove(name);}} aria-label={`Remove ${label} filter`}>{label} ×</Chip>)}<Button variant="subtle" onClick={reset}>Reset all</Button></div>}
    <DiscoveryServices value={filters.provider} selected={selected} services={services} providers={providers.data?.providers} loading={providers.isPending} error={providers.error?errMsg(providers.error):''} onRetry={()=>providers.refetch()} onChange={value=>{setDraft(null);change('provider',value);}}/>
    {!searching && <section className="discovery-categories" aria-label="Curated discovery"><AnimatedSelection value={category} className="discovery-category-tabs no-scrollbar" role="group" aria-label="Discovery category">{discoveryChannels.map(item=><button key={item.value} data-value={item.value} aria-pressed={category===item.value} onClick={()=>update({category:item.value})}>{item.value==='trending'?'Trending':item.value==='top'?'Top rated':item.value==='new'?'New releases':item.label}</button>)}</AnimatedSelection></section>}
    <div className="discovery-sort"><span className="discovery-help">{searching?'Search across movies & series':type==='series'?'Series to explore':'Movies to explore'}</span><NothingSelect aria-label="Sort" value={filters.sort} onChange={e=>{setDraft(null);change('sort',e.target.value);}}>{discoverySorts.map(([value,label])=><option key={value} value={value}>{label}</option>)}</NothingSelect></div>
    <div ref={setResultsContainer} hidden={!searching}/>
    {filters.year_from && filters.year_to && Number(filters.year_from)>Number(filters.year_to)?<p role="alert">Choose a start year before the end year.</p>:filters.provider && !selected?.available?<p role="status" className="text-sm text-neutral-500">{providers.isPending?'Loading services…':'This service is unavailable for the current region or media type. Clear it or choose another service.'}</p>:!searching && <BrowseResults type={type} category={category} label={`${selected?.name?selected.name+' · ':''}${channel.label}`} filters={activeFilters} hideWatched={hideWatched} hideLibrary={hideLibrary} onReset={reset}/>}
    <div className="discovery-directors"><DiscoverDirectors/></div>
    {hideLibrary && <p role="status" className="text-[10px] text-neutral-500">{catalogError?'Library availability could not refresh.':`Availability checked for ${catalog?.items?.length || 0} known library titles.`} Unknown titles remain visible.{catalogError && <Button variant="outlined" onClick={()=>refreshCatalog?.()}>Retry</Button>}</p>}
    <p className="discovery-attribution">TMDb / JustWatch · {region}</p>
  </div>;
}
