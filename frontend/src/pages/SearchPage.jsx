import {createPortal} from 'react-dom';
import {filterKnownTitles} from '../lib/experience';
import { Link } from 'react-router-dom';
import { useEffect, useMemo, useRef, useState } from "react";
import { useInfiniteQuery } from "@tanstack/react-query";
import { useSearchParams } from "react-router-dom";
import { Loader2, Search, SlidersHorizontal, X } from "lucide-react";
import { api, errMsg } from "../lib/api";
import { useApp } from "../context/AppContext";
import { accountRequest } from "../lib/accountQueries";
import { rankTitles } from "../lib/searchRelevance";
import {useMetadataRevision} from "../lib/metadata/runtime";
import {dedupeTitles} from "../lib/metadata/normalize";
import MetadataNotice from "../components/MetadataNotice";
import PosterCard from "../components/PosterCard";
import { Empty } from "../components/Bits";
import { PosterGridSkeleton } from "../components/Skeletons";
import { useT } from "../lib/i18n";
import DiscoverFilters from "../components/DiscoverFilters";

const KEYS = ["q", "type", "genre", "year_from", "year_to", "min_rating", "sort"];
const clean = (o) => Object.fromEntries(Object.entries(o).filter(([k, v]) => v !== "" && v != null && !(k === "min_rating" && !+v) && !(k === "sort" && v === "popular")));

function ActiveChips({ f, onRemove }) {
  const chips = [
    f.genre && ["genre", f.genre],
    (f.year_from || f.year_to) && ["year", `${f.year_from || "…"}–${f.year_to || "…"}`],
    +f.min_rating > 0 && ["min_rating", `★ ${f.min_rating}+`],
    f.sort && f.sort !== "popular" && ["sort", `Sort: ${f.sort}`],
  ].filter(Boolean);
  if (!chips.length) return null;
  return (
    <div className="no-scrollbar -mx-4 mb-4 flex gap-2 overflow-x-auto px-4">
      {chips.map(([k, l]) => (
        <button key={k} data-testid={`active-filter-${k}`} onClick={() => onRemove(k)} className="n-chip !border-[rgb(var(--n-accent-rgb)/0.6)] !text-white">{l} <X size={12} className="ml-1.5" /></button>
      ))}
    </div>
  );
}

