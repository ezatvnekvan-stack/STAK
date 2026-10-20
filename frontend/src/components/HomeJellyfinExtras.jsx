import { useNavigate } from "react-router-dom";
import { errMsg } from "../lib/api";
import { useAccountQuery } from '../lib/useAccountQuery';
import JellyfinDetailsLink from "./JellyfinDetailsLink";
import JellyfinImage from "./JellyfinImage";
export default function HomeJellyfinExtras({ section }) {
  const navigate = useNavigate();
  const cfg = useAccountQuery('/jellyfin');
  const query = useAccountQuery('/jellyfin/recent', { enabled: !!cfg.data?.connected });
  const items = query.data?.items || [];
  const on = id => section === id;
  if (!cfg.data?.connected) return null;
  if (query.error) return <p role="alert" className="mb-4 text-xs text-neutral-400">{errMsg(query.error)} <button className="n-chip" onClick={() => query.refetch()}>Retry</button></p>;
  if (query.isPending) return <div className="dot-skel h-32 mb-6 rounded-xl" aria-label="Loading Jellyfin titles" />;
  const row = (label, list) => <section className="mb-6"><h2 className="n-h2 mb-3">{label}</h2><div className="no-scrollbar flex gap-3 overflow-x-auto">{list.map(item => <JellyfinDetailsLink key={item.Id} item={item} className="w-28 shrink-0"><JellyfinImage item={item} width={400} className="aspect-[2/3] w-full rounded-xl object-cover" /><p className="mt-2 truncate text-xs">{item.Name}</p></JellyfinDetailsLink>)}</div>{!list.length && <p className="text-xs text-neutral-400">Connect Jellyfin in Settings to browse available titles.</p>}</section>;
  return <>{on('recent') && row('Recently added', items)}{on('available') && row('Available to watch', items.slice(0,15))}{on('new_seasons') && row('Recently added series', items.filter(x => x.Type === 'Series'))}{on('surprise') && items.length > 0 && <button className="n-chip mb-6" onClick={() => navigate(`/jellyfin/item/${items[Math.floor(Math.random()*items.length)].Id}`)}>Surprise me →</button>}</>;
}
