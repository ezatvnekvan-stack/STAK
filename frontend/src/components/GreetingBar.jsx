import { Link } from 'react-router-dom';
import { useAccountQuery } from '../lib/useAccountQuery';
import { errMsg } from '../lib/api';
export default function GreetingBar() {
  const cfg = useAccountQuery('/jellyfin');
  const count = useAccountQuery('/jellyfin/counts', { enabled: !!cfg.data?.connected, staleTime: 60000 });
  const error = cfg.error || count.error;
  return <section data-testid="library-summary" className="library-summary mb-4">
    <Link to="/library" className="flex min-h-11 items-center justify-between gap-3"><span className="n-label">Jellyfin library</span><span className="font-nmono text-[11px] text-neutral-300">{error ? 'Count unavailable' : cfg.isPending || (cfg.data?.connected && count.isPending) ? 'Loading…' : !cfg.data?.connected ? 'Not connected' : `${count.data?.movies ?? '—'} movies · ${count.data?.series ?? '—'} shows`}</span></Link>
    {error && <p role="alert" className="text-xs text-neutral-400">{errMsg(error)} <button className="stak-button stak-button--link" onClick={() => { cfg.refetch(); count.refetch(); }}>Retry</button></p>}
  </section>;
}
