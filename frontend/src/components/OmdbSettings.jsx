import {useMetadataSources} from "../lib/useMetadataSources";
import {metadataChanged} from "../lib/metadata/runtime";
import { useState } from "react";
import { Loader2 } from "lucide-react";
import { toast } from "sonner";

import { api, errMsg } from "../lib/api";

export default function OmdbSettings() {
  const {status,refetch}=useMetadataSources();
  const saved=status.omdb==='set';
  const [input, setInput] = useState("");
  const [busy, setBusy] = useState(false);

  const save = async () => {
    setBusy(true);
    try {
      await api.post("/metadata/test",{provider:"omdb",value:input.trim()});
      await api.put("/metadata/credentials",{provider:"omdb",value:input.trim()});metadataChanged();await refetch();
      setInput("");
      toast.success("OMDb connected");
    } catch (e) {
      toast.error(`Invalid OMDb key: ${errMsg(e)}`);
    } finally {
      setBusy(false);
    }
  };
  const remove = async () => {
    try { await api.put("/metadata/credentials",{provider:"omdb",value:""});metadataChanged();await refetch(); } catch (e) { toast.error(errMsg(e)); return; }

  };

  return (
    <div data-testid="omdb-settings" className="n-card rise p-5">
      <div className="n-h2 mb-2">Ratings</div>
      <p className="mb-4 text-sm text-neutral-400">
        Rotten Tomatoes, Metacritic and IMDb vote counts come from OMDb. Get a free key at{" "}
        <a href="https://www.omdbapi.com/apikey.aspx" target="_blank" rel="noreferrer" className="text-white underline">omdbapi.com</a>. Ratings are cached for at least 24 hours and count against your daily OMDb quota.
      </p>
      {saved && (
        <div className="mb-3 flex items-center justify-between gap-2">
          <span data-testid="omdb-saved-key" className="font-nmono text-sm text-white">Key set · encrypted on Android</span>
          <button data-testid="omdb-remove-button" onClick={remove} className="n-btn-ghost !min-h-[44px] !text-[#ff4d55]">Remove</button>
        </div>
      )}
      <div className="flex gap-2">
        <input disabled={busy} data-testid="omdb-key-input" type="password" value={input} onChange={(e) => setInput(e.target.value)} placeholder="OMDb API key" className="n-input flex-1 font-nmono" autoComplete="off" />
        <button data-testid="omdb-save-button" onClick={save} disabled={busy || !input.trim()} className="n-btn-primary">
          {busy ? <Loader2 size={14} className="animate-spin" /> : "Save"}
        </button>
      </div>
    </div>
  );
}
