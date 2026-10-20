import { useState } from "react";
import { Loader2 } from "lucide-react";
import { toast } from "sonner";
import { api, errMsg } from "../lib/api";

const EMPTY = { name: "", kind: "torznab", url: "", api_key: "" };
const HINT = {
  torznab: "Jackett: http://IP:9117/api/v2.0/indexers/all/results/torznab · Prowlarr: http://IP:9696/1/api",
  stremio: "Stremio addon URL, e.g. https://torrentio.strem.fun/providers=yts,eztv/manifest.json",
};

export default function IndexerForm({ onAdded }) {
  const [f, setF] = useState(EMPTY);
  const [busy, setBusy] = useState(null);
  const set = (k) => (e) => setF({ ...f, [k]: e.target.value });

  const test = async () => {
    setBusy("test");
    try {
      const { data } = await api.post("/indexers/test", f);
      data.ok ? toast.success(`Connected: ${data.detail}`) : toast.error(`Failed: ${data.detail}`);
    } catch (e) {
      toast.error(errMsg(e));
    } finally {
      setBusy(null);
    }
  };
  const add = async () => {
    setBusy("add");
    try {
      const { data } = await api.post("/indexers", { ...f, name: f.name || f.kind });
      onAdded(data);
      setF(EMPTY);
      toast.success("Indexer added");
    } catch (e) {
      toast.error(errMsg(e));
    } finally {
      setBusy(null);
    }
  };

  return (
    <div data-testid="indexer-form" className="space-y-2.5 rounded-2xl border border-dashed border-[color:var(--c-2a2a2a)] p-3">
      <div className="flex gap-2">
        {[["torznab", "Torznab"], ["stremio", "Stremio addon"]].map(([v, l]) => (
          <button disabled={!!busy} key={v} data-testid={`indexer-kind-${v}`} data-active={f.kind === v} onClick={() => setF({ ...f, kind: v })} className="n-chip">{l}</button>
        ))}
      </div>
      <input disabled={!!busy} data-testid="indexer-name-input" value={f.name} onChange={set("name")} placeholder="Name (e.g. Prowlarr)" className="n-input" />
      <input disabled={!!busy} data-testid="indexer-url-input" value={f.url} onChange={set("url")} placeholder="URL" className="n-input font-nmono text-xs" autoCapitalize="off" />
      <div className="break-all px-2 font-nmono text-[10px] leading-relaxed text-neutral-500">{HINT[f.kind]}</div>
      {f.kind === "torznab" && (
        <input disabled={!!busy} data-testid="indexer-apikey-input" type="password" value={f.api_key} onChange={set("api_key")} placeholder="API key" className="n-input font-nmono" autoComplete="off" />
      )}
      <div className="flex gap-2">
        <button data-testid="indexer-test-button" onClick={test} disabled={!!busy || !f.url} className="n-btn-ghost flex-1">
          {busy === "test" && <Loader2 size={14} className="animate-spin" />} Test
        </button>
        <button data-testid="indexer-add-button" onClick={add} disabled={!!busy || !f.url} className="n-btn-red flex-1">
          {busy === "add" && <Loader2 size={14} className="animate-spin" />} Add
        </button>
      </div>
    </div>
  );
}
