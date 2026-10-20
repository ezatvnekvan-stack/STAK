import { useEffect, useMemo, useRef, useState } from 'react';
import { CapacitorHttp } from '@capacitor/core';
import { validateProviderURL } from '../lib/validation';
import { getKey } from '../lib/api';
import { readJellyfinConnection } from '../lib/jellyfinConnection';
import { jellyfinHeaders } from '../lib/jellyfinAuth';
import { redirectTarget } from '../lib/transport';

// Jellyfin artwork is private: retrieve through native HTTP and never expose the token in an img URL.
const cache = new Map();
let generation = 0;
window.addEventListener('dmm-jellyfin-change', () => {
  generation++;
  for (const src of cache.values()) if (src.startsWith('blob:')) URL.revokeObjectURL(src);
  cache.clear(); pending.clear();
});
const MAX_CACHE = 75;
const pending = new Map();
const workQueue = [];
let activeRequests = 0;
function releaseRequest() { activeRequests--; pump(); }
function pump() {
  while (activeRequests < 4 && workQueue.length) {
    const { work, resolve, reject } = workQueue.shift(); activeRequests++;
    Promise.resolve().then(work).then(resolve, reject).finally(releaseRequest);
  }
}
function limited(work) { return new Promise((resolve, reject) => { workQueue.push({work,resolve,reject}); pump(); }); }
async function credentials() {
  return readJellyfinConnection(getKey() || 'guest');
}
async function imageSource(cfg, id, type, width) {
  const base = validateProviderURL(cfg.url).replace(/\/+$/, '');
  const url = `${base}/Items/${encodeURIComponent(id)}/Images/${type}?maxWidth=${width}&quality=85`;
  const key = `${base}|${id}|${type}|${width}|${cfg.user_id}|${generation}`;
  if (cache.has(key)) { const src=cache.get(key); cache.delete(key); cache.set(key,src); return src; }
  const requestKey = key;
  if (pending.has(requestKey)) return pending.get(requestKey);
  const started = generation;
  const work = limited(async () => {
  if (started !== generation) return null;
  const headers = { ...jellyfinHeaders(cfg.api_key), Accept: 'image/*' };
  let current = url, res;
  for (let hop = 0; hop <= 3; hop++) {
    res = await CapacitorHttp.get({ url: current, headers, responseType: 'blob', connectTimeout: 12000, readTimeout: 20000, disableRedirects: true });
    if (![301,302,303,307,308].includes(res.status)) break;
    if (hop === 3) return null;
    const location = Object.entries(res.headers || {}).find(([name]) => name.toLowerCase() === 'location')?.[1];
    current = redirectTarget(current, location, headers);
  }
  if (started !== generation) return null;
  if (res.status < 200 || res.status >= 300) return null;
  const contentType = String(res.headers?.['content-type'] || res.headers?.['Content-Type'] || 'image/jpeg').split(';')[0];
  // Capacitor native HTTP encodes blob responses as base64; a browser may return Blob instead.
  let src = null;
  if (typeof res.data === 'string') {
    const raw = res.data.replace(/^data:[^,]*,/, '');
    if (raw && /^[A-Za-z0-9+/=\s]+$/.test(raw)) src = `data:${contentType};base64,${raw}`;
  } else if (res.data instanceof Blob) {
    src = URL.createObjectURL(res.data);
  }
  if (src) { cache.set(key, src); if (cache.size > MAX_CACHE) { const oldest = cache.keys().next().value; const old = cache.get(oldest); if (old?.startsWith('blob:')) URL.revokeObjectURL(old); cache.delete(oldest); } }
  return src;
  });
  pending.set(requestKey, work);
  try { return await work; } finally { if (pending.get(requestKey) === work) pending.delete(requestKey); }
}
export default function JellyfinImage({ item, className = '', width = 640, landscape = false }) {
  const [image, setImage] = useState(null);
  const [epoch, setEpoch] = useState(generation);
  useEffect(() => { const changed = () => { setImage(null); setEpoch(generation); }; window.addEventListener('dmm-jellyfin-change', changed); return () => window.removeEventListener('dmm-jellyfin-change', changed); }, []);
  const ref = useRef(null);
  const [visible, setVisible] = useState(typeof IntersectionObserver === 'undefined');
  useEffect(() => { if (visible || !ref.current || typeof IntersectionObserver === 'undefined') return; const observer = new IntersectionObserver(entries => { if (entries.some(x => x.isIntersecting)) { setVisible(true); observer.disconnect(); } }, { rootMargin: '300px' }); observer.observe(ref.current); return () => observer.disconnect(); }, [visible]);
  const ids = useMemo(() => [...new Set((item?.Type === 'Episode' && !landscape ? [item?.SeriesId, item?.Id] : [item?.Id, item?.SeriesId]).filter(Boolean))], [item?.Type, item?.SeriesId, item?.Id, landscape]);
  const idsKey = ids.join('|');
  useEffect(() => {
    if (!visible) return;
    let live = true;
    (async () => {
      try {
        const cfg = await credentials();
        if (!cfg) return;
        const preferred = landscape && item?.Type !== 'Episode' ? ['Backdrop', 'Primary'] : ['Primary', 'Backdrop'];
        for (const id of idsKey.split('|').filter(Boolean)) for (const type of preferred) {
          try { const image = await imageSource(cfg, id, type, width); if (image) { if (live) setImage({key:idsKey,epoch,src:image}); return; } }
          catch { /* Try the next image type or item */ }
        }
      } catch { /* A missing image must not crash React */ }
    })();
    return () => { live = false; };
  }, [idsKey, item?.Type, landscape, width, visible, epoch]); // Stable primitives avoid repeated native requests.
  const src=image?.key===idsKey && image?.epoch===epoch ? image.src : '';
  return src ? <img ref={ref} src={src} alt={item?.SeriesName || item?.Name || ''} className={className} loading="lazy" decoding="async" onError={() => setImage(null)} />
    : <div ref={ref} className={`${className} dot-bg bg-[color:var(--c-222222)]`} aria-label={item?.Name || ''} />;
}
