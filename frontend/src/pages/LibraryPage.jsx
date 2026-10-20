import NothingSelect from "../components/NothingSelect";
import { useEffect, useMemo, useRef, useState } from "react";
import { Plus, RefreshCw } from "lucide-react";
import { toast } from "sonner";
import { useApp } from "../context/AppContext";
import { api, errMsg } from "../lib/api";
import { fmtBytes, statusOf } from "../lib/format";
import { Empty, NeedKey, PageTitle } from "../components/Bits";
import { ListSkeleton } from "../components/Skeletons";
import LibrarySwitch from "../components/LibrarySwitch";
import { useSearchParams } from "react-router-dom";
import { REFRESH_EVENT } from "../components/AppShell";
import { useT } from "../lib/i18n";
import LibraryRow from "../components/LibraryRow";
import {CollectionSearch,CollectionSheet} from "../components/CollectionControls";
import {useExperience} from "../lib/useExperience";
import {useCollectionSearch} from "../lib/useCollectionSearch";
import BulkBar from "../components/BulkBar";
import FilesSheet from "../components/FilesSheet";
import AddMagnetDialog from "../components/AddMagnetDialog";
import ShareCreateDialog from "../components/ShareCreateDialog";
import { AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent, AlertDialogDescription, AlertDialogFooter, AlertDialogHeader, AlertDialogTitle } from "../components/ui/alert-dialog";

const TABS = [["all", "All"], ["active", "Active"], ["done", "Completed"], ["error", "Error"]];
const SORTS = { date: (a, b) => new Date(b.created_at) - new Date(a.created_at), name: (a, b) => String(a.name || "").localeCompare(String(b.name || "")), size: (a, b) => b.size - a.size };

