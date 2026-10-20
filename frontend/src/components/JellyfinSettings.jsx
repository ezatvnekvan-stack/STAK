import { useEffect, useState } from "react";
import { Copy, Loader2, RefreshCw } from "lucide-react";
import { toast } from "sonner";
import { api, copyText, errMsg } from "../lib/api";
import { fmtDate } from "../lib/format";
import { useApp } from "../context/AppContext";

const TEMPLATE = '{"event":"{{NotificationType}}","item":"{{ItemId}}"}';

function Connected({ cfg, onSync, onDisconnect, onReconnect, busy }) {
  const hook = cfg.webhook_token ? `${window.location.origin}/api/jellyfin/webhook/${cfg.webhook_token}` : null;
  return (
    <div className="space-y-3">
      <div className="grid grid-cols-2 gap-2 font-nmono text-[11px]">
        <div><div className="n-label">Server</div><div data-testid="jellyfin-server" className="break-all text-white">{cfg.url}</div></div>
        <div><div className="n-label">User</div><div data-testid="jellyfin-user" className="text-white">{cfg.user_name}</div></div>
        <div><div className="n-label">Last sync</div><div data-testid="jellyfin-last-sync" className="text-white">{cfg.last_sync ? `${fmtDate(cfg.last_sync)} ${new Date(cfg.last_sync).toLocaleTimeString("en-US", { hour: "2-digit", minute: "2-digit" })}` : "—"}</div></div>
        <div><div className="n-label">Synced</div><div data-testid="jellyfin-synced-count" className="text-white">{cfg.last_stats ? `${cfg.last_stats.titles} titles · ${cfg.last_stats.episodes} eps` : "—"}</div></div>
      </div>
      {cfg.last_error && <div data-testid="jellyfin-error" className="text-xs text-[#ff4d55]">Last error: {cfg.last_error}</div>}
      <div className="flex gap-2">
        <button data-testid="jellyfin-sync-button" onClick={onSync} disabled={busy} className="n-btn-primary flex-1">{busy ? <Loader2 size={14} className="animate-spin" /> : <RefreshCw size={14} />} Sync now</button>
        <button data-testid="jellyfin-disconnect-button" disabled={busy} onClick={onDisconnect} className="n-btn-ghost !text-[#ff4d55]">Disconnect</button>
      </div>
      <button data-testid="jellyfin-reconnect-button" disabled={busy} onClick={onReconnect} className="n-btn-ghost w-full">Reconnect / replace session</button>
      <p className="text-xs text-neutral-400">A saved connection can expire or be revoked by Jellyfin. Reconnecting preserves your local lists and history.</p>
      {hook && <div className="rounded-2xl border border-dashed border-[color:var(--c-2a2a2a)] p-3">
        <div className="n-label mb-2">Live updates (optional)</div>
        <p className="mb-2 text-xs text-neutral-400">Jellyfin → Plugins → install <b>Webhook</b> → Add Generic Destination. Paste this URL, tick <b>Playback Stop</b> and <b>User Data Saved</b>, enable "Send All Properties" or use the template below. Use your laptop's LAN IP, not localhost.</p>
        <div className="mb-2 flex items-center gap-2">
          <div data-testid="jellyfin-webhook-url" className="min-w-0 flex-1 break-all font-nmono text-[10px] text-white">{hook}</div>
          <button data-testid="jellyfin-copy-webhook" onClick={() => copyText(hook).then(() => toast("Webhook URL copied"))} className="n-icon-btn !h-9 !w-9"><Copy size={13} /></button>
        </div>
        <div className="flex items-center gap-2">
          <div className="min-w-0 flex-1 break-all font-nmono text-[10px] text-neutral-500">{TEMPLATE}</div>
          <button data-testid="jellyfin-copy-template" onClick={() => copyText(TEMPLATE).then(() => toast("Template copied"))} className="n-icon-btn !h-9 !w-9"><Copy size={13} /></button>
        </div>
      </div>}
    </div>
  );
}

