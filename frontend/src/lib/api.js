import axios from "axios";
import { Browser } from "@capacitor/browser";
import { Capacitor } from "@capacitor/core";
import { validateExternalURL } from "./validation";
import { offlineAdapter } from "./offlineAdapter";
import { readStored } from "./storage";

export const API = `${(process.env.REACT_APP_BACKEND_URL || "").replace(/\/$/, "")}/api`;
export const KEY_STORAGE = "tb_api_key";
export const OMDB_STORAGE = "omdb_api_key";
export const TMDB_STORAGE = "tmdb_api_key";
export const TORRENTIO_STORAGE = "use_torrentio";
export const getKey = () => readStored(KEY_STORAGE);
export const torrentioEnabled = () => readStored(TORRENTIO_STORAGE) !== "false";

export const IS_CAPACITOR_OFFLINE = process.env.REACT_APP_CAPACITOR_OFFLINE === "true" || Capacitor.isNativePlatform();
export const api = axios.create({ baseURL: API, timeout: 120_000, ...(IS_CAPACITOR_OFFLINE ? { adapter: offlineAdapter } : {}) });
api.interceptors.request.use((c) => {
  // Respect captured account headers, including explicitly unauthenticated requests.
  if (!c.headers.has("X-TorBox-Key")) {
    const k = getKey();
    if (k) c.headers.set("X-TorBox-Key", k);
  }
  if (!c.headers.has("X-OMDb-Key")) {
    const o = readStored(OMDB_STORAGE);
    if (o) c.headers.set("X-OMDb-Key", o);
  }
  if (!c.headers.has("X-TMDb-Key")) {
    const tmdb = readStored(TMDB_STORAGE);
    if (tmdb) c.headers.set("X-TMDb-Key", tmdb);
  }
  return c;
});

export const serverConfig = IS_CAPACITOR_OFFLINE ? Promise.resolve({}) : axios.get(`${API}/config`, { timeout: 15_000 }).then((r) => r.data).catch(() => ({}));

export const isCancelled = (e) => axios.isCancel(e) || e?.code === "ERR_CANCELED" || e?.name === "AbortError";

const redact = (text) => {
  let message = String(text);
  for (const secret of [getKey(), readStored(OMDB_STORAGE), readStored(TMDB_STORAGE)]) {
    if (secret) message = message.split(secret).join("[redacted]");
  }
  return message.replace(/([?&](?:api_?key|apikey|token)=)[^&\s]+/gi, "$1[redacted]");
};

export const errMsg = (e) => {
  if (isCancelled(e)) return "Request cancelled";
  if (e?.code === "ERR_OFFLINE_UNIMPLEMENTED") return "This feature is not available in the offline Android build yet.";
  if (e?.code === "ECONNABORTED" || e?.code === "ETIMEDOUT") return "The request timed out. Please try again.";
  const d = e?.response?.data?.detail;
  if (typeof d === "string" && d) return redact(d);
  if (Array.isArray(d)) {
    const messages = d.map((x) => typeof x?.msg === "string" ? x.msg : "").filter(Boolean);
    if (messages.length) return redact(messages.join(", "));
  }
  const status = e?.response?.status;
  if (status === 401 || status === 403) return "Access denied. Check your connection settings.";
  if (status === 429) return "Too many requests. Wait a moment and try again.";
  if (status >= 500) return "The service is unavailable. Please try again.";
  if (e?.code === "ERR_NETWORK") return "Unable to reach the server. Check your connection and try again.";
  return redact(e?.message || "Unknown error");
};

export const buildMagnet = (hash, name, trackers = []) => {
  let m = `magnet:?xt=urn:btih:${hash}`;
  if (name) m += `&dn=${encodeURIComponent(name)}`;
  trackers.forEach((t) => (m += `&tr=${encodeURIComponent(t)}`));
  return m;
};

export const addTorrent = ({ hash, name, magnet }) =>
  api.post("/torbox/add", { hash, name, magnet: magnet || (hash ? buildMagnet(hash, name) : undefined) });

export const checkCached = async (hashes, config = {}) => {
  const key = config.headers?.get?.("X-TorBox-Key") ?? config.headers?.["X-TorBox-Key"] ?? getKey();
  if (!key || !hashes.length) return new Set();
  const {includeDetails,...requestConfig}=config;
  const { data } = await api.post("/torbox/cached", { hashes, ...(includeDetails?{include_details:true}:{}) }, requestConfig);
  const cached=new Set(Array.isArray(data?.cached) ? data.cached : []);
  if(includeDetails)cached.torrentSizes=data?.torrent_sizes || {};
  return cached;
};

export const openExternal = (url) => { const safe = validateExternalURL(url); if (Capacitor.isNativePlatform()) return Browser.open({ url: safe }); return window.DMMApp ? window.DMMApp.open(safe) : window.open(safe, "_blank", "noopener,noreferrer"); };

export const copyText = async (text) => {
  if (window.DMMApp) return window.DMMApp.copy(text);
  try {
    await navigator.clipboard.writeText(text);
  } catch {
    const ta = document.createElement("textarea");
    ta.value = text;
    document.body.appendChild(ta);
    ta.select();
    document.execCommand("copy");
    ta.remove();
  }
};
