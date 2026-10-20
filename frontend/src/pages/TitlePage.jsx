import {useMetadataRevision} from '../lib/metadata/runtime';
import MetadataNotice from '../components/MetadataNotice';
import EpisodeDetailsDialog from "../components/EpisodeDetailsDialog";
import { useExperience } from '../lib/useExperience';
import { useQueryClient } from "@tanstack/react-query";
import { metadataOptions } from "../lib/metadataQuery";
import NothingSelect from "../components/NothingSelect";
import { useEffect, useMemo, useRef, useState } from "react";
import { Link, useNavigate, useParams, useSearchParams } from "react-router-dom";
import { createPortal } from "react-dom";
import { Bookmark, Loader2, Plus, RefreshCw } from "lucide-react";
import { toast } from "sonner";
import { IS_CAPACITOR_OFFLINE, checkCached, errMsg, torrentioEnabled } from "../lib/api";
import { fetchIndexers, fetchStreams, mergeStreams } from "../lib/torrentio";
import TitleHero from "../components/TitleHero";
import TitleAmbient from "../components/TitleAmbient";
import { useAccountQuery } from "../lib/useAccountQuery";
import { useTitleAvailability, effectiveEpisodes } from "../lib/titleAvailability";
import TrailerButton from '../components/TrailerButton';
import TitleProgress from "../components/TitleProgress";
import { useApp } from "../context/AppContext";
import { DotLoader, Empty, GlyphBurst } from "../components/Bits";
import StreamRow from "../components/StreamRow";
import StreamFilters from "../components/StreamFilters";
import EpisodePicker from "../components/EpisodePicker";
import DetailExtras from "../components/DetailExtras";
import {sourceSize} from '../lib/sourcePresentation';
import { isSeasonPack } from "../lib/seasonPack";
import { bestHash, scoreStream, readPreferHun } from "../lib/score";
import { ListSkeleton } from "../components/Skeletons";
import { useT } from "../lib/i18n";
import { buzz } from "../lib/haptics";
import { episodesOf, firstEpisode } from '../lib/episodes';
import { Dialog, DialogContent, DialogTitle, DialogDescription } from '../components/ui/dialog';

function WatchButton({ meta }) {
  const { key, watchIds, toggleWatch, watchUndo } = useApp();
  const [burst, setBurst] = useState(false),[busy,setBusy]=useState(false);
  const on = watchIds.has(meta.imdb_id || meta.id);
  const click = async () => {
    if(busy)return;
    if (!key && !IS_CAPACITOR_OFFLINE) return toast.error("Add your TorBox API key first");
    setBusy(true);
    try {
      const added = await toggleWatch(meta);
      buzz(added ? [10, 30, 10] : 8);
      if (added) { setBurst(true); setTimeout(() => setBurst(false), 800); }
      const undo=watchUndo?.(meta,added);
      toast(added ? "Added to watchlist" : "Removed from watchlist",{action:{label:"Undo",onClick:()=>{return undo?undo().catch(e=>toast.error(errMsg(e))):toggleWatch(meta,{desired:!added,expectedCurrent:added}).catch(e=>toast.error(errMsg(e)));}}});
    } catch (e) {
      toast.error(errMsg(e));
    } finally {setBusy(false);}
  };
  return (
    <button data-testid="watchlist-toggle-button" data-active={on} onClick={click} disabled={busy} aria-pressed={on} className={`n-chip detail-watchlist ${on ? "!border-[color:var(--n-accent)] !text-[color:var(--n-accent)]" : ""}`} aria-label="Toggle watchlist">
      <Bookmark size={16} className={on ? "fill-[color:var(--n-accent)]" : ""} />
      {on?'Saved':'Watchlist'}<GlyphBurst run={burst} />
    </button>
  );
}