export default function JellyfinSettings() {
  const { key, refreshTracking } = useApp();
  const [cfg, setCfg] = useState(null);
  const [f, setF] = useState({ url: "", api_key: "", username: "", password: "" });
  const [mode, setMode] = useState("password");
  const [users, setUsers] = useState(null);
  const [busy, setBusy] = useState(null);
  const [editing, setEditing] = useState(false);
  const load = () => api.get("/jellyfin").then(({ data }) => setCfg(data)).catch(() => setCfg({ connected: false }));
  useEffect(() => { load(); }, [key]);

  const run = async (tag, fn) => {
    setBusy(tag);
    try { await fn(); } catch (e) { toast.error(errMsg(e)); } finally { setBusy(null); }
  };
  const test = () => run("test", async () => {
    const { data } = await api.post(mode === "password" ? "/jellyfin/login" : "/jellyfin/test", f);
    if (data.token) setF(old => ({ ...old, api_key: data.token, password: "" }));
    setUsers(data.users);
    toast.success(`Connected to ${data.server_name} (${data.version})`);
  });
  const save = (u) => run(u.id, async () => {
    await api.put("/jellyfin", { ...f, user_id: u.id, user_name: u.name });
    toast.success("Jellyfin connected locally");
    setUsers(null);
    setF({ url: "", api_key: "", username: "", password: "" });
    await load();
    setEditing(false);
    window.dispatchEvent(new Event('dmm-jellyfin-change'));
    refreshTracking().catch(() => {});
  });
  const sync = () => run("sync", async () => {
    await api.post("/jellyfin/sync");
    toast.success("Jellyfin connection refreshed");
    await load();
    refreshTracking().catch(() => {});
  });
  const disconnect = () => run("dc", async () => {
    await api.delete("/jellyfin");
    window.dispatchEvent(new Event('dmm-jellyfin-change'));
    await load();
    refreshTracking().catch(() => {});
  });

  return (
    <div data-testid="jellyfin-settings" className="n-card rise p-5">
      <div className="n-h2 mb-2">Jellyfin</div>
      <p className="mb-4 text-sm text-neutral-400">Track what you watch on Jellyfin. Watched status, progress and history sync into STAK, and marking watched here updates Jellyfin too.</p>
      {cfg?.connected && !editing ? (
        <Connected cfg={cfg} onSync={sync} onDisconnect={disconnect} busy={!!busy} onReconnect={() => { setF({ url: cfg.url, api_key: '', username: '', password: '' }); setUsers(null); setEditing(true); }} />
      ) : (
        <div className="space-y-2.5">
          {editing && <button type="button" disabled={!!busy} className="n-chip" onClick={() => { setEditing(false); setUsers(null); setF({ url: '', api_key: '', username: '', password: '' }); }}>Cancel · keep saved connection</button>}
          <input disabled={!!busy} data-testid="jellyfin-url-input" value={f.url} onChange={(e) => { setF({ ...f, url: e.target.value, api_key: "", password: "" }); setUsers(null); }} placeholder="http://192.168.1.50:8096" className="n-input font-nmono text-xs" autoCapitalize="off" />
          <div className="flex gap-2">{[["password", "User login"], ["key", "API key"]].map(([id, label]) => <button disabled={!!busy} type="button" className="n-chip" data-active={mode === id} key={id} onClick={() => { setMode(id); setUsers(null); }}>{label}</button>)}</div>
          {mode === "password" ? <><input disabled={!!busy} aria-label="Jellyfin username" autoComplete="username" className="n-input" placeholder="Username" value={f.username} onChange={e => { setF({ ...f, username: e.target.value }); setUsers(null); }} /><input disabled={!!busy} type="password" aria-label="Jellyfin password" autoComplete="current-password" className="n-input" placeholder="Password" value={f.password} onChange={e => { setF({ ...f, password: e.target.value }); setUsers(null); }} /></> : <input disabled={!!busy} data-testid="jellyfin-apikey-input" type="password" value={f.api_key} onChange={(e) => { setF({ ...f, api_key: e.target.value }); setUsers(null); }} placeholder="API key (Dashboard → API Keys)" className="n-input font-nmono" autoComplete="off" />}
          <button data-testid="jellyfin-test-button" onClick={test} disabled={!!busy || !f.url || (mode === "password" ? !f.username : !f.api_key)} className="n-btn-red w-full">
            {busy === "test" && <Loader2 size={14} className="animate-spin" />} Connect
          </button>
          <p className="text-xs text-neutral-400">HTTP sends credentials and media without encryption. Use it only on a trusted local network; prefer HTTPS.</p>
          {users && (
            <div data-testid="jellyfin-user-list" className="space-y-2 pt-1">
              <div className="n-label">Pick your user</div>
              {users.map((u, i) => (
                <button key={u.id} data-testid={`jellyfin-user-${i}`} onClick={() => save(u)} disabled={!!busy} className="n-btn-ghost w-full !justify-between">
                  {u.name} {busy === u.id && <Loader2 size={14} className="animate-spin" />}
                </button>
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  );
}
