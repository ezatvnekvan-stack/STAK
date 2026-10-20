import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { api } from "../lib/api";
import { useT } from "../lib/i18n";

const poster = (id) => `https://images.metahub.space/poster/small/${id}/img`;
const sxe = (s, e) => `S${String(s).padStart(2, "0")}E${String(e || 0).padStart(2, "0")}`;
const dayKey = (d) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;

// Movie and episode releases from followed titles in the next 7 days.
export default function UpcomingStrip({ refreshVersion = 0 }) {
  const t = useT();
  const [items, setItems] = useState(null);
  useEffect(() => {
    let live = true;
    api.get("/media/upcoming", { params: { days: 7 } }).then(({ data }) => live && setItems(data.items || [])).catch(() => live && setItems([]));
    return () => { live = false; };
  }, [refreshVersion]);
  if (!items?.length) return null;
  const today = new Date(); today.setHours(0, 0, 0, 0);
  const tomorrow = new Date(today); tomorrow.setDate(today.getDate() + 1);
  const label = (iso) => {
    if (iso === dayKey(today)) return t("Today");
    if (iso === dayKey(tomorrow)) return t("Tomorrow");
    return new Date(`${iso}T12:00:00`).toLocaleDateString("en-US", { weekday: "short", day: "numeric" });
  };
  const airsToday = items.some((x) => x.date === dayKey(today));
  return (
    <section data-testid="home-upcoming" className="rise section-gap">
      <div className="mb-3 flex items-center justify-between">
        <h2 className="n-h2 flex items-center gap-2">{t("New releases")}{airsToday && <span className="red-dot blink" />}</h2>
        <span className="glyph">{items.length}</span>
      </div>
      <div className="no-scrollbar -mx-4 flex snap-x snap-proximity gap-3 overflow-x-auto scroll-px-4 px-4 pb-1">
        {items.slice(0, 14).map((e, i) => (
          <Link key={`${e.imdb_id}-${e.season}-${e.episode}-${i}`} to={e.type==='movie'?`/title/movie/${e.imdb_id}`:`/title/series/${e.imdb_id}?season=${Number(e.season)}&episode=${Number(e.episode)}`} viewTransition className="n-card flex w-60 shrink-0 snap-start items-center gap-3 p-2.5">
            <img src={e.poster || poster(e.imdb_id)} alt="" loading="lazy" className="aspect-[2/3] w-12 shrink-0 rounded-lg bg-[color:var(--c-181818)] object-cover" />
            <div className="min-w-0">
              <div className={`glyph mb-1 !text-[9px] ${e.date === dayKey(today) ? "!border-[color:var(--n-accent)] !text-[color:var(--n-accent)]" : ""}`}>{label(e.date)}</div>
              <div className="truncate text-sm text-white">{e.name}</div>
              <div className="font-nmono text-[10px] text-neutral-400">{e.type==='movie'?'Movie':sxe(e.season, e.episode)}</div>
            </div>
          </Link>
        ))}
      </div>
    </section>
  );
}
