import { cacheGet, cacheSet, DAY } from './metadata/cache';

export const GRAPHITE = '108 112 120';
const valid = value => typeof value === 'string' && /^\d{1,3} \d{1,3} \d{1,3}$/.test(value) && value.split(' ').every(n => Number(n) <= 255);
// Sample only 32×24 pixels. Ignore near-black/white and favour prevalent colour,
// then desaturate and cap brightness so atmosphere never competes with text.
export function paletteFromPixels(pixels) {
  const buckets = new Map();
  for (let i = 0; i < pixels.length; i += 4) {
    const rgb = [pixels[i], pixels[i + 1], pixels[i + 2]], hi = Math.max(...rgb), lo = Math.min(...rgb);
    if (pixels[i + 3] < 200 || hi < 24 || lo > 235) continue;
    const key = rgb.map(n => Math.floor(n / 32)).join(':');
    const bucket = buckets.get(key) || { weight: 0, rgb: [0, 0, 0] };
    const weight = 1 + (hi - lo) / 255;
    bucket.weight += weight; rgb.forEach((n, c) => { bucket.rgb[c] += n * weight; }); buckets.set(key, bucket);
  }
  const winner = [...buckets.values()].sort((a, b) => b.weight - a.weight)[0];
  if (!winner) return GRAPHITE;
  const rgb = winner.rgb.map(n => n / winner.weight), mean = rgb.reduce((a, b) => a + b) / 3;
  const muted = rgb.map(n => mean + (n - mean) * .65), scale = Math.min(1, 145 / Math.max(...muted));
  return muted.map(n => Math.round(n * scale)).join(' ');
}
const pending = new Map();
let active = 0;
const waiting = [];
async function sample(src) {
  if (active >= 4) await new Promise(resolve => waiting.push(resolve));
  else active++;
  try {
    return await new Promise(resolve => {
      const image = new Image();
      let timer;
      const finish = value => { clearTimeout(timer); image.onload = null; image.onerror = null; resolve(value); };
      image.crossOrigin = 'anonymous'; image.decoding = 'async'; image.referrerPolicy = 'no-referrer';
      image.onload = () => {
        try {
          const canvas = document.createElement('canvas'); canvas.width = 32; canvas.height = 24;
          const context = canvas.getContext('2d', { willReadFrequently: true });
          if (!context) return finish(GRAPHITE);
          context.drawImage(image, 0, 0, 32, 24);
          finish(paletteFromPixels(context.getImageData(0, 0, 32, 24).data));
        } catch { finish(GRAPHITE); } // CORS/decoding failures never hide artwork.
      };
      image.onerror = () => finish(GRAPHITE);
      timer = setTimeout(() => { image.src = ''; finish(GRAPHITE); }, 4000);
      image.src = src;
    });
  } finally { if (waiting.length) waiting.shift()(); else active--; }
}
export async function getArtworkPalette(src) {
  try {
    const url = new URL(src, window.location.origin);
    if (!['https:', 'http:'].includes(url.protocol) || url.username || url.password) return GRAPHITE;
    // No tokens, signed query strings or raw URLs are stored in the cache.
    let hash = 2166136261;
    for (const char of url.origin + url.pathname) hash = Math.imul(hash ^ char.charCodeAt(0), 16777619);
    const key = `artwork-palette:v2:${(hash >>> 0).toString(16)}`;
    const cached = await cacheGet(key);
    if (valid(cached)) return cached;
    if (pending.has(key)) return pending.get(key);
    if (waiting.length >= 4) return GRAPHITE;
    if (url.hostname === 'image.tmdb.org') url.pathname = url.pathname.replace(/\/t\/p\/[^/]+\//, '/t/p/w300/');
    const work = sample(url.href).then(value => cacheSet(key, value, value === GRAPHITE ? DAY : 7 * DAY)).catch(() => GRAPHITE).finally(() => pending.delete(key));
    pending.set(key, work);
    return work;
  } catch { return GRAPHITE; }
}
