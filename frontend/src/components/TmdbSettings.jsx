import {useMetadataSources} from "../lib/useMetadataSources";
import {metadataChanged} from "../lib/metadata/runtime";
import { useState } from "react";
import { KeyRound, Loader2 } from "lucide-react";
import { toast } from "sonner";
import { api, errMsg } from "../lib/api";

export default function TmdbSettings() {
  const {status,refetch}=useMetadataSources();
  const saved=status.tmdb==='set';
  const [input, setInput] = useState("");
  const [show, setShow] = useState(false);
  const [busy, setBusy] = useState(false);
  const save = async () => {
    setBusy(true);
    try {
      await api.post("/metadata/test",{provider:"tmdb",value:input.trim()});
      await api.put("/metadata/credentials",{provider:"tmdb",value:input.trim()});
      metadataChanged();await refetch();
      setInput("");
      toast.success("TMDb connected · logos, cast, galleries and trailers enabled");
      window.dispatchEvent(new Event("tmdb-key-changed"));
    } catch (e) { toast.error(`TMDb verification failed: ${errMsg(e)}`); }
    finally { setBusy(false); }
  };
  const remove = async () => { try { await api.put("/metadata/credentials",{provider:"tmdb",value:""});metadataChanged();await refetch(); } catch (e) { toast.error(errMsg(e)); return; } window.dispatchEvent(new Event("tmdb-key-changed")); toast.success("TMDb key removed"); };
  return <div className="n-card rise p-5" data-testid="tmdb-settings">
    <div className="mb-2 flex items-center gap-2 font-nmono text-sm font-bold"><KeyRound size={16}/> TMDb · METADATA</div>
    <details className="stak-help"><summary>API key help</summary><p>Use an API key or API Read Access Token from TMDb Settings → API.</p></details>
    <p className="mb-3 font-nmono text-xs">{saved ? "● CONNECTED ON THIS DEVICE" : "○ NOT CONFIGURED"}</p>
    <div className="flex gap-2">
      <input disabled={busy} className="n-input min-w-0 flex-1 font-nmono" type={show ? "text" : "password"} value={input} onChange={e => setInput(e.target.value)} placeholder="TMDb API key or read access token" autoComplete="off" aria-label="TMDb API key or read access token" />
      <button type="button" className="n-btn-ghost" onClick={() => setShow(v => !v)}>{show ? "HIDE" : "SHOW"}</button>
    </div>
    <div className="mt-3 flex gap-2"><button className="n-btn-primary" disabled={!input.trim() || busy} onClick={save}>{busy ? <Loader2 size={16} className="animate-spin"/> : "SAVE & VERIFY"}</button>{saved && <button className="n-btn-ghost" disabled={busy} onClick={remove}>REMOVE</button>}</div>
    <p className="mt-3 text-xs text-neutral-500">Stored on this device; encrypted on Android. Use HTTPS on untrusted networks; other devices need their own key.</p>
    <p className="mt-2 text-xs text-neutral-500">This product uses the TMDB API but is not endorsed or certified by TMDB.</p>
  </div>;
}
