import { Capacitor, registerPlugin } from '@capacitor/core';
import { getKey } from './api';
import { objectResponse } from './validation';
const Upload = registerPlugin('DMMTorrentUpload');
export async function uploadTorrent(file) {
  const key = getKey();
  if (!key) throw new Error('Configure your TorBox API key first.');
  if (!file || !/\.torrent$/i.test(file.name) || file.size < 1 || file.size > 5 * 1024 * 1024) throw new Error('Choose a .torrent file between 1 byte and 5 MiB.');
  let response;
  if (Capacitor.isNativePlatform()) {
    const bytes = new Uint8Array(await file.arrayBuffer());
    let binary = '';
    for (let i = 0; i < bytes.length; i += 8192) binary += String.fromCharCode(...bytes.subarray(i, i + 8192));
    response = await Upload.upload({ key, filename: file.name, data: btoa(binary) });
  } else {
    const controller = new AbortController(), timer = setTimeout(() => controller.abort(), 45000);
    try {
      const form = new FormData(); form.append('file', file);
      const r = await fetch('https://api.torbox.app/v1/api/torrents/createtorrent', { method: 'POST', headers: { Authorization: `Bearer ${key}` }, body: form, signal: controller.signal });
      if (!r.ok) throw new Error(`TorBox upload failed (HTTP ${r.status}).`);
      response = await r.json();
    } finally { clearTimeout(timer); }
  }
  const envelope = objectResponse(response, 'TorBox');
  if (envelope.success !== true) throw new Error('TorBox rejected the file. Check account limits and torrent validity.');
  return envelope.data;
}
