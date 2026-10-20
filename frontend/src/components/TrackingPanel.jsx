import ProgressIndicator from './ProgressIndicator';
import { useState } from "react";
import { Check, ExternalLink, Loader2, RotateCcw, SkipForward } from "lucide-react";
import { toast } from "sonner";
import { api, IS_CAPACITOR_OFFLINE, errMsg, openExternal } from "../lib/api";
import { fmtDate } from "../lib/format";
import { useApp } from "../context/AppContext";
import { nextUnwatched, episodesOf } from '../lib/episodes';

const jfLink = (url, id) => `${url}/web/#/details?id=${id}`;

export const nextUp = (videos, watched) => {
  return nextUnwatched(videos, watched);
};

export default function TrackingPanel({ meta, data, ep, onChanged, onJump, compact = false, hidePrimary = false }) {
  const { key, refreshTracking } = useApp();
  const [busy, setBusy] = useState(null);
  if ((!key && !IS_CAPACITOR_OFFLINE) || !data) return null;
  const isSeries = meta.type === "series";
  const item = data.item;
  const watched = Object.fromEntries((Array.isArray(data.episodes) ? data.episodes : []).map((d) => [`${d.season}:${d.episode}`, d]));
  const cur = isSeries ? watched[`${ep.s}:${ep.e}`] : item;
  const next = isSeries ? nextUp(meta.videos || [], watched) : null;
  const seasonEps = episodesOf(meta.videos || [], ep.s).map((v) => [v.season, v.episode]);

  const mark = async (tag, played, episodes = []) => {
    setBusy(tag);
    try {
      const { data: r } = await api.post("/tracking/mark", { imdb_id: meta.imdb_id || meta.id, type: meta.type, name: meta.name, played, episodes });
      toast.success(`${played ? "Marked watched" : "Marked unwatched"}${r.jellyfin_updated ? ` · synced to Jellyfin (${r.jellyfin_updated})` : ""}`);
      if (r.jellyfin_failed) toast.error(`${r.jellyfin_failed} item(s) failed to update in Jellyfin`);
      await onChanged();
      refreshTracking().catch(() => {});
    } catch (e) {
      toast.error(errMsg(e));
    } finally {
      setBusy(null);
    }
  };

  const status = cur?.played ? `Watched${cur.last_played ? ` · ${fmtDate(cur.last_played)}` : ""}${cur.play_count > 1 ? ` · ${cur.play_count}×` : ""}`
    : cur?.progress > 0 ? `${Math.round(cur.progress)}% watched` : "Not watched";
  const label = isSeries ? `S${String(ep.s).padStart(2, "0")}E${String(ep.e).padStart(2, "0")}` : "Movie";

  if (compact) return <button data-testid="compact-mark-watched" disabled={!!busy} onClick={() => mark("one", !cur?.played, isSeries ? [[ep.s,ep.e]] : [])} className="n-chip !min-h-0 !px-3 !py-1.5 !text-[10px]">{cur?.played ? "✓ Watched" : "✓ Mark watched"}</button>;

  return (
    <div data-testid="tracking-panel" className="n-card rise mb-5 p-4">
      <div className="mb-3 flex items-center justify-between gap-2">
        <div className="min-w-0">
          <div className="n-label mb-1">Tracking · {label}</div>
          <div data-testid="tracking-status" className={`font-dot text-xl ${cur?.played ? "text-[#10B981]" : "text-white"}`}>{status}</div>
          {isSeries && item?.unplayed != null && <div className="font-nmono text-[10px] text-neutral-500">{item.unplayed} unwatched episodes in Jellyfin</div>}
        </div>
        {item?.in_jellyfin && data.jellyfin_url && (
          <button data-testid="open-in-jellyfin-button" onClick={() => openExternal(jfLink(data.jellyfin_url, item.jf_id))} className="glyph shrink-0 !border-[#AA5CC3]/60 !text-[#c58be0]">
            Jellyfin <ExternalLink size={10} />
          </button>
        )}
      </div>
      {cur?.progress > 0 && !cur.played && (
        <ProgressIndicator value={cur.progress} label="Playback progress" className="mb-3"/>
      )}
      <div className="flex flex-wrap gap-2">
        {!hidePrimary && (cur?.played ? (
          <button data-testid="mark-unwatched-button" disabled={!!busy} onClick={() => mark("one", false, isSeries ? [[ep.s, ep.e]] : [])} className="n-btn-ghost !min-h-[44px]">
            {busy === "one" ? <Loader2 size={14} className="animate-spin" /> : <RotateCcw size={14} />} Unwatch
          </button>
        ) : (
          <button data-testid="mark-watched-button" disabled={!!busy} onClick={() => mark("one", true, isSeries ? [[ep.s, ep.e]] : [])} className="n-btn-primary !min-h-[44px]">
            {busy === "one" ? <Loader2 size={14} className="animate-spin" /> : <Check size={14} />} Mark watched
          </button>
        ))}
        {isSeries && (
          <button data-testid="mark-season-watched-button" disabled={!!busy} onClick={() => mark("season", true, seasonEps)} className="n-btn-ghost !min-h-[44px]">
            {busy === "season" ? <Loader2 size={14} className="animate-spin" /> : <Check size={14} />} Season {ep.s}
          </button>
        )}
        {next && (
          <button data-testid="next-up-button" onClick={() => onJump(next.season, next.episode)} className="n-btn-ghost !min-h-[44px] !border-[rgb(var(--n-accent-rgb)/0.6)]">
            <SkipForward size={14} /> Next S{String(next.season).padStart(2, "0")}E{String(next.episode).padStart(2, "0")}
          </button>
        )}
      </div>
    </div>
  );
}
