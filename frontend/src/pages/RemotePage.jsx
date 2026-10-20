import { PageTitle } from "../components/Bits";
import NothingSelect from "../components/NothingSelect";
import { useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { Play, Pause, SkipForward, SkipBack, Square, RefreshCw } from 'lucide-react';
import { toast } from 'sonner';
import { api, errMsg } from '../lib/api';
import { useAccountQuery } from '../lib/useAccountQuery';
export default function RemotePage() {
  const [params] = useSearchParams();
  const [selected, setSelected] = useState(() => params.get('device') || ''), [busy, setBusy] = useState(false);
  const query = useAccountQuery('/jellyfin/sessions', { refetchInterval: () => document.visibilityState === 'visible' ? 15000 : false });
  const sessions = query.data?.sessions || [];
  const active = selected ? sessions.find(s => s.id === selected) : sessions.find(s => s.controllable);
  const command = async action => {
    if (!active?.controllable || query.error || busy) return;
    setBusy(true);
    try { await api.post('/jellyfin/playback', { session_id: active.id, action }); await query.refetch(); }
    catch (error) { toast.error(errMsg(error)); }
    finally { setBusy(false); }
  };
  return <div className="stak-screen stak-screen-remote"><PageTitle title="Jellyfin Remote" testId="remote-title"/><div className="n-card p-5">
    <div className="mb-3 flex items-center justify-between"><span className="n-label">Active devices</span><button className="n-icon-btn" disabled={query.isFetching} aria-label="Refresh devices" onClick={() => query.refetch()}><RefreshCw size={16} /></button></div>
    {query.error && <p role="alert" className="mb-3 text-xs text-red-400">{errMsg(query.error)}</p>}
    {query.isPending && <p role="status" className="mb-3 text-xs">Loading devices…</p>}
    <NothingSelect className="n-input mb-4" aria-label="Jellyfin playback device" value={active?.id || selected || ''} disabled={busy} onChange={e => setSelected(e.target.value)}><option value="">No device selected</option>{selected && !active && <option value={selected}>Selected device unavailable</option>}{sessions.map(s => <option key={s.id} value={s.id}>{s.device || s.client || 'Device'} · {s.user || 'User'}{!s.controllable ? ' · remote unavailable' : ''}</option>)}</NothingSelect>
    {selected && !active && !query.isPending && <p role="status" className="mb-3 text-xs text-neutral-400">The selected device is no longer connected. Refresh or choose another device.</p>}
    <p className="mb-5 text-sm text-neutral-300">{active?.title || 'Nothing playing'}</p>
    <div className="flex flex-wrap justify-center gap-3">{[['previous', SkipBack], ['play', Play], ['pause', Pause], ['next', SkipForward], ['stop', Square]].map(([action, Icon]) => <button key={action} disabled={!active?.controllable || busy || !!query.error} aria-label={action} className="n-icon-btn !h-12 !w-12 disabled:opacity-30" onClick={() => command(action)}><Icon size={19} /></button>)}</div>
    <p className="mt-4 text-xs text-neutral-500">Playback commands depend on the connected Jellyfin client's remote-control support.</p>
  </div></div>;
}