export default function SearchPage({ embedded = false, mediaType,discoveryFilters,onFiltersRequest,resultsContainer,onResetFilters, hideFilters = false, searchOnly = false }) {
  const t = useT();
  const { session,tracking,catalog } = useApp();
  const [params, setParams] = useSearchParams();
  const fKey = params.toString();
  const f = useMemo(() => { const saved=new URLSearchParams(fKey),value=Object.fromEntries(KEYS.map(k=>[k,saved.get(k) || '']));if(!['','movie','series'].includes(value.type))value.type='';if(discoveryFilters)Object.assign(value,{genre:discoveryFilters.genre || '',year_from:discoveryFilters.year_from || '',year_to:discoveryFilters.year_to || '',min_rating:discoveryFilters.imdb_rating || '',sort:discoveryFilters.sort || ''});return value;},[fKey,discoveryFilters]);
  const q = f.q;
  const metadataRevision=useMetadataRevision();
  const committed=useRef(q);
  const [composing,setComposing]=useState(false);
  const browsing = discoveryFilters?!!q:!!(q || params.get("browse") || f.genre || f.year_from || f.year_to || +f.min_rating || (f.sort && f.sort !== "popular"));
  const [input, setInput] = useState(q);
  const [open, setOpen] = useState(false),[suggesting,setSuggesting]=useState(false);

  const blocked=discoveryFilters?.blocked;
  const fetchPage=async(cursor,signal)=>{
    const filters=discoveryFilters?Object.fromEntries(Object.entries(discoveryFilters).filter(([name])=>!['hideWatched','hideLibrary','blocked'].includes(name))):{};
    const {data}=await api.get('/metadata/search',{...accountRequest(session,{signal}),params:{...clean(f),...filters,q,...(!q?{type:mediaType || f.type}: {type:undefined}),imdb_rating:filters.imdb_rating || f.min_rating || '',page:typeof cursor==='number'?Math.max(1,cursor):1,...(typeof cursor==='object'?{pages:JSON.stringify(cursor)}:{})}});
    return data;
  };
  const query = useInfiniteQuery({
    queryKey: ["account", session.scope, "search", metadataRevision,fKey,q?'all':mediaType,discoveryFilters?Object.fromEntries(Object.entries(discoveryFilters).filter(([name])=>!['hideWatched','hideLibrary','blocked'].includes(name))):null],
    queryFn: ({ pageParam, signal }) => fetchPage(pageParam, signal),
    initialPageParam: 1,
    getNextPageParam: (page) => page.next_cursor ?? undefined,
    enabled: browsing && !blocked,
    retry: false, staleTime: 120000, refetchOnWindowFocus: false,
  });
  const items=useMemo(()=>{const all=dedupeTitles((query.data?.pages.flatMap(page=>page.items)||[]).filter(x=>x?.id));const filtered=discoveryFilters?filterKnownTitles(all,tracking,catalog,discoveryFilters):all;return q?rankTitles(filtered,q):filtered;},[query.data,q,discoveryFilters,tracking,catalog]);
  const partialErrors = [...new Set(query.data?.pages.flatMap(page => page.errors || []) || [])];
  const next = query.hasNextPage;
  const loading = browsing && !blocked && query.isPending;
  const more = query.isFetchingNextPage;
  const error = query.error ? errMsg(query.error) : "";
  useEffect(() => {if(q!==committed.current){committed.current=q;setInput(q);}}, [q]);
  useEffect(() => {
    if(composing)return undefined;
    const t = setTimeout(() => {
      if (input.trim() !== q) {committed.current=input.trim();setParams(clean({ ...f, q: input.trim() }), {replace: true});}
    }, 320);
    return () => clearTimeout(t);
  }, [input, q, f, setParams,composing]);

  const groups = [["", items]];
  const activeFilters = [f.genre, f.year_from || f.year_to, +f.min_rating > 0, f.sort && f.sort !== "popular"].filter(Boolean).length;
  const clearAll = () => {onResetFilters?.();setParams({});};
  const apply = (o) => setParams({ browse: "1", ...clean({ ...f, ...o }) });
  const loadMore = () => {
    query.fetchNextPage();
  };
  const remove = (k) => apply(k === "year" ? { year_from: "", year_to: "" } : { [k]: k === "sort" ? "popular" : "" });
  const submit = (e) => {
    e.preventDefault();
    input.trim() ? apply({ q: input.trim() }) : setParams({});
  };

  const resultView=blocked?null:(!browsing ? (embedded ? null : <div className="n-label">Search movies and series together</div>) : (
        <>
          <div className="n-label mb-3">{q ? `${t("Results")}: “${q}”` : t("Browse")} · {items.length}</div>
          {error && <div role="alert" className="mb-4"><Empty title={t("Error")} text={error} testId="search-error" actionLabel={t("Retry")} onAction={() => query.isFetchNextPageError ? query.fetchNextPage() : query.refetch()} /></div>}
          {loading ? <PosterGridSkeleton count={8} className="search-four-grid" /> : !items.length && error ? null : !items.length ? (
            <Empty title={t("No results")} text={t("Try another term or loosen the filters.")} testId="search-empty" actionLabel={t("Clear filters")} onAction={clearAll} />
          ) : (
            <>
              <div data-testid="search-results-grid">
                {groups.map(([label, list]) => (
                  <section key={label || "all"} className="mb-6">
                    {label && <h2 className="n-h2 mb-3 flex items-baseline gap-2">{label}<span className="font-nmono text-[11px] text-neutral-500">{list.length}</span></h2>}
                    <div className="search-four-grid">
                      {list.map((m, i) => <PosterCard key={m.id + i} item={m} index={i} term={q} />)}
                    </div>
                  </section>
                ))}
              </div>
              {next && (
                <button data-testid="load-more-button" onClick={loadMore} disabled={more} className="n-btn-ghost mx-auto mt-2 flex">
                  {more ? <Loader2 size={14} className="animate-spin" /> : null} {t("Load more")}
                </button>
              )}
            </>
          )}
        </>
      ));

  return (
    <div onBlur={event=>{if(!event.currentTarget.contains(event.relatedTarget))setSuggesting(false);}}>
      <form onSubmit={submit} className="rise relative mb-4 flex gap-2" style={{ animationDelay: "60ms" }}>
        <div className="relative min-w-0 flex-1">
          <Search size={16} className="absolute left-5 top-1/2 -translate-y-1/2 text-neutral-500" />
          <input aria-label={t('Search')} data-testid="media-search-input" value={input} onCompositionStart={()=>setComposing(true)} onCompositionEnd={e=>{setComposing(false);setInput(e.currentTarget.value);}} onFocus={()=>setSuggesting(true)} onKeyDown={e=>{if(e.key==='Escape')setSuggesting(false);}} onChange={(e) => {setSuggesting(true);setInput(e.target.value);}} placeholder={t("Movie, series or IMDb ID")} className="n-input !pl-11 !pr-10" enterKeyHint="search" />
          {input && (
            <button type="button" aria-label="Clear search" data-testid="media-search-clear" onClick={() => { setInput(""); setParams({}); }} className="n-icon-btn !absolute right-1 top-1/2 -translate-y-1/2 !border-transparent"><X size={14} /></button>
          )}
        </div>
        {!hideFilters && <button type="button" data-testid="open-filters-button" aria-label={t("Filters")} onClick={() => onFiltersRequest?onFiltersRequest():setOpen(true)} className="n-icon-btn relative"><SlidersHorizontal size={17} />{activeFilters > 0 && <span className="red-dot absolute -right-0.5 -top-0.5" />}</button>}

      </form>
        {suggesting && q && input.trim()===q && items.length>0 && <div aria-label="Suggested titles" className="search-suggestions">{items.slice(0,3).map(item=><Link key={`${item.type}:${item.id}`} className="search-suggestion" onClick={()=>setSuggesting(false)} to={`/title/${item.type}/${item.imdb_id || item.id}`}><span className="truncate">{item.name}</span><small>{item.year || item.releaseInfo} · {item.type==='series'?'TV':'Movie'}</small></Link>)}</div>}

      <MetadataNotice notices={query.data?.pages.flatMap(page=>page.source==='cinemeta' ? [...(page.notices || []),'TMDb key missing. Search is using Cinemeta. Configure TMDb in Metadata sources.'] : page.notices || []) || []}/>
      {!discoveryFilters && <ActiveChips f={f} onRemove={remove} />}
      {partialErrors.length > 0 && <div role="status" className="mb-3 text-xs text-neutral-400">{partialErrors.join(' · ')} <button className="n-chip" onClick={() => query.refetch()}>Retry missing results</button></div>}
      {!hideFilters && !onFiltersRequest && <DiscoverFilters open={open} onOpenChange={setOpen} value={f} onApply={(o) => { setOpen(false); apply(o); }} />}
      {searchOnly && !q?null:resultsContainer===undefined?resultView:resultsContainer?createPortal(resultView,resultsContainer):null}
    </div>
  );
}
