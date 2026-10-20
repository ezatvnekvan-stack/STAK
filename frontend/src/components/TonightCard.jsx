import MediaQuickActions from "./MediaQuickActions";
import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { Shuffle } from "lucide-react";
import { api } from "../lib/api";
import { useApp } from "../context/AppContext";
import { useT } from "../lib/i18n";
import { useTint } from "../lib/tint";
import { buzz } from "../lib/haptics";

const poster = (id) => `https://images.metahub.space/poster/small/${id}/img`;
const minutes = (r) => { const m = String(r || "").match(/(\d+)\s*(?:h|hr)?\s*(\d+)?/i); if (!m) return null; return /h/i.test(String(r)) ? Number(m[1]) * 60 + Number(m[2] || 0) : Number(m[1]); };

// "Tonight" suggestion: unwatched watchlist first, then popular titles. Filters: type and "90 min or less".
export default function TonightCard({ refreshVersion = 0 }) {
  const t = useT();
  const { watchlist, tracking } = useApp();
  const [type, setType] = useState("any");
  const [short, setShort] = useState(false);
  const [popular, setPopular] = useState([]);
  const [seed, setSeed] = useState(0);
  const [pick, setPick] = useState(null);

  useEffect(() => {
    let live = true;
    Promise.allSettled([
      api.get("/discover", { params: { type: "movie", sort: "popular" } }),
      api.get("/discover", { params: { type: "series", sort: "popular" } }),
    ]).then((res) => {
      if (live) setPopular(res.flatMap((r) => (r.status === "fulfilled" ? (r.value.data.items || []).slice(0, 12) : [])));
    });
    return () => { live = false; };
  }, [refreshVersion]);

  const candidates = useMemo(() => {
    const own = watchlist.filter((w) => !tracking.get(w.imdb_id)?.played).map((w) => ({ id: w.imdb_id, type: w.type === "series" ? "series" : "movie", name: w.name, year: w.year, own: true }));
    const seen = new Set(own.map((x) => x.id));
    const rest = popular.filter((p) => p.id && !seen.has(p.id)).map((p) => ({ id: p.id, type: p.type === "series" ? "series" : "movie", name: p.name || p.title, year: p.releaseInfo || p.year }));
    return [...own, ...own, ...rest].filter((c) => (type === "any" || c.type === type) && !tracking.get(c.id)?.played); // own titles twice = more likely
  }, [watchlist, tracking, popular, type]);

  useEffect(() => {
    let live = true;
    if (!candidates.length) { setPick(null); return undefined; }
    (async () => {
      for (let tries = 0; tries < 6; tries++) {
        const c = candidates[Math.floor(Math.random() * candidates.length)];
        try {
          const { data } = await api.get(`/meta/${c.type}/${c.id}`);
          const len = minutes(data.runtime);
          if (short && (!len || len > 90)) continue;
          if (live) setPick({ ...c, meta: data });
          return;
        } catch { /* try another */ }
      }
      if (live) setPick(null);
    })();
    return () => { live = false; };
  }, [candidates, seed, short, refreshVersion]);

  const [r, g, b] = useTint(pick ? poster(pick.id) : null, [90, 90, 90]);
  const m = pick?.meta || {};
  const rawGenres = m.genres || m.genre;
  const genres = (Array.isArray(rawGenres) ? rawGenres : []).map(g => typeof g === 'string' ? g : g?.name).filter(Boolean).slice(0, 2);
  const line = [pick?.year || m.year, m.runtime, ...genres].filter(Boolean).join(" · ");
  return (
    <section data-testid="tonight-card" className="n-card section-gap relative overflow-hidden p-4" style={{ borderColor: `rgba(${r},${g},${b},.4)`, boxShadow: `0 24px 70px -50px rgba(${r},${g},${b},.9)` }}>
      <div className="pointer-events-none absolute inset-0" style={{ background: `radial-gradient(ellipse at 100% 0%,rgba(${r},${g},${b},.22),transparent 65%)` }} />
      <div className="relative">
        <div className="mb-3 flex flex-wrap items-center gap-2">
          <h2 className="n-h2 mr-1">{t("Tonight")}</h2>
          {[["any", "Any"], ["movie", "Movies"], ["series", "Series"]].map(([v, l]) => (
            <button key={v} type="button" data-active={type === v} onClick={() => setType(v)} className="n-chip !h-8 !px-3">{t(l)}</button>
          ))}
          <button type="button" data-active={short} onClick={() => setShort(!short)} className="n-chip !h-8 !px-3">≤ 90</button>
        </div>
        {pick ? <div className="flex gap-4">
          <MediaQuickActions item={{...m,...pick}}><Link to={`/title/${pick.type}/${pick.id}`} className="w-24 shrink-0 self-start"><img src={poster(pick.id)} alt={pick.name || m.name} loading="lazy" className="aspect-[2/3] w-full rounded-xl bg-[color:var(--c-181818)] object-cover" /></Link></MediaQuickActions>
          <div className="flex min-w-0 flex-1 flex-col">
            <div className="font-dot text-2xl leading-tight text-white">{pick.name || m.name}</div>
            <div className="mt-1 font-nmono text-[11px] text-neutral-400">{line}</div>
            {(m.description || m.overview) && <p className="mt-2 line-clamp-3 text-xs text-neutral-300">{m.description || m.overview}</p>}
            <div className="mt-auto flex items-center gap-2 pt-3">
              <Link to={`/title/${pick.type}/${pick.id}`} viewTransition className="n-btn-primary !min-h-[44px] flex-1">{t("Open")}</Link>
              <button type="button" aria-label={t("Shuffle")} onClick={() => { buzz(10); setSeed((s) => s + 1); }} className="n-icon-btn"><Shuffle size={16} /></button>
            </div>
          </div>
        </div> : <div className="flex items-center gap-3"><p role="status" className="flex-1 text-sm text-neutral-400">{t("No matching suggestion available. Try another filter or shuffle.")}</p><button type="button" aria-label={t("Shuffle")} onClick={() => setSeed(s => s + 1)} className="n-icon-btn"><Shuffle size={16} /></button></div>}
      </div>
    </section>
  );
}
