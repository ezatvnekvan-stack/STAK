import { api, getKey, IS_CAPACITOR_OFFLINE, isCancelled, serverConfig } from "./api";
import { parseStream, resolutionOf } from "./format";

const BASE = "https://torrentio.strem.fun";

const dedupe = (list) => {
  const seen = new Set();
  return list.filter((s) => s.hash && (seen.has(s.hash) ? false : seen.add(s.hash)));
};

const viaServer = async (type, streamId, config) => (await api.get(`/streams/${type}/${streamId}`, config)).data;

export async function fetchStreams(type, streamId, config = {}) {
  let streams;
  if (IS_CAPACITOR_OFFLINE) return dedupe((await viaServer(type, streamId, config)).filter(s => s?.infoHash).map(parseStream));
  if ((await serverConfig).server_streams) {
    try {
      return dedupe((await viaServer(type, streamId, config)).filter((s) => s.infoHash).map(parseStream));
    } catch (e) {
      if (isCancelled(e) || config.signal?.aborted) throw e;
      /* fall back to direct browser fetch */
    }
  }
  try {
    const controller = new AbortController();
    const abort = () => controller.abort();
    if (config.signal?.aborted) controller.abort();
    config.signal?.addEventListener("abort", abort, { once: true });
    const timeout = setTimeout(abort, 12000);
    let r;
    try {
      r = await fetch(`${BASE}/stream/${type}/${streamId}.json`, { signal: controller.signal });
      if (!r.ok) throw new Error(String(r.status));
      streams = (await r.json()).streams || [];
    } finally {
      clearTimeout(timeout);
      config.signal?.removeEventListener("abort", abort);
    }
  } catch (e) {
    if (config.signal?.aborted) throw e;
    const { data } = await api.get(`/streams/${type}/${streamId}`, config);
    streams = data;
  }
  return dedupe(streams.filter((s) => s.infoHash).map(parseStream));
}

export async function fetchIndexers(type, id, meta, season, episode) {
  if (!getKey() && !IS_CAPACITOR_OFFLINE) return { list: [], errors: [] };
  const { data } = await api.get(`/indexer-search/${type}/${id}`, {
    params: { name: meta.name, year: (meta.releaseInfo || meta.year || "").toString().slice(0, 4), season, episode },
  });
  const fromTorznab = (Array.isArray(data.results) ? data.results : []).map((r) => ({ ...r, size_scope:"torrent", torrent_size:Number(r.size)>0?Number(r.size):null, file: null, resolution: resolutionOf(r.name), tags: "", trackers: (() => { try { return new URL(r.magnet).searchParams.getAll('tr'); } catch { return []; } })() }));
  const fromStremio = (Array.isArray(data.streams) ? data.streams.filter(s => s?.infoHash) : []).map((s) => {
    const p = parseStream(s);
    return { ...p, source: p.source ? `${s._source} · ${p.source}` : s._source };
  });
  return { list: dedupe([...fromStremio, ...fromTorznab]), errors: Array.isArray(data.errors) ? data.errors : [] };
}

export const mergeStreams = (...lists) => dedupe(lists.flat());
