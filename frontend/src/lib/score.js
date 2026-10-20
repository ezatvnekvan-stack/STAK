import {releaseMatch} from './releaseMatch';
// "Best pick" scoring for release lists. Heuristic: based on names, seeders and cache state only.
const RES_RANK = { "4K": 4, "1080p": 3, "720p": 2, SD: 1, Other: 0 };
export const hasHun = (s) => /\b(hun|hungarian|magyar)\b/i.test(`${s.name || ""} ${s.tags || ""} ${s.file || ""}`);
export const PREFER_HUN_KEY = "dmm-prefer-hun";
export const readPreferHun = () => { try { return localStorage.getItem(PREFER_HUN_KEY) === "1"; } catch { return false; } };

export function scoreStream(s, cached, preferHun, context) {
  const match=context?releaseMatch(s,context):null;
  let v=match?.status==='conflict'?-5000:match?.status==='uncertain'?-1500:0;
  if(match?.warnings.length)v-=1000;
  if (cached.has(s.hash)) v += 1000;
  v += (RES_RANK[s.resolution] ?? 0) * 100;
  v += Math.min(60, Math.log2((s.seeders || 0) + 1) * 8);
  if (preferHun && hasHun(s)) v += 250;
  if (s.size > 80 * 1024 ** 3) v -= 80; // huge remuxes are rarely what people want first
  return v;
}

export function bestHash(list, cached, preferHun, context) {
  let best = null;
  let top = -Infinity;
  for (const s of list) {
    if(context && !releaseMatch(s,context).recommendable)continue;
    const v = scoreStream(s, cached, preferHun, context);
    if (v > top) { top = v; best = s.hash; }
  }
  return best;
}
