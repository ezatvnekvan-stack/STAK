import { useEffect, useState } from "react";

// Average "colourful" pixel of an image (poster/backdrop) for glows. Returns [r,g,b] or null.
// Cross-origin images without CORS headers cannot be read: the caller falls back to neutral.
const cache = new Map();
export function getTint(url) {
  if (!url) return Promise.resolve(null);
  if (cache.has(url)) return cache.get(url);
  const p = new Promise((resolve) => {
    const img = new Image();
    img.crossOrigin = "anonymous";
    img.onload = () => {
      try {
        const c = document.createElement("canvas");
        c.width = 24; c.height = 24;
        const ctx = c.getContext("2d", { willReadFrequently: true });
        ctx.drawImage(img, 0, 0, 24, 24);
        const px = ctx.getImageData(0, 0, 24, 24).data;
        let r = 0, g = 0, b = 0, n = 0;
        for (let i = 0; i < px.length; i += 4) {
          const bright = (px[i] + px[i + 1] + px[i + 2]) / 3;
          if (px[i + 3] > 200 && bright > 35 && bright < 225 && Math.max(px[i], px[i + 1], px[i + 2]) - Math.min(px[i], px[i + 1], px[i + 2]) > 16) {
            r += px[i]; g += px[i + 1]; b += px[i + 2]; n++;
          }
        }
        resolve(n ? [Math.round(r / n), Math.round(g / n), Math.round(b / n)] : null);
      } catch { resolve(null); }
    };
    img.onerror = () => resolve(null);
    img.src = url;
  });
  cache.set(url, p);
  return p;
}

export function useTint(url, fallback = [60, 60, 60]) {
  const [tint, setTint] = useState(null);
  useEffect(() => {
    let live = true;
    setTint(null);
    getTint(url).then((v) => { if (live) setTint(v); });
    return () => { live = false; };
  }, [url]);
  return tint || fallback;
}