function Synopsis({text}) {
  const [expanded,setExpanded]=useState(false);
  return <><p id="title-synopsis-copy" className={!expanded && text.length>220?'synopsis-preview':''}>{text}</p>{text.length>220 && <button className="stak-button stak-button--link" aria-expanded={expanded} aria-controls="title-synopsis-copy" onClick={()=>setExpanded(value=>!value)}>{expanded?'Read less':'Read more'}</button>}</>;
}
export default function TitlePage() {
  const { type, id } = useParams();
  const client=useQueryClient();
  const [params] = useSearchParams();
  const requestedSeason = Number(params.get('season'));
  const requestedEpisode = Number(params.get('episode'));
  const nav = useNavigate();
  const { key,rememberCachedTitle } = useApp();
  const t = useT();
  const metadataRevision=useMetadataRevision();
  const [meta, setMeta] = useState(null);
  const [showAll, setShowAll] = useState(false);
  const [hiddenA, setHiddenA] = useState(() => new Set());
  const [hiddenB, setHiddenB] = useState(() => new Set());
  const [ep, setEp] = useState({ s: 1, e: 0 });
  const [packMode, setPackMode] = useState(true);
  const [base, setBase] = useState([]);
  const [extra, setExtra] = useState([]);
  const [cached, setCached] = useState(new Set());
  const [cachePending,setCachePending]=useState(0),[episodeInfo,setEpisodeInfo]=useState(null);
  const [cacheError, setCacheError] = useState("");
  const [loading, setLoading] = useState(true);
  const [ixLoading, setIxLoading] = useState(false);
  const [error, setError] = useState("");
  const [sourceErrors, setSourceErrors] = useState([]);
  const [sourcePrefs,setSourcePrefs]=useExperience('sources',{preferHun:readPreferHun()});
  const f=useMemo(()=>({...sourcePrefs,cachedOnly:true,text:''}),[sourcePrefs]);
  const setF=next=>setSourcePrefs(typeof next==='function'?next(f):next);
  const [torrentSizes,setTorrentSizes]=useState({});
  const [tick, setTick] = useState(0);

  const [libraryDialog, setLibraryDialog] = useState(false);
  const openedQuickAction=useRef('');
  const loadedTitle=useRef('');
  useEffect(()=>{
    const request=`${type}:${id}:${params.toString()}`;
    if(params.get('add')==='1' && meta && loadedTitle.current===`${type}:${id}` && openedQuickAction.current!==request){openedQuickAction.current=request;setLibraryDialog(true);}
    if(params.get('add')!=='1')openedQuickAction.current='';
  },[params,meta,type,id]);
  const titleId = meta?.imdb_id || meta?.id || id;
  const tracking = useAccountQuery(`/tracking/title/${titleId}`);
  const availability = useTitleAvailability(meta,ep);
  const watchedEps = useMemo(() => effectiveEpisodes(tracking.data,availability.data,meta), [tracking.data,availability.data,meta]);
  useEffect(() => {
    let live = true;
    setMeta(null);
    setEpisodeInfo(null);
    setError('');
    setSourceErrors([]);
    setEp({s: 1, e: 0});
    client.fetchQuery(metadataOptions(`/meta/${type}/${id}`)).then((data) => {
      if (!live) return;
      if (!data || typeof data !== 'object' || Array.isArray(data)) throw new Error('The metadata provider returned invalid title data.');
      const videos = episodesOf(Array.isArray(data.videos) ? data.videos : []).filter(video=>video.season>0);
      const rawGenres = data.genres || data.genre;
      const genres = (Array.isArray(rawGenres) ? rawGenres : typeof rawGenres === 'string' ? rawGenres.split(',') : []).map(g => typeof g === 'string' ? g : g?.name).filter(g => typeof g === 'string');
      loadedTitle.current=`${type}:${id}`;
      setMeta({ ...data, type: data.type || type, videos, genres });
      const target = videos.find(v => v.season === requestedSeason && v.episode === requestedEpisode) || videos.find(v=>v.season>0);
      if (target) setEp({ s: target.season, e: target.episode });
      setPackMode(!(params.get('add')==='1' && requestedSeason>0 && requestedEpisode>0));
    }).catch((e) => live && setError(errMsg(e)));
    return () => { live = false; };
  }, [type, id, requestedSeason, requestedEpisode,client,params,metadataRevision]);
  useEffect(() => { setShowAll(false); }, [ep.s, packMode]);
  const streamId = type === "series" ? `${titleId}:${ep.s}:${packMode ? (firstEpisode(meta?.videos || [], ep.s)?.episode || 1) : ep.e}` : titleId;
  useEffect(() => {
    if (!meta || !libraryDialog) return;
    if(!/^tt\d+$/.test(titleId)){setLoading(false);setSourceErrors(['No safe IMDb episode identity is available. Source lookup is disabled.']);return;}
    let live = true;
    const addCached = (list) => {
      const hashes = list.map((x) => x.hash).filter(Boolean);
      if (!hashes.length || !key) return;
      setCachePending(n=>n+1);
      checkCached(hashes,{includeDetails:true}).then((c) => {if(live){setTorrentSizes(previous=>({...previous,...c.torrentSizes}));setCached((p) => new Set([...p, ...c]));if(c.size)rememberCachedTitle?.(titleId);}}).catch((e) => live && setCacheError(`Cache check failed: ${errMsg(e)}`)).finally(()=>{if(live)setCachePending(n=>Math.max(0,n-1));});
    };
    setLoading(true);
    setSourceErrors([]);
    setCached(new Set());
    setTorrentSizes({});
    setCacheError("");
    setCachePending(0);
    setBase([]);
    setExtra([]);
    setHiddenA(new Set());
    setHiddenB(new Set());
    const p1 = torrentioEnabled()
      ? fetchStreams(type, streamId).then((l) => { if (live) { const packOnly = type === "series" && packMode && !showAll; const result = packOnly ? l.filter((x) => isSeasonPack(x, ep.s)) : l; setHiddenA(new Set(l.filter((x) => !result.includes(x)).map((x) => x.hash))); setBase(result); addCached(result); } }).catch((e) => live && setSourceErrors(old => [...old, `Torrentio: ${errMsg(e)}`]))
      : Promise.resolve();
    p1.finally(() => live && setLoading(false));
    if (key || IS_CAPACITOR_OFFLINE) {
      setIxLoading(true);
      fetchIndexers(type, titleId, meta, ep.s, type === "series" && packMode ? 0 : ep.e)
        .then(({ list, errors }) => {
          if (!live) return;
          const packOnly = type === "series" && packMode && !showAll;
          const result = packOnly ? list.filter((x) => isSeasonPack(x, ep.s)) : list;
          setHiddenB(new Set(list.filter((x) => !result.includes(x)).map((x) => x.hash)));
          setExtra(result);
          addCached(result);
          setSourceErrors(old => [...old, ...errors.map(x => `${x.name || x.source || 'Indexer'}: ${x.error}`)]);
        })
        .catch((e) => live && setSourceErrors(old => [...old, `Indexers: ${errMsg(e)}`]))
        .finally(() => live && setIxLoading(false));
    }
    return () => { live = false; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [type, streamId, meta, key, tick, libraryDialog, packMode, showAll]);

  const matchContext=useMemo(()=>({meta,season:ep.s,episode:ep.e,packMode}),[meta,ep.s,ep.e,packMode]);
  const streams = useMemo(() => mergeStreams(base, extra).map(stream=>({...stream,torrent_size:torrentSizes[stream.hash] || stream.torrent_size})), [base, extra,torrentSizes]);
  const counts = useMemo(() => streams.filter(s=>cached.has(s.hash)).reduce((a, s) => ({ ...a, [s.resolution]: (a[s.resolution] || 0) + 1 }), {}), [streams,cached]);
  // Source labels are inferred from names/tags; they are not verified media metadata.
  const matchesSmartFilters = (s) => {
    const label = `${s.name || ""} ${s.file || ""} ${s.tags || ""}`;
    const bytes=sourceSize(s,type==='series' && isSeasonPack(s,ep.s));
    if (f.hdr && !/\b(HDR10\+?|HDR|HLG|Dolby[ ._-]?Vision|DV)\b/i.test(label)) return false;
    if (f.dv && !/\b(Dolby[ ._-]?Vision|DOVI|DV)\b/i.test(label)) return false;
    if (f.atmos && !/\bAtmos\b/i.test(label)) return false;
    if (f.remux && !/\bREMUX\b/i.test(label)) return false;
    if (f.webdl && !/\bWEB[ ._-]?DL\b/i.test(label)) return false;
    if (f.maxSize && (!Number.isFinite(Number(bytes)) || Number(bytes) <= 0 || Number(bytes) > Number(f.maxSize) * 1024 ** 3)) return false;
    return true;
  };
  const shown = useMemo(() => {
    const t = '';
    return streams
      .filter((s) => (f.res === "All" || s.resolution === f.res) && (!t || (s.name + s.tags).toLowerCase().includes(t)) && cached.has(s.hash) && matchesSmartFilters(s))
      .sort((a, b) => f.sort === "best" ? scoreStream(b, cached, f.preferHun, matchContext) - scoreStream(a, cached, f.preferHun, matchContext) : (cached.has(b.hash) - cached.has(a.hash)) || (f.sort === "size" ? (sourceSize(b,type==='series' && isSeasonPack(b,ep.s)) || 0)-(sourceSize(a,type==='series' && isSeasonPack(a,ep.s)) || 0) : b.seeders - a.seeders));
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [streams, f, cached,matchContext]);

  const best = useMemo(() => (shown.length > 1 ? bestHash(shown, cached, f.preferHun,matchContext) : null), [shown, cached, f.preferHun,matchContext]);
  const hiddenCount = useMemo(() => new Set([...hiddenA, ...hiddenB]).size, [hiddenA, hiddenB]);
  const seasonEps = useMemo(() => (meta?.videos || []).filter((v) => Number(v.season) === ep.s).length, [meta, ep.s]);
  const noPack = type === "series" && packMode && !showAll && !streams.length;

  if (!meta && error) return <Empty title="Error" text={error} testId="title-error" />;
  if (!meta) return <DotLoader />;

  return (
    <div className="title-page relative isolate">
      <TitleAmbient src={meta.background}/>
      <TitleHero meta={meta} onBack={() => nav(-1)} />
      <MetadataNotice notices={meta.notices}/>
      {!/^tt\d+$/.test(titleId) && <p role="status" className="mb-3 text-xs text-neutral-400">{t("No IMDb identifier is available. Torrent source lookup is disabled for this title.")}</p>}
      <div className="detail-quick-actions"><TrailerButton imdbId={titleId} type={type}/><WatchButton meta={meta}/></div>
      <TitleProgress meta={meta} ep={ep}/>
      {createPortal(<div data-testid="title-action-bar" className="stak-action-dock title-action-dock sheet-up fixed inset-x-4 bottom-[calc(env(safe-area-inset-bottom,0px)+12px)] z-50 mx-auto flex max-w-sm items-center gap-1.5 p-1">
        <button data-testid="open-library-picker" disabled={!/^tt\d+$/.test(titleId)} onClick={() => { buzz(8); setPackMode(true);setLibraryDialog(true); }} className="n-btn-primary"><Plus size={15} /> {t("Find sources")}</button>
      </div>, document.body)}
      
      {meta.description && <section className="title-synopsis detail-synopsis"><h2 className="n-label">Synopsis</h2><Synopsis text={meta.description}/></section>}

      {type === "series" && meta.videos?.length > 0 && (
        <EpisodePicker videos={meta.videos} season={ep.s} episode={ep.e} watched={watchedEps} onChange={(s, e) => setEp({ s, e })} onOpenEpisode={video=>setEpisodeInfo(video)} />
      )}
      {episodeInfo && <EpisodeDetailsDialog meta={meta} video={episodeInfo} titleId={titleId} titleName={meta.name} onClose={()=>setEpisodeInfo(null)} onAdd={video=>{setEp({s:video.season,e:video.episode});setPackMode(false);setEpisodeInfo(null);setLibraryDialog(true);}}/>}
      <DetailExtras imdbId={titleId} type={type} meta={meta} watched={watchedEps} />
      <Dialog open={libraryDialog} onOpenChange={setLibraryDialog}>
        <DialogContent className="dmm-source-sheet w-[calc(100vw-24px)] max-w-2xl max-h-[90dvh] overflow-y-auto overscroll-contain bg-[color:var(--c-101010)] p-5 pb-[max(20px,env(safe-area-inset-bottom))]">
          <div className="source-title-header" data-testid="source-title-header">
            <img className="source-title-poster" src={meta.poster || '/icon.svg'} alt="" decoding="async" onError={event=>{if(event.currentTarget.dataset.fallback!=='true'){event.currentTarget.dataset.fallback='true';event.currentTarget.src='/icon.svg';}}}/>
            <div className="min-w-0 flex-1">
              <DialogTitle className="source-title-name pr-8">{meta.name}</DialogTitle>
              <p className="source-title-meta">{[meta.releaseInfo || meta.year, type==='series'?'Series':'Movie', meta.runtime].filter(Boolean).join(' · ')}</p>
              <div className="flex min-h-11 items-center justify-between gap-2">
                <p className="source-title-meta">{type==='series'?`S${ep.s} · ${packMode?'Season pack':`E${ep.e}`}`:(meta.genres || []).slice(0,2).join(' · ')}</p>
              </div>
            </div>
            <button data-testid="streams-refresh-button" aria-label="Refresh torrent sources" onClick={() => setTick(x => x+1)} className="n-icon-btn absolute source-refresh"><RefreshCw size={14}/></button>
          </div>
          <DialogDescription className="sr-only">Torrent source selection for {meta.name}</DialogDescription>
          <div className="source-body">
          {type === "series" && <div className="source-selection-panel">
            <label className="n-label">Season<NothingSelect className="n-input mt-2" aria-label="Choose season" value={ep.s} onChange={event=>{const s=Number(event.target.value);setEp({s,e:firstEpisode(meta.videos,s)?.episode || 1});}}>{[...new Set((meta.videos || []).map(v=>Number(v.season)))].filter(n=>n>0).sort((a,b)=>a-b).map(s=><option key={s} value={s}>{`Season ${s}`}</option>)}</NothingSelect></label>
            <div className="flex flex-wrap items-center gap-2"><button data-testid="source-mode-pack" className="n-chip" data-active={packMode} onClick={() => setPackMode(true)}>Season packs</button><button data-testid="source-mode-episode" className="n-chip" data-active={!packMode} onClick={() => setPackMode(false)}>Episode</button>{!packMode && <NothingSelect data-testid="source-episode-select" aria-label="Search episode" className="n-input !w-auto" value={ep.e} onChange={e => setEp(old => ({ ...old, e: Number(e.target.value) }))}>{episodesOf(meta.videos, ep.s).map(v => <option key={v.episode} value={v.episode}>E{v.episode} · {v.name || v.title}</option>)}</NothingSelect>}</div>
          </div>}
          {cachePending>0 && <span className="sr-only" role="status">Checking cache…</span>}
          <StreamFilters f={f} setF={setF} counts={counts} total={shown.length}/>
          {type === "series" && packMode && !showAll && hiddenCount > 0 && <div className="mb-3 flex items-center justify-between gap-3 font-nmono text-[11px] text-neutral-400"><span>{hiddenCount} {t("single episodes hidden")}</span><button onClick={() => setShowAll(true)} className="min-h-[32px] text-white underline decoration-[color:var(--n-accent)] decoration-2 underline-offset-4">{t("Show all")}</button></div>}
          {type === "series" && packMode && showAll && <div className="mb-3 flex justify-end"><button onClick={() => setShowAll(false)} className="n-chip">{t("Packs only")}</button></div>}
          {ixLoading && <div className="mb-3 font-nmono text-xs text-neutral-400"><Loader2 size={12} className="mr-2 inline animate-spin"/>Searching indexers…</div>}
          {cacheError && <p role="status" className="mb-3 text-xs text-amber-400">{cacheError}. Only confirmed cached releases can be shown. Retry to check again.</p>}
          {sourceErrors.length > 0 && <div role="alert" className="n-card mb-3 p-3 text-xs text-amber-400 space-y-2">{sourceErrors.map((message, i) => <p key={i}>{message}</p>)}<Link className="n-chip" to="/account?section=sources" onClick={() => setLibraryDialog(false)}>Check torrent sources</Link></div>}
          {!key && <p className="mb-3 text-xs text-neutral-400">Set your TorBox API key in Settings to check cache and add torrents.</p>}
          {(loading || ixLoading || cachePending>0) && !shown.length ? <ListSkeleton rows={5}/> : !shown.length ? sourceErrors.length && !streams.length ? <Empty title="Search unavailable" text="One or more sources failed. Retry or check source settings." actionLabel="Retry search" onAction={() => setTick(x => x + 1)} /> : noPack ? <Empty title={t("No pack found")} text={t("No season pack matched. Show every release or search single episodes.")} testId="no-pack">
            <div className="mt-2 flex flex-wrap justify-center gap-2"><button onClick={() => setShowAll(true)} className="n-btn-ghost">{t("Show all releases")}</button><button onClick={() => { setPackMode(false); setEp((x) => ({ ...x, e: 1 })); }} className="n-btn-ghost">{t("Try episodes")}</button></div>
          </Empty> : <Empty title="No cached releases" text={streams.length ? "No confirmed cached releases match your filters. Refresh cache checks or adjust quality filters." : "No cached releases were confirmed. Check your TorBox connection and torrent sources, then retry."} actionLabel={streams.length ? "Clear source filters" : "Retry search"} onAction={() => streams.length ? setF(old => ({ ...old, res: "All", text: "", cachedOnly: true, hdr: false, dv: false, atmos: false, remux: false, webdl: false, maxSize: "" })) : setTick(x => x + 1)} /> : <div className="space-y-2.5">{shown.map((s,i) => <StreamRow key={s.hash} s={s} index={i} cached={cached.has(s.hash)} matchContext={matchContext} best={s.hash === best} pack={type === "series" && packMode && isSeasonPack(s, ep.s) ? { season: ep.s, episodes: seasonEps } : null}/>)}</div>}
          </div>
        </DialogContent>
      </Dialog>

    </div>
  );
}
