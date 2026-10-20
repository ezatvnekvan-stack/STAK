import { writeStored } from "../lib/storage";
import { useEffect, useState } from "react";
import { Trash2 } from "lucide-react";
import { toast } from "sonner";
import { Switch } from "./ui/switch";
import { api, IS_CAPACITOR_OFFLINE, errMsg, TORRENTIO_STORAGE, torrentioEnabled } from "../lib/api";
import { useApp } from "../context/AppContext";
import IndexerForm from "./IndexerForm";

const Row = ({ title, sub, checked, onChange, onDelete, testId }) => (
  <div data-testid={testId} className="flex items-center gap-3 border-b border-[color:var(--c-1c1c1c)] py-3 last:border-0">
    <div className="min-w-0 flex-1">
      <div className="truncate text-sm font-semibold text-white">{title}</div>
      <div className="truncate font-nmono text-[10px] text-neutral-500">{sub}</div>
    </div>
    {onDelete && <button data-testid={`${testId}-delete`} onClick={onDelete} className="n-icon-btn !h-9 !w-9 !text-[#ff4d55]"><Trash2 size={14} /></button>}
    <Switch data-testid={`${testId}-toggle`} checked={checked} onCheckedChange={onChange} />
  </div>
);

export default function SourcesSettings() {
  const { key } = useApp();
  const [tio, setTio] = useState(torrentioEnabled());
  const [list, setList] = useState([]);

  useEffect(() => {
    if (key || IS_CAPACITOR_OFFLINE) api.get("/indexers").then(({ data }) => setList(data)).catch(() => {});
  }, [key]);

  const toggleTio = (v) => {
    if (!writeStored(TORRENTIO_STORAGE, String(v))) { toast.error('Torrentio preference could not be saved.'); return; }
    setTio(v);
  };
  const toggle = async (ix, enabled) => {
    try {
      await api.patch(`/indexers/${ix.id}`, { enabled });
      setList((l) => l.map((x) => (x.id === ix.id ? { ...x, enabled } : x)));
    } catch (e) {
      toast.error(errMsg(e));
    }
  };
  const del = async (ix) => {
    try { await api.delete(`/indexers/${ix.id}`); setList((l) => l.filter((x) => x.id !== ix.id)); } catch (e) { toast.error(errMsg(e)); }
  };

  return (
    <div data-testid="sources-settings" className="n-card rise p-5">
      <div className="n-h2 mb-2">Indexers</div>

      <Row title="Torrentio" sub="built-in · torrentio.strem.fun" checked={tio} onChange={toggleTio} testId="source-torrentio" />
      {list.map((ix, i) => (
        <Row key={ix.id} title={ix.name} sub={`${ix.kind} · ${(() => { try { return new URL(ix.url).origin; } catch { return "Configured endpoint"; } })()}`} checked={ix.enabled} onChange={(v) => toggle(ix, v)} onDelete={() => del(ix)} testId={`indexer-row-${i}`} />
      ))}
      <div className="mt-3">
        {key || IS_CAPACITOR_OFFLINE ? <IndexerForm onAdded={(ix) => setList((l) => [...l, ix])} /> : <div className="text-xs text-neutral-500">Add your TorBox key above to manage custom indexers.</div>}
      </div>
    </div>
  );
}
