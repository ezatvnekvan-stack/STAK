import ProgressIndicator from './ProgressIndicator';
import JellyfinDetailsLink from "./JellyfinDetailsLink";
import JellyfinImage from "./JellyfinImage";
import { Link } from "react-router-dom";
import { errMsg } from "../lib/api";
import { useAccountQuery } from '../lib/useAccountQuery';
import { useT } from "../lib/i18n";

const minutesLeft = (item) => {
  const total = Number(item.RunTimeTicks), pos = Number(item.UserData?.PlaybackPositionTicks);
  if (!total || !pos || pos >= total) return null;
  return Math.max(1, Math.round((total - pos) / 6e8));
};


export default function JellyfinHub() {
  const t = useT();
  const cfg = useAccountQuery('/jellyfin');
  const query = useAccountQuery('/jellyfin/home', { enabled: !!cfg.data?.connected });
  const data = query.data;
  const error = query.error ? errMsg(query.error) : '';
  if (cfg.error) return <div role="alert" className="n-card mb-6 space-y-2 p-4 text-sm"><p>Jellyfin connection could not load: {errMsg(cfg.error)}</p><button className="n-chip" onClick={() => cfg.refetch()}>Retry connection</button> <Link className="n-chip" to="/account?section=jellyfin">Connection settings</Link></div>;
  if (!cfg.data?.connected && !cfg.isPending) return <p className="mb-6 text-xs text-neutral-400">Connect Jellyfin to resume watching. <Link className="n-chip" to="/account?section=jellyfin">Connect Jellyfin</Link></p>;
  const seen = new Set();
  const entries = [...(data?.continue_watching || []).map(item => ({item, kind:"Continue"})),
    ...(data?.next_up || []).map(item => ({item, kind:"Next Up"}))]
    .filter(({item}) => {if(!item?.Id || seen.has(item.SeriesId || item.Id)) return false; seen.add(item.SeriesId || item.Id); return true;});
  return <section data-testid="jellyfin-unified-queue" className="rise section-gap">
    <div className="mb-3 flex items-center justify-between gap-2">
      <h2 className="n-h2">CONTINUE WATCHING</h2>
      <Link className="shrink-0 font-nmono text-[10px] text-neutral-400" to="/library">JELLYFIN →</Link>
    </div>
    {error ? <div role="alert" className="text-xs text-neutral-400 space-y-2"><p>{error}</p><button className="n-chip" onClick={() => query.refetch()}>Retry</button> <Link className="n-chip" to="/account?section=jellyfin">Reconnect</Link></div> : !data ? <div className="dot-skel h-36 rounded-2xl" /> : !entries.length ? <p className="text-xs text-neutral-500">{t("Nothing to continue yet in Jellyfin.")}</p> :
      <div>{['Continue','Next Up'].map(group=>entries.some(entry=>entry.kind===group) && <div className="home-queue-group" key={group}><h3 className="queue-subheading">{group==='Continue'?'Continue watching':'Next up'}<span>{entries.filter(entry=>entry.kind===group).length}</span></h3><div className="no-scrollbar -mx-4 flex snap-x snap-proximity gap-3 overflow-x-auto scroll-px-4 px-4 pb-2">
        {entries.filter(entry=>entry.kind===group).map(({item,kind}) => {
          const left = minutesLeft(item);
          const saved = item.UserData?.PlayedPercentage;
          const percent = saved != null && Number.isFinite(Number(saved)) ? Number(saved) : Number(item.RunTimeTicks) > 0 ? (Number(item.UserData?.PlaybackPositionTicks) || 0) / Number(item.RunTimeTicks) * 100 : 0;
          const pct = Math.min(100, Math.max(0, percent));
          return <JellyfinDetailsLink item={item} key={`${kind}-${item.Id}`} className="n-card w-[184px] shrink-0 snap-start overflow-hidden sm:w-[220px]" aria-label={`${kind}: ${item.SeriesName||item.Name}`}>
            <div className="relative aspect-video overflow-hidden bg-neutral-900">
              <JellyfinImage item={item} landscape className="h-full w-full object-cover" />
              <span className="absolute bottom-2 left-2 rounded-full bg-black/75 px-2 py-1 font-nmono text-[9px] uppercase text-white">{kind === 'Next Up' ? 'NEXT UP' : 'RESUME'}</span>
            </div>
            <div className="p-2.5">
              <div className="truncate text-xs font-semibold text-white">{item.SeriesName || item.Name}</div>
              <div className="home-queue-meta mt-1 truncate font-nmono text-[10px] text-neutral-400">
                {item.Type === "Episode" ? `S${item.ParentIndexNumber??0} E${item.IndexNumber??0}` : left ? `${left} min left` : ''}
              </div>
              {kind==='Continue' && pct > 0 && <ProgressIndicator value={pct} variant="continuous" label={`${item.SeriesName || item.Name} playback`} className="mt-2"/>}
            </div>
          </JellyfinDetailsLink>;
        })}
      </div></div>)}</div>}
    {data?.errors?.map((failure, i) => <p key={i} role="status" className="mt-2 text-xs text-neutral-400">{failure.section}: {failure.error}</p>)}
  </section>;
}
