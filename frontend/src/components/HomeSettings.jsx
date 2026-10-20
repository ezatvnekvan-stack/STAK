import NothingSelect from "./NothingSelect";
import { useEffect, useState } from "react";
import { ArrowDown, ArrowUp, Plus, Trash2 } from "lucide-react";
import { Switch } from "./ui/switch";
import { api } from "../lib/api";
import { DEFAULT_HOME, sectionInfo, useHomePrefs } from "../lib/home";

export default function HomeSettings() {
  const [home, save, status] = useHomePrefs();
  const [genres, setGenres] = useState({});
  const [g, setG] = useState({ type: "movie", genre: "" });
  useEffect(() => { api.get("/meta/genres").then(({ data }) => setGenres(data)).catch(() => {}); }, []);

  const move = (i, d) => {
    const n = [...home];
    let target=i+d;
    while(n[target]?.id==='because')target+=d;
    if(!n[target])return;
    [n[i], n[target]] = [n[target], n[i]];
    save(n);
  };
  const addGenre = () => {
    const id = `genre:${g.type}:${g.genre}`;
    if (!g.genre || home.some((s) => s.id === id)) return;
    save([...home, { id, on: true }]);
  };

  return (
    <div data-testid="home-settings" className="n-card rise p-5">
      <div className="n-h2 mb-2">Home screen</div>

      {status.loading && <p role="status" className="mb-3 text-xs text-neutral-400">Loading preferences…</p>}
      {status.saving && <p role="status" className="mb-3 text-xs text-neutral-400">Saving preferences…</p>}
      {status.error && <div role="alert" className="mb-3 text-xs text-red-400">{status.error} <button type="button" onClick={status.retry} className="stak-button stak-button--link">Reload saved preferences</button></div>}
      <fieldset disabled={status.loading} className="min-w-0 disabled:opacity-50">
      {home.map((s, i) => s.id==='because'?null:(
        <div key={s.id} data-testid={`home-section-${i}`} className="flex items-center gap-2 border-b border-[color:var(--c-1c1c1c)] py-2.5 last:border-0">
          <div className="min-w-0 flex-1 truncate text-sm text-white">{sectionInfo(s.id)?.label || s.id}</div>
          <button data-testid={`home-section-${i}-up`} aria-label={`Move ${sectionInfo(s.id)?.label || s.id} up`} disabled={i === 0} onClick={() => move(i, -1)} className="n-icon-btn !h-8 !w-8"><ArrowUp size={13} /></button>
          <button data-testid={`home-section-${i}-down`} aria-label={`Move ${sectionInfo(s.id)?.label || s.id} down`} disabled={i === home.length - 1} onClick={() => move(i, 1)} className="n-icon-btn !h-8 !w-8"><ArrowDown size={13} /></button>
          {s.id.startsWith("genre:") && <button data-testid={`home-section-${i}-remove`} aria-label={`Remove ${sectionInfo(s.id)?.label || s.id} shelf`} onClick={() => save(home.filter((x) => x.id !== s.id))} className="n-icon-btn !h-8 !w-8 !text-[#ff4d55]"><Trash2 size={13} /></button>}
          <Switch data-testid={`home-section-${i}-toggle`} aria-label={`Show ${sectionInfo(s.id)?.label || s.id}`} checked={s.on} onCheckedChange={(on) => save(home.map((x) => (x.id === s.id ? { ...x, on } : x)))} className="data-[state=checked]:bg-[color:var(--n-accent)]" />
        </div>
      ))}
      <div className="mt-3 flex gap-2">
        <NothingSelect data-testid="home-genre-type" aria-label="Genre media type" value={g.type} onChange={(e) => setG({ type: e.target.value, genre: "" })} className="n-input !h-10 !w-auto !px-3 font-nmono text-xs">
          <option value="movie">Movies</option>
          <option value="series">Series</option>
        </NothingSelect>
        <NothingSelect data-testid="home-genre-select" aria-label="Genre for new shelf" value={g.genre} onChange={(e) => setG({ ...g, genre: e.target.value })} className="n-input !h-10 min-w-0 flex-1 !px-3 font-nmono text-xs">
          <option value="">Genre row…</option>
          {(Array.isArray(genres[g.type]) ? genres[g.type] : []).map((x) => { const name = typeof x === "string" ? x : x?.name; return name ? <option key={name} value={name}>{name}</option> : null; })}
        </NothingSelect>
        <button data-testid="home-genre-add" aria-label="Add genre shelf" onClick={addGenre} disabled={!g.genre} className="n-icon-btn !h-10 !w-10 !border-white !bg-white !text-black"><Plus size={16} /></button>
      </div>
      <button data-testid="home-reset-button" onClick={() => save(DEFAULT_HOME)} className="n-btn-ghost mt-3">Reset to default</button>
      </fieldset>
    </div>
  );
}