export default function LibraryPage() {
  const tr = useT();
  const { key, session, library, libLoading, libraryError, refreshLibrary } = useApp();
  const [prefs,setPrefs]=useExperience("torbox",{});
  const {tab,sort}=prefs;
  const update=patch=>setPrefs(previous=>({...previous,...patch}));
  const [q,setQ]=useCollectionSearch("torbox",session?.scope||"local");
  const [filtersOpen,setFiltersOpen]=useState(false),[selecting,setSelecting]=useState(false);
  const [sel, setSel] = useState(new Set());
  const [open, setOpen] = useState(null);
  const [addOpen, setAddOpen] = useState(false);
  const [shareOpen, setShareOpen] = useState(false);
  const [confirm, setConfirm] = useState(false);
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState("");

  const [params] = useSearchParams();
  const openedTorrent=useRef('');
  useEffect(()=>{
    const id=params.get('torrent');if(!id){openedTorrent.current='';return;}if(openedTorrent.current===id)return;
    const item=library.find(t=>String(t.id)===id);if(item){openedTorrent.current=id;setOpen(item);}
  },[params,library]);
  const reload = () => refreshLibrary().then(() => setErr("")).catch((e) => setErr(errMsg(e)));
  const hasActive = library.some(t => statusOf(t) === 'active');
  useEffect(() => { if (params.get("add")) setAddOpen(true); // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);
  useEffect(() => { window.addEventListener(REFRESH_EVENT, reload); return () => window.removeEventListener(REFRESH_EVENT, reload); // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);
  useEffect(() => {
    if (!key) return;
    reload();
    if (!hasActive) return;
    const iv = setInterval(() => { if (document.visibilityState === 'visible') reload(); }, 15000);
    return () => clearInterval(iv);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key, hasActive]);

  const counts = useMemo(() => library.reduce((a, t) => ({ ...a, [statusOf(t)]: (a[statusOf(t)] || 0) + 1 }), { all: library.length }), [library]);
  const shown = useMemo(() => {
    const ql = q.toLowerCase();
    return library.filter((t) => (tab === "all" || statusOf(t) === tab) && (!ql || String(t.name || "").toLowerCase().includes(ql) || String(t.hash || "").toLowerCase().includes(ql))).sort(SORTS[sort]);
  }, [library, tab, q, sort]);
  const selItems = library.filter((t) => sel.has(t.id));

  const toggle = (id) => {setSelecting(true);setSel((s) => { const n = new Set(s); n.has(id) ? n.delete(id) : n.add(id); return n; });};
  const control = async (operation, okMsg) => {
    setBusy(true);
    try {
      const { data } = await api.post("/torbox/control", { torrent_ids: [...sel], operation });
      const ok = data.filter((r) => r.ok).length;
      if (ok) toast.success(`${ok}/${data.length} ${okMsg}`);
      else toast.error(`No torrents were ${okMsg}. Retry the failed items.`);
      data.filter((r) => !r.ok).slice(0, 2).forEach((r) => toast.error(r.error));
      setSel(new Set(data.filter(r => !r.ok).map(r => r.id)));
      await reload();
    } catch (e) {
      toast.error(errMsg(e));
    } finally {
      setBusy(false);
      setConfirm(false);
    }
  };

  if (!key) return (<><LibrarySwitch /><PageTitle eyebrow="TorBox" title={tr("Library")} testId="library-title" /><NeedKey /></>);

  return (
    <div className="stak-screen stak-screen-torbox collection-page">
      <>
      <PageTitle
        title={tr("Library")}
        testId="library-title"
        right={
          <div className="flex gap-2">
            <button data-testid="library-refresh-button" aria-label="Refresh torrents" onClick={reload} className="n-icon-btn"><RefreshCw size={16} className={libLoading ? "animate-spin" : ""} /></button>
            <button data-testid="library-add-magnet-button" aria-label="Add magnet" onClick={() => setAddOpen(true)} className="n-icon-btn stak-button--primary"><Plus size={18} /></button>
          </div>
        }
      />
      <LibrarySwitch />
      <p className="collection-subtitle">{library.length} torrents · {fmtBytes(library.reduce((a,t)=>a+(t.size||0),0))}</p>
      <CollectionSearch value={q} onChange={setQ} label="Search by name or hash" testId="library-search-input" onFilters={()=>setFiltersOpen(true)} count={Number(sort!=='date')}/>
      <div className="collection-segments collection-task-tabs" aria-label="Torrent status">{TABS.map(([id,label])=><button key={id} data-testid={`library-tab-${id}`} aria-pressed={tab===id} data-active={tab===id} onClick={()=>update({tab:id})}>{tr(label)} <span>{counts[id]||0}</span></button>)}</div>
      <div className="collection-results-meta"><span>{shown.length} of {library.length} torrents · {sort==='date'?'Newest':sort==='size'?'Largest first':'Name A–Z'}</span><button className="stak-button stak-button--subtle" disabled={busy || !library.length} aria-pressed={selecting} onClick={()=>{setSelecting(!selecting);setSel(new Set());}}>{selecting?'Cancel selection':'Select'}</button></div>
      {selecting && <div className="collection-selection-tools"><span role="status">{sel.size} selected</span><button data-testid="library-select-all" className="n-chip" disabled={busy || !shown.length} onClick={()=>setSel(previous=>{const next=new Set(previous),all=shown.every(item=>next.has(item.id));shown.forEach(item=>all?next.delete(item.id):next.add(item.id));return next;})}>{shown.length && shown.every(item=>sel.has(item.id))?'Deselect visible':'Select visible'}</button></div>}
      <CollectionSheet open={filtersOpen} onOpenChange={setFiltersOpen} onReset={()=>update({sort:'date'})} testId="torbox-filters"><label className="collection-field">Sort by<NothingSelect aria-label="Torrent sort" data-testid="library-sort-select" value={sort} onChange={e=>update({sort:e.target.value})}><option value="date">Newest</option><option value="name">Name</option><option value="size">Size</option></NothingSelect></label></CollectionSheet>
      {err || libraryError ? <Empty title={tr("Error")} text={err || libraryError} testId="library-error" actionLabel={tr("Retry")} onAction={reload} /> : libLoading && !library.length ? <ListSkeleton rows={6} /> : !shown.length ? (
        <Empty title={library.length?"No matching torrents":"Your TorBox library is empty"} text={library.length?"Try another search or status filter.":"Add a magnet or torrent file to get started."} testId="library-empty" actionLabel={library.length?"Clear filters":tr("Add magnet")} onAction={() => {if(library.length){setQ("");update({tab:"all"});}else setAddOpen(true);}} />
      ) : (
        <>
        <div data-testid="library-list" className="collection-torrent-list">
          {shown.map((t, i) => <LibraryRow key={t.id} t={t} index={i} selected={sel.has(t.id)} selecting={selecting} onToggle={() => toggle(t.id)} onOpen={() => setOpen(t)} />)}
        </div>
        </>
      )}
      <BulkBar count={sel.size} busy={busy} onClear={() => {setSel(new Set());setSelecting(false);}} onDelete={() => setConfirm(true)} onShare={() => setShareOpen(true)} onReannounce={() => control("reannounce", "reannounced")} />
      <FilesSheet t={open} onClose={() => setOpen(null)} />
      <AddMagnetDialog open={addOpen} onOpenChange={setAddOpen} onDone={reload} />
      <ShareCreateDialog items={selItems} open={shareOpen} onOpenChange={setShareOpen} />
      <AlertDialog open={confirm} onOpenChange={setConfirm}>
        <AlertDialogContent data-testid="delete-confirm-dialog" className="w-[calc(100%-2rem)] rounded-3xl border-[color:var(--c-2a2a2a)] bg-[color:var(--c-0f0f0f)]">
          <AlertDialogHeader className="text-left">
            <AlertDialogTitle className="font-dot text-2xl text-white">{tr("Delete?")}</AlertDialogTitle>
            <AlertDialogDescription>{sel.size} torrent(s) will be permanently deleted from your TorBox account.</AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter className="flex-row gap-2">
            <AlertDialogCancel data-testid="delete-cancel-button" className="n-btn-ghost mt-0 flex-1">{tr("Cancel")}</AlertDialogCancel>
            <AlertDialogAction data-testid="delete-confirm-button" onClick={(e) => { e.preventDefault(); control("delete", "deleted"); }} className="n-btn-destructive flex-1">{tr("Delete")}</AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
      </>
    </div>
  );
}
