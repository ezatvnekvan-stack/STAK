import { Dialog, DialogContent, DialogDescription, DialogTitle } from "./ui/dialog";
import { useState } from "react";
import { api, errMsg } from "../lib/api";
import { useApp } from "../context/AppContext";

const readableSize = (bytes) => `${(Number(bytes || 0) / 1073741824).toFixed(2)} GiB`;
export default function MediaToolkit() {
  const { key } = useApp();
  const [health, setHealth] = useState(null);
  const [overview, setOverview] = useState(null);
  const [sessions, setSessions] = useState(null);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);
  const [busySession, setBusySession] = useState("");
  const [resume, setResume] = useState(null);
  const [duplicates, setDuplicates] = useState(null);
  const [deleteGroup, setDeleteGroup] = useState(null);
  const command = async (id, action) => {
    setBusySession(id); setError("");
    try {
      await api.post("/jellyfin/playback", { session_id: id, action });
      const { data } = await api.get("/jellyfin/sessions");
      setSessions(data.sessions);
    } catch (e) { setError(errMsg(e)); }
    finally { setBusySession(""); }
  };
  const refresh = async () => {
    setLoading(true); setError("");
    try {
      const h = await api.get("/system/health"); setHealth(h.data);
      {
        const [o, j] = await Promise.allSettled([
          key ? api.get("/media/overview") : Promise.resolve({ data: null }), api.get("/jellyfin/sessions")
        ]);
        if (o.status === "fulfilled") setOverview(o.value.data);
        else setError(errMsg(o.reason));
        if (j.status === "fulfilled") setSessions(j.value.data.sessions);
        const [r, d] = await Promise.allSettled([
          api.get("/media/continue"), key ? api.get("/media/duplicates") : Promise.resolve({ data: null })
        ]);
        if (r.status === "fulfilled") setResume(r.value.data.items);
        if (d.status === "fulfilled") setDuplicates(d.value.data);
        else setError(errMsg(d.reason));
      }
    } catch (e) { setError(errMsg(e)); }
    finally { setLoading(false); }
  };
  const removeDuplicates = async () => {
    if (!deleteGroup) return;
    setLoading(true); setError('');
    try {
      const items = [...deleteGroup.items].sort((a,b) => Number(b.download_finished) - Number(a.download_finished) || Number(b.progress || 0) - Number(a.progress || 0) || new Date(b.created_at) - new Date(a.created_at));
      const { data } = await api.post('/torbox/control', { torrent_ids: items.slice(1).map(x => x.id), operation: 'delete' });
      const failures = data.filter(x => !x.ok);
      if (failures.length) setError(`${failures.length} duplicate(s) could not be removed. Refresh and retry.`);
      const { data: groups } = await api.get('/media/duplicates'); setDuplicates(groups); setDeleteGroup(null);
    } catch (e) { setError(errMsg(e)); } finally { setLoading(false); }
  };
  return <section className="n-card p-5" data-testid="media-toolkit">
    <div className="flex items-center justify-between gap-3">
      <div><div className="n-label mb-1">Media toolkit</div>
      <h2 className="n-h2">Server & Library</h2></div>
      <button className="n-btn-ghost" onClick={refresh} disabled={loading}>
        {loading ? "Loading…" : "Refresh"}
      </button>
    </div>
    <p className="mt-2 text-sm text-neutral-400">Device and provider diagnostics. Playback controls require a compatible Jellyfin client.</p>
    {error && <p role="alert" className="mt-3 text-sm text-red-400">{error}</p>}
    {health && <div className="mt-4 grid grid-cols-2 gap-2">
      <div className="n-card p-3"><div className="n-label">API</div><div>{health.api}</div></div>
      <div className="n-card p-3"><div className="n-label">MongoDB</div><div>{health.mongodb}</div></div>
    </div>}
    {overview && <div className="mt-3 grid grid-cols-2 gap-2 sm:grid-cols-3">
      <div className="n-card p-3"><div className="n-label">TorBox torrents</div><div>{overview.torbox.torrents}</div></div>
      <div className="n-card p-3"><div className="n-label">TorBox total size</div><div>{readableSize(overview.torbox.total_bytes)}</div></div>
      <div className="n-card p-3"><div className="n-label">Watchlist</div><div>{overview.watchlist}</div></div>
    </div>}
    {sessions && <div className="mt-4"><div className="n-label mb-2">Jellyfin sessions ({sessions.length})</div>
      {sessions.length === 0 ? <p className="text-sm text-neutral-400">No active sessions.</p> :
        sessions.map((s, i) => <div key={s.id || i} className="border-t border-neutral-800 py-2 text-sm">
          <div>{s.device || s.client || "Unknown device"}</div>
          <div className="text-neutral-400">{s.title || "Not playing"}{s.is_paused ? " · Paused" : ""}</div>
          {s.id && s.title && <div className="flex flex-wrap gap-2 mt-2">
            {["previous", s.is_paused ? "play" : "pause", "next", "stop"].map(action =>
              <button key={action} className="n-btn-ghost text-xs" disabled={!!busySession}
                onClick={() => command(s.id, action)} aria-label={`${action} ${s.device || "device"}`}>
                {busySession === s.id ? "Working…" : action}
              </button>)}
          </div>}
        </div>)}
    </div>}
    {resume && <div className="mt-4"><div className="n-label mb-2">Continue Watching ({resume.length})</div>
      {resume.length === 0 ? <p className="text-sm text-neutral-400">No unfinished titles synced from Jellyfin.</p> :
        resume.slice(0, 8).map((item, i) => <div key={item.imdb_id + ":" + i} className="border-t border-neutral-800 py-2 text-sm">
          <div>{item.name || item.series_name || item.imdb_id}</div>
          <div className="text-neutral-400">{Number(item.progress || 0).toFixed(0)}% watched</div>
        </div>)}
    </div>}
    {duplicates && <div className="mt-4"><div className="n-label mb-2">Duplicate TorBox hashes</div>
      <p className="text-sm text-neutral-400">{duplicates.duplicate_groups} duplicate groups detected.</p>
      {(duplicates.groups || []).slice(0, 5).map(group => <div key={group.hash} className="border-t border-neutral-800 py-2 text-sm">
        {group.items.map((item, i) => <div key={item.id || i}>{item.name || group.hash}</div>)}<button className="n-chip mt-2" disabled={loading} onClick={() => setDeleteGroup(group)}>Review duplicates</button>
      </div>)}
    </div>}
    {deleteGroup && <Dialog open onOpenChange={open => { if (!open && !loading) setDeleteGroup(null); }}><DialogContent><DialogTitle>Remove duplicate torrents?</DialogTitle><DialogDescription>This deletes {deleteGroup.items.length - 1} duplicate(s) from TorBox and keeps one copy, preferring a finished download. Check the list before continuing.</DialogDescription>{deleteGroup.items.map(item => <p key={item.id} className="text-xs">#{item.id} · {item.name}</p>)}<button className="n-btn-destructive" disabled={loading} onClick={removeDuplicates}>{loading ? 'Working…' : 'Delete duplicates'}</button><button className="n-btn-ghost" disabled={loading} onClick={() => setDeleteGroup(null)}>Cancel</button></DialogContent></Dialog>}
    {!key && <p className="mt-3 text-sm text-neutral-400">Connect TorBox to view library statistics.</p>}
  </section>;
}
