import { useQueryClient } from "@tanstack/react-query";
import { useState } from 'react';
import { Capacitor } from '@capacitor/core';
import { Share } from '@capacitor/share';
import { toast } from 'sonner';
import { api, copyText, errMsg, getKey, KEY_STORAGE, IS_CAPACITOR_OFFLINE } from '../lib/api';
import { useApp } from '../context/AppContext';
import { encryptBackup, decryptBackup } from '../lib/encryptedBackup';
import { metadataContext } from '../lib/metadata/settings';
import { metadataChanged } from '../lib/metadata/runtime';
import { saveCredential } from '../lib/secureStorage';
import { readJellyfinConnection, saveJellyfinConnection } from '../lib/jellyfinConnection';
import { validateProviderURL } from '../lib/validation';
export default function BackupSettings() {
  const [text, setText] = useState(''), [busy, setBusy] = useState(false);
  const [includeCredentials, setIncludeCredentials] = useState(false), [passphrase, setPassphrase] = useState('');
  const queryClient = useQueryClient();
  const { refreshWatchlist, refreshTracking, setKey } = useApp();
  if (!IS_CAPACITOR_OFFLINE) return null;
  const run = async work => { setBusy(true); try { await work(); } catch (e) { toast.error(errMsg(e)); } finally { setBusy(false); } };
  const exportData = () => run(async () => {
    setText('');
    const { data } = await api.get('/backup');
    if (!includeCredentials) { setText(JSON.stringify(data, null, 2)); return; }
    const context = await metadataContext({ headers: { 'X-TorBox-Key': getKey() } });
    const jellyfin = await readJellyfinConnection(getKey()), { data: indexers } = await api.get('/indexers');
    setText(JSON.stringify(await encryptBackup({ ...data, credentials: { torbox: getKey() || '', ...context.credentials, jellyfin, indexers } }, passphrase)));
    setPassphrase('');
  });
  const importData = file => run(async () => {
    if (!file || file.size > 3 * 1024 * 1024) throw new Error('Choose a JSON backup smaller than 3 MiB.');
    let doc = JSON.parse(await file.text());
    const encrypted = doc?.v === 1;
    if (encrypted) { doc = await decryptBackup(doc, passphrase); setPassphrase(''); }
    const credentials = encrypted ? doc.credentials : null;
    if (credentials) {
      for (const name of ['torbox', 'tmdb', 'omdb']) if (typeof credentials[name] !== 'string' || credentials[name].length > 4096 || /[\r\n]/.test(credentials[name])) throw new Error('Invalid backup credentials.');
      if (credentials.jellyfin && (typeof credentials.jellyfin.api_key !== 'string' || !credentials.jellyfin.api_key || /[\r\n]/.test(credentials.jellyfin.api_key) || !credentials.jellyfin.user_id)) throw new Error('Invalid Jellyfin backup connection.');
      if (credentials.jellyfin) validateProviderURL(credentials.jellyfin.url);
      if (!Array.isArray(credentials.indexers) || credentials.indexers.length > 5000) throw new Error('Invalid backup indexers.');
      for (const indexer of credentials.indexers) { validateProviderURL(indexer?.url); if (!['torznab', 'stremio'].includes(indexer.kind)) throw new Error('Invalid backup indexer.'); }
    }
    const targetKey = credentials?.torbox || getKey(), config = { headers: { 'X-TorBox-Key': targetKey || '' } };
    const { data } = await api.post('/backup', doc, config);
    if (credentials) {
      for (const provider of ['tmdb', 'omdb']) if (credentials[provider]) await api.put('/metadata/credentials', { provider, value: credentials[provider] }, config);
      if (credentials.jellyfin) await saveJellyfinConnection({ ...credentials.jellyfin, is_admin: false });
      const { data: existing } = await api.get('/indexers', config);
      for (const indexer of credentials.indexers) if (!existing.some(saved => saved.url === indexer.url && saved.kind === indexer.kind)) await api.post('/indexers', indexer, config);
      if (credentials.torbox) await saveCredential(KEY_STORAGE, credentials.torbox);
      metadataChanged();
    }
    await Promise.all([refreshWatchlist(), refreshTracking(), queryClient.invalidateQueries({ queryKey: ["account"] })]); toast.success(`Imported ${data.added} new records. Existing records were preserved.`);
    if (credentials?.torbox) setKey(credentials.torbox);
  });
  const send = () => run(async () => {
    if (text.length > 500000) throw new Error('This backup is too large for Android sharing. Use Copy backup and save the text in a file.');
    if (Capacitor.isNativePlatform()) await Share.share({ title: 'STAK backup', text });
    else { await copyText(text); toast.success('Backup copied'); }
  });
  return <section className="n-card p-5 space-y-3"><h2 className="n-h2">Local backup</h2><p className="text-xs text-neutral-400">Export this account’s watchlist, manual tracking, home layout and saved magnet lists. Credentials and indexer URLs are excluded unless encrypted export is selected. Backups contain personal viewing data; share them carefully.</p><label className="flex min-h-11 items-center gap-2 text-sm"><input type="checkbox" checked={includeCredentials} disabled={busy} onChange={e => { setIncludeCredentials(e.target.checked); setText(''); }} />Include credentials (encrypted)</label><label className="block text-xs">Passphrase (encrypted export/import)<input type="password" autoComplete="off" maxLength={1024} value={passphrase} disabled={busy} onChange={e => setPassphrase(e.target.value)} className="n-input mt-2" /></label><button className="n-chip" disabled={busy} onClick={exportData}>Prepare backup</button>{text && <><textarea readOnly aria-label="Backup JSON" className="n-input !h-40 font-nmono text-xs" value={text} /><div className="flex gap-2"><button className="n-chip" disabled={busy} onClick={() => run(async () => { await copyText(text); toast.success('Backup copied'); })}>Copy backup</button><button className="n-chip" disabled={busy} onClick={send}>Share backup</button></div></>}<label className="block text-xs">Import backup (merge missing records)<input disabled={busy} type="file" accept=".json,application/json" className="mt-2 block w-full" onChange={e => { const file = e.target.files?.[0]; if (file) importData(file); e.target.value = ''; }} /></label>{busy && <p role="status" className="text-xs">Working…</p>}</section>;
}
