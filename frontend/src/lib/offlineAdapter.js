import {mergeHistory} from './historyModel';
import {watchedTimestamp} from './watchedDate';
import {jellyfinPlayback,jellyfinEpisodes,readJellyfinEpisodes} from './watchedState';
import {canonicalStreamId} from "./metadata/resolveIdentity";
import {metadataRoutes} from "./metadata/routes";
import {metadataContext} from "./metadata/settings";
import {calendarRange} from "./calendarModel";
import {calendarEntries} from "./metadata/calendar";
import { experienceSection, snapshotItem } from "./experience";
import { addMediaSizes, scanTask } from "./jellyfinLibraryMetrics";
import { jellyfinIdentity } from "./titleIdentity";
import { readJSON } from "./storage";
import { accountScope } from "./accountScope";
import { parseTorznabDetailed } from "./torznabParser";
// Capacitor local-only endpoints. Other endpoints are intentionally NOT faked.
import { LocalPreferences as Preferences } from "./secureStorage";
import { arrayResponse, objectResponse, validateExternalURL, validateProviderURL, validateMagnet, torboxList } from "./validation";
import { externalRequest } from "./transport";
import { normalizeHash } from "./torznabParser";
import { jellyfinHeaders, jellyfinError } from "./jellyfinAuth";
import {APP_VERSION} from './appVersion';
import { readJellyfinConnection, saveJellyfinConnection, removeJellyfinConnection } from "./jellyfinConnection";


const prefix = "dmm.local.v1.";
const read = async (key, fallback) => {
  const { value } = await Preferences.get({ key: prefix + key });
  if (!value) return fallback;
  try { const parsed = JSON.parse(value); return Array.isArray(fallback) ? (Array.isArray(parsed) ? parsed.filter(x => x && typeof x === "object") : fallback) : parsed && typeof parsed === "object" && !Array.isArray(parsed) ? parsed : fallback; } catch { return fallback; }
};
const write = (key, data) => Preferences.set({ key: prefix + key, value: JSON.stringify(data) });
const result = (config, data, status = 200) => ({ data, status, statusText: "OK", headers: {}, config });

const presentationWrites=new Map();
function savePresentation(namespace,id,data) {
  const queued=(presentationWrites.get(namespace) || Promise.resolve()).catch(()=>{}).then(async()=>{
    const cache=await read(namespace,{}),entries=Array.isArray(cache.entries)?cache.entries:[];
    await write(namespace,{entries:[{id,data},...entries.filter(e=>e && e.id!==id)].slice(0,24)});
  });
  presentationWrites.set(namespace,queued);
  const clear=()=>{if(presentationWrites.get(namespace)===queued)presentationWrites.delete(namespace);};queued.then(clear,clear);
  return queued;
}

// Direct external APIs: native Capacitor HTTP on Android, fetch in browser preview.
// Never send account keys to the old DMM server or to unrelated providers.
const requireKey = (value, provider) => {
  if (typeof value !== "string" || !value.trim() || value.length > 4096 || /[\r\n]/.test(value)) { const e = new Error(`Configure your ${provider} API key in Settings.`); e.response = { status: 401, data: { detail: e.message } }; throw e; }
  return value;
};
const queryParams = (config) => {
  const u = new URL(String(config.url || "/"), "https://local.invalid");
  return { ...Object.fromEntries(u.searchParams), ...(config.params || {}) };
};
async function executeAdapter(config) {
  if (config.signal?.aborted) { const e = new Error("Request cancelled"); e.code = "ERR_CANCELED"; throw e; }
  const external = (url, opts = {}) => externalRequest(url, { ...opts, signal: config.signal });
const torbox = (path, key, opts = {}) => external(`https://api.torbox.app/v1/api${path}`, {
  ...opts, headers: { Authorization: `Bearer ${requireKey(key === "guest" ? "" : key, "TorBox")}`, ...(opts.headers || {}) }
}).then(data => { const envelope = objectResponse(data, "TorBox"); if (envelope.success !== true || !Object.prototype.hasOwnProperty.call(envelope, "data")) throw new Error("TorBox returned an invalid response. Please retry."); return envelope.data; });

  const method = (config.method || "get").toLowerCase();
  const path = String(config.url || "").split("?")[0];
  const params = queryParams(config);
  let body = config.data;
  if (typeof body === "string") {
    try { body = JSON.parse(body || "null"); } catch { /* Form-encoded bodies remain strings. */ }
  }
  if (/^\/(metadata|meta|discover|ratings|tmdb)(?:\/|$)/.test(path)) {
    const handled=await metadataRoutes(config);if(handled)return handled;
  }
  const account = String(config.headers?.get?.("X-TorBox-Key") || config.headers?.["X-TorBox-Key"] || "guest");
  // Store an account-specific namespace without persisting the raw key.
  // crypto.subtle is not guaranteed in every Android WebView origin.
  // Use a stable, non-secret namespace; never store the API token as a key.
  const scope = await accountScope(account);
  const key = (name) => scope + "." + name;
  const defaultHome = scope === "guest" ? readJSON("home_prefs", [], Array.isArray) : [];
  const safeTitle = x => ({ imdb_id: String(x.imdb_id || ""), type: x.type === "series" ? "series" : "movie", name: typeof x.name === "string" ? x.name.slice(0,500) : "", poster: typeof x.poster === "string" && /^https:\/\//.test(x.poster) ? x.poster : "", year: String(x.year || "").slice(0,30), rating: String(x.rating || "").slice(0,20), genres: Array.isArray(x.genres) ? x.genres.filter(g => typeof g === "string").slice(0,20) : [], added_at: typeof x.added_at === "string" ? x.added_at : typeof x.created_at === "string" ? x.created_at : "" });
  const safeTracking = x => ({ ...safeTitle(x), updated_at: typeof x.updated_at === "string" ? x.updated_at : "", last_played: typeof x.last_played === "string" ? x.last_played : null, played: x.played === true, ...(Number.isInteger(x.total_episodes) && x.total_episodes>0 && x.total_episodes<=20000?{total_episodes:x.total_episodes}:{}), ...(x.source==="manual" || x.source==="jellyfin"?{source:x.source}:{}), ...(typeof x.synced_at==="string"?{synced_at:x.synced_at}:{}), ...(typeof x.sync_pending==="boolean"?{sync_pending:x.sync_pending}:{}), progress: Math.max(0, Math.min(100, Number(x.progress) || 0)), episodes: (Array.isArray(x.episodes) ? x.episodes : []).filter(e => e && Number.isInteger(e.season) && Number.isInteger(e.episode) && e.season >= 0 && e.episode > 0).map(e => ({ season: e.season, episode: e.episode, played: e.played === true, ...(e.source==="manual" || e.source==="jellyfin"?{source:e.source}:{}), ...(typeof e.updated_at==="string"?{updated_at:e.updated_at}:{}), ...(typeof e.synced_at==="string"?{synced_at:e.synced_at}:{}), ...(typeof e.sync_pending==="boolean"?{sync_pending:e.sync_pending}:{}), last_played: typeof e.last_played === "string" ? e.last_played : null, progress: Math.max(0, Math.min(100, Number(e.progress) || 0)) })) });
  const safeShare = x => ({ id: String(x.id || ""), title: typeof x.title === "string" ? x.title.slice(0,160) : "Magnet list", local_only: true, created_at: typeof x.created_at === "string" ? x.created_at : new Date().toISOString(), items: (Array.isArray(x.items) ? x.items : []).filter(i => i && normalizeHash(i.hash)).map(i => ({ hash: normalizeHash(i.hash), name: typeof i.name === "string" ? i.name.slice(0,500) : "", size: Math.max(0, Number(i.size) || 0) })) });
  if (path === "/backup" && method === "get") {
    const [watchlist, tracking, shares, prefs] = await Promise.all([read(key("watchlist"), []), read(key("tracking"), []), read(key("shares"), []), read(key("prefs"), { home: defaultHome })]);
    return result(config, { format: "dmm-backup", version: 1, exported_at: new Date().toISOString(), watchlist: watchlist.map(safeTitle), tracking: tracking.map(safeTracking), shares: shares.map(safeShare), home: (Array.isArray(prefs.home) ? prefs.home : []).filter(x => x && typeof x.id === "string" && typeof x.on === "boolean").map(x => ({id:x.id,on:x.on})) });
  }
  if (path === "/backup" && method === "post") {
    if (body?.format !== "dmm-backup" || body?.version !== 1 || JSON.stringify(body).length > 2 * 1024 * 1024) throw new Error("Unsupported or oversized backup.");
    for (const field of ["watchlist", "tracking", "shares", "home"]) if (!Array.isArray(body[field]) || body[field].length > 5000) throw new Error("Invalid backup lists.");
    let added = 0;
    for (const [field, normalize, identity] of [["watchlist",safeTitle,"imdb_id"],["tracking",safeTracking,"imdb_id"],["shares",safeShare,"id"]]) {
      const saved = await read(key(field), []), ids = new Set(saved.map(x => x[identity]));
      const incoming = body[field].filter(x => x && typeof x === "object").map(normalize).filter(x => identity === "imdb_id" ? /^(tt\d+|tmdb:\d+)$/.test(x.imdb_id) : /^local-[\w-]+$/.test(x.id));
      for (const x of incoming) if (!ids.has(x[identity])) { saved.push(x); ids.add(x[identity]); added++; }
      await write(key(field), saved);
    }
    const prefs = await read(key("prefs"), { home: defaultHome });
    if (!Array.isArray(prefs.home) || !prefs.home.length) await write(key("prefs"), {home: body.home.filter(x => x && typeof x.id === "string" && typeof x.on === "boolean").map(x => ({id:x.id,on:x.on}))});
    return result(config, { added });
  }
  if (path === "/prefs/experience") {
    if(method==='get')return result(config,await read(key('experience'),{}));
    if(method==='put') {
      const value=experienceSection(body?.section,body?.value),saved=await read(key('experience'),{});
      const next={...saved,[body.section]:value};await write(key('experience'),next);return result(config,next);
    }
  }
  if (path === "/prefs") {
    if (method === "get") return result(config, await read(key("prefs"), { home: defaultHome }));
    if (method === "put") { await write(key("prefs"), body || { home: [] }); return result(config, body); }
  }
  if (path === "/watchlist") {
    if (method === "get") return result(config, (await read(key("watchlist"), [])).map(safeTitle));
    if (method === "post") {
      const list = await read(key("watchlist"), []);
      const item = { ...body, created_at: new Date().toISOString(), added_at: new Date().toISOString() };
      await write(key("watchlist"), [item, ...list.filter(x => x.imdb_id !== item.imdb_id)]);
      return result(config, item, 201);
    }
  }
  if (path.startsWith("/watchlist/") && method === "delete") {
    const id = decodeURIComponent(path.slice("/watchlist/".length));
    const list = await read(key("watchlist"), []);
    await write(key("watchlist"), list.filter(x => x.imdb_id !== id));
    return result(config, { ok: true });
  }
  // Offline hash lists are stored on this device only. A remote recipient
  // cannot open an app-local ID without a separate share server.
  if (path === "/shares/mine" && method === "get") return result(config, (await read(key("shares"), [])).map(safeShare));
  if (path === "/shares" && method === "post") {
    const items = Array.isArray(body?.items) ? body.items : [];
    if (!items.length || items.length > 1000) throw new Error("Choose 1–1000 torrents to save a list.");
    const valid = items.map(x => ({ hash: String(x.hash || "").toLowerCase(), name: String(x.name || "").slice(0, 500), size: Number(x.size || 0) }))
      .filter(x => /^[a-f0-9]{40}$|^[a-z2-7]{32}$/i.test(x.hash));
    if (!valid.length) throw new Error("No valid torrent hashes selected.");
    const share = { id: `local-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`, title: String(body?.title || "Hash list").slice(0, 160), items: valid, created_at: new Date().toISOString(), local_only: true };
    await write(key("shares"), [share, ...(await read(key("shares"), []))]);
    return result(config, share, 201);
  }
  const shareId = path.match(/^\/shares\/([^/]+)$/);
  if (shareId && ["get", "delete"].includes(method)) {
    const id = decodeURIComponent(shareId[1]);
    const shares = await read(key("shares"), []);
    if (method === "delete") { await write(key("shares"), shares.filter(x => x.id !== id)); return result(config, { ok: true }); }
    const found = shares.find(x => x.id === id);
    if (!found) { const e = new Error("List not found on this device. Offline lists are not public links."); e.response = { status: 404, data: { detail: e.message } }; throw e; }
    return result(config, safeShare(found));
  }
  // Custom indexer settings are local to the Android device.
  if (path === "/indexers" && method === "get") return result(config, (await read(key("indexers"), [])).map(x => ({ ...x, name: typeof x.name === "string" ? x.name : "Indexer", url: typeof x.url === "string" ? x.url : "", enabled: x.enabled !== false })));
  if (path === "/indexers/test" && method === "post") {
    const u = new URL(validateProviderURL(body?.url));
    if (!["http:", "https:"].includes(u.protocol)) throw new Error("Indexer URL must use HTTP or HTTPS");
    if (body.kind === "stremio") {
      const manifest = await external(u.toString());
      return result(config, { ok: !!manifest?.id, detail: manifest?.name || "Invalid Stremio manifest" });
    }
    u.searchParams.set("t", "caps");
    if (body?.api_key) u.searchParams.set("apikey", body.api_key);
    const xml = await external(u.toString(), { raw: true });
    return result(config, { ok: /<caps(?:\s|>)/i.test(String(xml)), detail: "Torznab capabilities response received" });
  }
  if (path === "/indexers" && method === "post") {
    const u = new URL(validateProviderURL(body?.url));
    if (!["http:", "https:"].includes(u.protocol)) throw new Error("Invalid indexer URL");
    if (!["stremio", "torznab"].includes(body?.kind)) throw new Error("Invalid indexer type");
    const list = await read(key("indexers"), []);
    const item = { ...body, id: `${Date.now()}-${Math.random().toString(36).slice(2, 9)}`, enabled: true };
    await write(key("indexers"), [...list, item]);
    return result(config, item, 201);
  }
  const indexerMatch = path.match(/^\/indexers\/([^/]+)$/);
  if (indexerMatch && ["patch", "delete"].includes(method)) {
    const list = await read(key("indexers"), []);
    const id = decodeURIComponent(indexerMatch[1]);
    const updated = method === "delete" ? list.filter(x => x.id !== id) : list.map(x => x.id === id ? { ...x, enabled: !!body?.enabled } : x);
    await write(key("indexers"), updated);
    return result(config, { ok: true });
  }
  // Search configured Stremio and Torznab indexers directly from Android.
  const streamMatch = path.match(/^\/streams\/(movie|series)\/([^/]+)$/);
  const indexerMatchSearch = path.match(/^\/indexer-search\/(movie|series)\/([^/]+)$/);
  if (method === "get" && (streamMatch || indexerMatchSearch)) {
    const match = streamMatch || indexerMatchSearch;
    const kind = match[1];
    const requested=decodeURIComponent(match[2]);
    const id=/^tt\d+(?::\d+:\d+)?$/.test(requested)?requested:await canonicalStreamId(requested,kind,await metadataContext(config));
    const streams = [], results = [], errors = [];
    const configured = indexerMatchSearch ? (await read(key("indexers"), [])).filter(x => x.enabled !== false) : [];
    const sources = streamMatch ? [{ kind: "stremio", url: "https://torrentio.strem.fun/manifest.json", name: "Torrentio" }] : configured;
    const clean = value => String(value || "").replace(/\/manifest\.json(?:\?.*)?$/i, "").replace(/\/+$/, "");
    const baseId = id.split(':')[0];
    const streamId = kind === "series" && params.season != null && params.episode != null ? `${baseId}:${params.season}:${params.episode}` : id;
    await Promise.all(sources.map(async source => {
      try {
        if (source.kind === "stremio") {
          const data = await external(`${clean(source.url)}/stream/${kind}/${encodeURIComponent(streamId)}.json`);
          streams.push(...arrayResponse(data.streams, "Stremio").map(s => ({ ...s, _source: source.name || "Stremio" })));
        } else if (source.kind === "torznab") {
          const u = new URL(source.url);
          u.searchParams.set("t", kind === "series" ? "tvsearch" : "movie");
          if (source.api_key) u.searchParams.set("apikey", source.api_key);
          u.searchParams.set("imdbid", baseId.replace(/^tt/, ""));
          if (params.name) u.searchParams.set("q", params.name);
          if (params.season) u.searchParams.set("season", params.season);
          if (params.episode) u.searchParams.set("ep", params.episode);
          const xml = await external(u.toString(), { raw: true });
          if (/<error\b/i.test(String(xml))) throw new Error("Torznab rejected the search. Check credentials and capabilities.");
          const parsed = parseTorznabDetailed(xml, source.name || "Torznab");
          results.push(...parsed.items);
          if (parsed.missingHashes) errors.push({ source: source.name || "Torznab", error: `${parsed.missingHashes} result(s) lacked a torrent hash. Download their .torrent files from the indexer and add them using the Library file picker.` });
        }
      } catch (e) { errors.push({ source: source.name || "Indexer", error: e.message }); }
    }));
    return result(config, streamMatch ? streams : { streams, results, errors });
  }
  if (path === "/torbox/add" && method === "post") {
    const magnet = body?.magnet || (body?.hash ? `magnet:?xt=urn:btih:${body.hash}` : "");
    validateMagnet(magnet);
    const boundary = `dmm-${Date.now()}-${Math.random().toString(36).slice(2)}`;
    const fields = { magnet, ...(body?.name ? { name: String(body.name).slice(0, 500) } : {}) };
    const form = Object.entries(fields).map(([k,v]) => `--${boundary}\r\nContent-Disposition: form-data; name="${k}"\r\n\r\n${v}\r\n`).join("") + `--${boundary}--\r\n`;
    const response = await torbox("/torrents/createtorrent", account, { method: "POST", headers: { "Content-Type": `multipart/form-data; boundary=${boundary}` }, data: form, signal: config.signal });
    return result(config, { data: response, detail: "Torrent submitted" });
  }
  if (path === "/torbox/control" && method === "post") {
    const results = [];
    if (!["delete", "reannounce", "resume", "pause"].includes(body?.operation)) throw new Error("Unsupported TorBox operation.");
    for (const id of body?.torrent_ids || []) {
      if (!Number.isInteger(Number(id)) || Number(id) < 0) throw new Error("Invalid torrent ID.");
      try { await torbox("/torrents/controltorrent", account, { method: "POST", headers: { "Content-Type": "application/json" }, data: { torrent_id: id, operation: body.operation } }); results.push({ id, ok: true }); }
      catch (e) { results.push({ id, ok: false, error: e.message }); }
    }
    return result(config, results);
  }
  if (path === "/torbox/download" && method === "get") {
    const data = await torbox("/torrents/requestdl", account, { params: { token: account, torrent_id: params.torrent_id, zip_link: String(params.zip === true || params.zip === "true"), ...(params.file_id != null ? { file_id: params.file_id } : {}) } });
    return result(config, { url: validateExternalURL(data, { allowHTTP: false }) });
  }
  if (path === "/torbox/cached" && method === "post") {
    const hashes = [...new Set((Array.isArray(body?.hashes) ? body.hashes : []).map(normalizeHash).filter(Boolean))];
    const cached = [],torrent_sizes={};
    for (let i = 0; i < hashes.length; i += 80) {
      const data = await torbox("/torrents/checkcached", account, { params: { hash: hashes.slice(i, i+80).join(","), format: "list", list_files: body?.include_details?"true":"false" } });
      for (const item of arrayResponse(data, "TorBox cache")) if (item?.hash) {const hash=String(item.hash).toLowerCase();cached.push(hash);const bytes=Number(item.size || item.total_size);if(Number.isFinite(bytes) && bytes>0)torrent_sizes[hash]=bytes;}
    }
    return result(config, { cached, ...(body?.include_details?{torrent_sizes}:{}) });
  }
  if (path === "/torbox/me" && method === "get") return result(config, objectResponse(await torbox("/user/me", account, { params: { settings: "false" }, signal: config.signal }), "TorBox"));
  if (path === "/torbox/torrents" && method === "get") return result(config, torboxList(await torbox("/torrents/mylist", account, { params: { bypass_cache: "true" }, signal: config.signal })));
  // Direct Jellyfin API: no DMM server required. Credentials stay on the device.
  const jfKey = key("jellyfin");
  const jfConfig = async () => { const cfg = await readJellyfinConnection(account); if (!cfg) return null; validateProviderURL(cfg.url); return cfg; };
  const jfRequest = async (route, opts = {}) => {
    const cfg = await jfConfig();
    if (!cfg?.url || !cfg?.api_key) throw new Error("Connect Jellyfin in Settings first.");
    const base = cfg.url.replace(/\/+$/, "");
    const device = await read("device.identity", null);
    try { return await external(`${base}${route}`, { ...opts, headers: { ...jellyfinHeaders(cfg.api_key, device?.id), ...(opts.headers || {}) } }); }
    catch (error) { const message = jellyfinError(error); error.message = message; if (error.response) error.response = { ...error.response, data: { detail: message } }; throw error; }
  };
  if (path === "/jellyfin/login" && method === "post") {
    const base = validateProviderURL(body?.url).replace(/\/+$/, "");
    if (!body?.username) throw new Error("Enter your Jellyfin username.");
    let device = await read("device.identity", null);
    if (!device?.id) { device = { id: globalThis.crypto?.randomUUID?.() || `dmm-${Date.now()}-${Math.random().toString(36).slice(2)}` }; await write("device.identity", device); }
    const login = objectResponse(await external(`${base}/Users/AuthenticateByName`, { method: "POST", headers: { Authorization: `MediaBrowser Client="DMM Studio", Device="Android", DeviceId="${device.id}", Version="${APP_VERSION}"`, "Content-Type": "application/json" }, data: { Username: String(body.username), Pw: String(body.password || "") } }), "Jellyfin");
    if (!login.AccessToken || !login.User?.Id) throw new Error("Jellyfin returned invalid login data.");
    return result(config, { token: login.AccessToken, users: [{ id: login.User.Id, name: typeof login.User.Name === "string" ? login.User.Name : "Jellyfin user" }], server_name: "Jellyfin", version: "" });
  }
  if (path === "/jellyfin/test" && method === "post") {
    const url = validateProviderURL(body?.url).replace(/\/+$/, "");
    if (!/^https?:\/\/[^\s/]+/i.test(url)) throw new Error("Invalid Jellyfin URL");
    const headers = jellyfinHeaders(requireKey(body?.api_key, "Jellyfin"));
    const [info, users] = await Promise.all([
      external(`${url}/System/Info/Public`, { headers }),
      external(`${url}/Users`, { headers }),
    ]);
    return result(config, { server_name: info.ServerName || "Jellyfin", version: info.Version || "", users: arrayResponse(users, "Jellyfin").map(u => ({ id: String(u.Id), name: typeof u.Name === "string" ? u.Name : "Jellyfin user" })) });
  }
  if (path === "/jellyfin" && method === "put") {
    if (!body?.user_id || !body?.api_key) throw new Error("Choose a Jellyfin user and provide credentials.");
    const doc = { url: validateProviderURL(body.url).replace(/\/+$/, ""), api_key: body.api_key, user_id: String(body.user_id), user_name: typeof body.user_name === "string" ? body.user_name : "Jellyfin user", connected: true };
    // Verify the exact token/user pair before replacing a working connection.
    try {
      const user = objectResponse(await external(`${doc.url}/Users/${encodeURIComponent(doc.user_id)}`, { headers: jellyfinHeaders(doc.api_key) }), "Jellyfin user");
      if (String(user.Id || '') !== doc.user_id) throw new Error('Jellyfin returned a different user. Reconnect and choose your user again.');
      doc.is_admin = user.Policy?.IsAdministrator === true;
    } catch (error) { error.message = jellyfinError(error); if (error.response) error.response = { ...error.response, data: { detail: error.message } }; throw error; }
    await saveJellyfinConnection(doc);
    await write(jfKey, doc);
    await Preferences.set({ key: prefix + "jellyfin.active", value: scope });
    return result(config, { ok: true, stats: { titles: 0, episodes: 0 } });
  }
  if (path === "/jellyfin" && method === "get") {
    const cfg = await jfConfig();
    return result(config, cfg ? { connected: true, url: cfg.url, user_name: typeof cfg.user_name === "string" ? cfg.user_name : "Jellyfin user", last_sync: cfg.last_sync || null, last_stats: cfg.last_stats || null, is_admin: cfg.is_admin === true, webhook_token: null } : { connected: false });
  }
  if (path === "/jellyfin" && method === "delete") {
    await removeJellyfinConnection(account);
    return result(config, { ok: true });
  }
  if (path === "/jellyfin/counts" && method === "get") {
    const cfg = await jfConfig();
    if (!cfg) return result(config, { connected: false, movies: null, series: null });
    const count = async type => { const data = await jfRequest(`/Users/${encodeURIComponent(cfg.user_id)}/Items`, { params: { Recursive: true, IncludeItemTypes: type, Limit: 1, EnableTotalRecordCount: true } }); const total = Number(data.TotalRecordCount); if (data.TotalRecordCount == null || !Number.isInteger(total) || total < 0) throw new Error('Jellyfin did not provide a reliable library count.'); return total; };
    const [movies, series] = await Promise.all([count('Movie'), count('Series')]);
    return result(config, { connected: true, movies, series });
  }
  if (path === "/jellyfin/storage" && method === "get") {
    const cfg=await jfConfig();if(!cfg)throw new Error('Connect Jellyfin first.');
    const state={bytes:0,missing:0,seen:new Set()};let start=0,total=null;
    for(let page=0;page<200;page++) {
      const data=await jfRequest(`/Users/${encodeURIComponent(cfg.user_id)}/Items`,{params:{Recursive:true,IncludeItemTypes:'Movie,Episode',Fields:'MediaSources',EnableTotalRecordCount:true,StartIndex:start,Limit:250}});
      const items=arrayResponse(data.Items,'Jellyfin media sources');
      if(data.TotalRecordCount!=null && Number.isInteger(Number(data.TotalRecordCount)) && Number(data.TotalRecordCount)>=0)total=Number(data.TotalRecordCount);
      addMediaSizes(state,items);start+=items.length;
      if(!items.length || (total!=null && start>=total))break;
    }
    return result(config,{bytes:state.bytes,missing_sources:state.missing,items_checked:start,complete:total!=null && start>=total && state.missing===0,scope:'Accessible movie and episode media files; excludes artwork, music and server disk overhead.'});
  }
  if (path === "/jellyfin/scan-status" && method === "get") {
    return result(config,scanTask(arrayResponse(await jfRequest('/ScheduledTasks'),'Jellyfin scheduled tasks')));
  }
  if (path === "/jellyfin/resolve-title" && method === "get") {
    const cfg = await jfConfig();
    if (!cfg) return result(config, { matched: false, reason: 'Connect Jellyfin to resolve this title.' });
    const id = String(params.item_id || '');
    if (!id || id.length > 128 || /[/\s]/.test(id)) throw new Error('Invalid Jellyfin item identifier.');
    const item = objectResponse(await jfRequest(`/Users/${encodeURIComponent(cfg.user_id)}/Items/${encodeURIComponent(id)}`), 'Jellyfin item');
    const parent = item.Type === 'Episode' && item.SeriesId ? await jfRequest(`/Users/${encodeURIComponent(cfg.user_id)}/Items/${encodeURIComponent(item.SeriesId)}`) : item;
    const mappings=await read('jellyfin.presentation.'+(await accountScope(`${cfg.url}|${cfg.user_id}`))+'.matches',{});
    const manual=mappings[parent.Id];
    const identity = manual ? {...manual,...(item.Type==='Episode'?{season:Number(item.ParentIndexNumber),episode:Number(item.IndexNumber)}:{})} : jellyfinIdentity(item, parent);
    return result(config, { matched: !!identity, identity, name: item.SeriesName || item.Name || '', reason: identity ? null : 'This title has no usable IMDb or TMDb identifier in Jellyfin.', web_url: `${cfg.url}/web/#/details?id=${encodeURIComponent(id)}` });
  }
  if (path === "/jellyfin/home" && method === "get") {
    const cfg = await jfConfig();
    if (!cfg) throw new Error("Connect Jellyfin in Settings first.");
    const q = { UserId: cfg.user_id, Limit: 30, Fields: "PrimaryImageAspectRatio,ProviderIds", EnableUserData: true };
    const [resume, next] = await Promise.allSettled([
      jfRequest(`/Users/${encodeURIComponent(cfg.user_id)}/Items/Resume`, { params: q }),
      jfRequest("/Shows/NextUp", { params: q })
    ]);
    if (resume.status === 'rejected' && next.status === 'rejected') throw resume.reason;
    return result(config, { continue_watching: resume.status === 'fulfilled' ? arrayResponse(resume.value.Items, "Jellyfin") : [], next_up: next.status === 'fulfilled' ? arrayResponse(next.value.Items, "Jellyfin") : [], errors: [resume, next].flatMap((r, i) => r.status === 'rejected' ? [{ section: i ? 'Next Up' : 'Continue Watching', error: jellyfinError(r.reason) }] : []) });
  }
  // Snapshots are encrypted on Android, isolated by server and user, bounded,
  // and contain presentation fields only. Never cache tokens or media paths.
  const jfNamespace=async cfg=>'jellyfin.presentation.'+await accountScope(`${cfg.url}|${cfg.user_id}`);
  const jfSnapshot=async(cfg,cacheId,fetcher)=>{
    const namespace=await jfNamespace(cfg),cache=await read(namespace,{entries:[]});
    try {
      const data=await fetcher();if(config.signal?.aborted)throw new Error('Request cancelled');
      const saved={...data,items:data.items.map(snapshotItem),cached_at:new Date().toISOString()};
      // Snapshot persistence must not turn a successful server response into failure.
      try{await savePresentation(namespace,cacheId,saved);}catch{/* Live data is still usable. */}
      return {...data,cached_at:saved.cached_at,offline:false};
    }catch(error){
      if(config.signal?.aborted)throw error;
      const saved=(Array.isArray(cache.entries)?cache.entries:[]).find(e=>e?.id===cacheId)?.data;
      if(saved && Array.isArray(saved.items))return {...saved,offline:true,error:error.message};
      throw error;
    }
  };
  if (path === '/jellyfin/diagnostics' && method==='get') {
    const cfg=await jfConfig();if(!cfg)return result(config,{connected:false,checks:[]});
    const start=Date.now();
    const checks=await Promise.allSettled([jfRequest('/System/Info/Public'),jfRequest(`/Users/${encodeURIComponent(cfg.user_id)}`),jfRequest(`/Users/${encodeURIComponent(cfg.user_id)}/Views`)]);
    return result(config,{connected:true,transport:new URL(cfg.url).protocol==='https:'?'HTTPS':'Local HTTP (unencrypted)',elapsed_ms:Date.now()-start,checks:checks.map((entry,i)=>({name:['Server reachable','Session valid','Library access'][i],ok:entry.status==='fulfilled' && !!entry.value && typeof entry.value==='object' && !Array.isArray(entry.value),...(entry.status==='rejected'?{error:entry.reason.message}:!entry.value || typeof entry.value!=='object' || Array.isArray(entry.value)?{error:'Invalid server response'}:{})}))});
  }
  if(path==='/jellyfin/catalog' && method==='get') {
    const cfg=await jfConfig();if(!cfg)return result(config,{connected:false,complete:false,items:[]});
    const namespace=await jfNamespace(cfg);
    try {
      const data=await jfRequest(`/Users/${encodeURIComponent(cfg.user_id)}/Items`,{params:{Recursive:true,IncludeItemTypes:'Movie,Series',Fields:'ProviderIds,UserData',EnableUserData:true,Limit:500,EnableTotalRecordCount:true}});
      const list=arrayResponse(data.Items,'Jellyfin catalog'),matches=await read(namespace+'.matches',{});
      const items=list.map(item=>{const identity=jellyfinIdentity(item),manual=matches[item.Id];return {item_id:item.Id,type:item.Type==='Series'?'series':'movie',ids:[identity?.id,manual?.id,...Object.entries(item.ProviderIds || {}).flatMap(([provider,value])=>provider.toLowerCase()==='imdb' && /^tt\d+$/.test(value)?[String(value)]:provider.toLowerCase()==='tmdb' && /^\d+$/.test(value)?[`tmdb:${value}`]:[])].filter(Boolean),played:typeof item.UserData?.Played==='boolean'?item.UserData.Played:null,last_played:item.UserData?.LastPlayedDate || null};});
      const active=await jfConfig();if(config.signal?.aborted || !active || active.url!==cfg.url || active.user_id!==cfg.user_id || active.api_key!==cfg.api_key)throw new Error('Jellyfin connection changed. Retry library status.');
      const response={connected:true,complete:Number.isInteger(data.TotalRecordCount) && data.TotalRecordCount<=list.length,total:data.TotalRecordCount,items,checked_at:new Date().toISOString()};
      try{await savePresentation(namespace,'catalog',response);}catch{/* Live state remains usable if optional snapshot storage fails. */}
      return result(config,response);
    }catch(error){
      const active=await jfConfig();if(config.signal?.aborted || !active || active.url!==cfg.url || active.user_id!==cfg.user_id || active.api_key!==cfg.api_key)throw error;
      const cache=await read(namespace,{entries:[]}),saved=cache.entries?.find(entry=>entry.id==='catalog')?.data;
      if(saved && Array.isArray(saved.items))return result(config,{...saved,offline:true,error:error.message});
      throw error;
    }
  }

  if(path==='/jellyfin/match' && method==='put') {
    const cfg=await jfConfig();if(!cfg)throw new Error('Connect Jellyfin first.');
    const id=String(body?.item_id || '');if(!/^[a-zA-Z0-9-]+$/.test(id))throw new Error('Invalid Jellyfin item.');
    const item=await jfRequest(`/Users/${encodeURIComponent(cfg.user_id)}/Items/${encodeURIComponent(id)}`);
    const parent=item.Type==='Episode' && item.SeriesId?await jfRequest(`/Users/${encodeURIComponent(cfg.user_id)}/Items/${encodeURIComponent(item.SeriesId)}`):item;
    const identity=body?.identity,type=parent.Type==='Series'?'series':parent.Type==='Movie'?'movie':null;
    if(!type || identity?.type!==type || !/^(tt\d+|tmdb:\d+)$/.test(identity?.id || ''))throw new Error('Choose a matching movie or series.');
    const namespace=(await jfNamespace(cfg))+'.matches',matches=await read(namespace,{});
    await write(namespace,Object.fromEntries([...Object.entries(matches).filter(([itemId,value])=>itemId!==parent.Id && /^(tt\d+|tmdb:\d+)$/.test(value?.id || '') && ['movie','series'].includes(value?.type)),[parent.Id,{id:identity.id,type}]].slice(-1000)));return result(config,{ok:true});
  }
  if (path === "/jellyfin/libraries" && method === "get") {
    const cfg = await jfConfig();if (!cfg) throw new Error("Connect Jellyfin in Settings first.");
    return result(config,await jfSnapshot(cfg,'views',async()=>({items:arrayResponse((await jfRequest(`/Users/${encodeURIComponent(cfg.user_id)}/Views`)).Items,'Jellyfin')})));
  }
  if (path === "/jellyfin/library-items" && method === "get") {
    const cfg = await jfConfig();if (!cfg) throw new Error("Connect Jellyfin in Settings first.");
    const request={ ParentId: params.parent_id, Recursive: true, Limit: Math.max(1, Math.min(150, Number(params.limit) || 150)), SortBy: ({name:"SortName",recent:"DateCreated",year:"ProductionYear"}[params.sort] || "SortName"), SortOrder: ["recent","year"].includes(params.sort) ? "Descending" : "Ascending", SearchTerm: String(params.search || "").trim().slice(0,200) || undefined, IsPlayed: params.filter === "watched" ? true : params.filter === "unwatched" ? false : undefined, EnableTotalRecordCount: true, Fields: "PrimaryImageAspectRatio,ProviderIds", EnableUserData: true, IncludeItemTypes: "Movie,Series", StartIndex: Math.max(0, Number(params.start) || 0) };
    return result(config,await jfSnapshot(cfg,JSON.stringify(request),async()=>{const data=await jfRequest(`/Users/${encodeURIComponent(cfg.user_id)}/Items`,{params:request});return {items:arrayResponse(data.Items,'Jellyfin'),total:data.TotalRecordCount || 0};}));
  }
  if (["/jellyfin/recent","/jellyfin/scan-recent"].includes(path) && method === "get") {
    const cfg = await jfConfig();if (!cfg) throw new Error("Connect Jellyfin in Settings first.");
    const data = await jfRequest(`/Users/${encodeURIComponent(cfg.user_id)}/Items`, { params: { Recursive: true, IncludeItemTypes: path.endsWith('scan-recent')?'Movie,Series,Episode':"Movie,Series", SortBy: "DateCreated", SortOrder: "Descending", Limit: 30, Fields: "ProviderIds" } });
    return result(config, { items: arrayResponse(data.Items, "Jellyfin") });
  }
  const jfRemoteHistory = async () => {
    const cfg = await jfConfig();
    if (!cfg?.user_id) {
      const local = (await read(key("tracking"), [])).map(safeTracking);
      const rows = local.flatMap(x => x.type === "series" ? x.episodes.map(e => ({ ...e, imdb_id: x.imdb_id, name: x.name, series_name: x.name, type: "episode", last_played: e.last_played || x.updated_at || x.last_played })) : [{ ...x, type: "movie", last_played: x.last_played || x.updated_at }]);
      const recent = rows.filter(x => x.played).sort((a,b) => String(b.last_played || "").localeCompare(String(a.last_played || "")));
      return { recent, continue: rows.filter(x => !x.played && x.progress > 0), stats: { movies: recent.filter(x => x.type === "movie").length, episodes: recent.filter(x => x.type === "episode").length, hours: 0 }, local_only: true };
    }
    const uid = encodeURIComponent(cfg.user_id),matches=await read((await jfNamespace(cfg))+'.matches',{});
    const [played, resume] = await Promise.all([
      jfRequest(`/Users/${uid}/Items`, { params: { Recursive: true, IncludeItemTypes: "Movie,Episode", IsPlayed: true, SortBy: "DatePlayed", SortOrder: "Descending", Limit: 300, Fields: "ProviderIds,UserData,RunTimeTicks" } }),
      jfRequest(`/Users/${uid}/Items/Resume`, { params: { Limit: 60, Fields: "ProviderIds,UserData,RunTimeTicks" } })
    ]);
    const series = new Map();
    const seriesIds = [...new Set([...arrayResponse(played.Items, "Jellyfin"), ...arrayResponse(resume.Items, "Jellyfin")].filter(x => x.Type === "Episode" && x.SeriesId).map(x => x.SeriesId))];
    let seriesCursor = 0;
    await Promise.all(Array.from({ length: Math.min(4, seriesIds.length) }, async () => {
      while (seriesCursor < seriesIds.length) {
        const id = seriesIds[seriesCursor++];
        try { const title = await jfRequest(`/Users/${uid}/Items/${encodeURIComponent(id)}`); series.set(id, {id:matches[id]?.id || title.ProviderIds?.Imdb || (title.ProviderIds?.Tmdb?`tmdb:${title.ProviderIds.Tmdb}`:""),total:Number(title.RecursiveItemCount)>0?Number(title.RecursiveItemCount):null}); } catch { /* A missing series mapping does not discard other history. */ }
      }
    }));
    const normalize = x => {
      const runtime = Number(x.RunTimeTicks || 0), position = Number(x.UserData?.PlaybackPositionTicks || 0);
      return { imdb_id: x.Type === "Episode" ? series.get(x.SeriesId)?.id || x.SeriesProviderIds?.Imdb || "" : matches[x.Id]?.id || x.ProviderIds?.Imdb || (x.ProviderIds?.Tmdb?`tmdb:${x.ProviderIds.Tmdb}`:""), name: x.Name || "", series_name: x.SeriesName || "", type: x.Type === "Episode" ? "episode" : "movie", source:"jellyfin", season: x.ParentIndexNumber || 0, episode: x.IndexNumber || 0, year: x.ProductionYear || "", total_episodes:series.get(x.SeriesId)?.total || null, played: !!x.UserData?.Played, progress: runtime ? Math.min(100, position / runtime * 100) : 0, last_played: x.UserData?.LastPlayedDate || x.DateCreated || null, duration_hours: runtime / 36000000000 };
    };
    const recent = (arrayResponse(played.Items, "Jellyfin")).map(normalize).filter(x => x.imdb_id);
    const ongoing = (arrayResponse(resume.Items, "Jellyfin")).map(normalize).filter(x => x.imdb_id);
    return { recent, continue: ongoing, stats: { movies: recent.filter(x => x.type === "movie").length, episodes: recent.filter(x => x.type === "episode").length, hours: Math.round(recent.reduce((n, x) => n + x.duration_hours, 0)) } };
  };
  const jfHistory = async () => {
    const local=(await read(key('tracking'),[])).map(safeTracking);
    try{return mergeHistory(await jfRemoteHistory(),local);}
    catch(error){if(config.signal?.aborted || error.code==='ERR_CANCELED')throw error;return mergeHistory({recent:[],continue:[],local_only:true,sync_error:'Jellyfin history unavailable. Showing saved activity.'},local);}
  };
  // Resolve an IMDb title to its Jellyfin item, then sync watched state.
  // A failed remote update must never discard the locally saved tracking state.
  const jfFindTitle = async (identity,type,tmdb='') => {
    const cfg=await jfConfig();if(!cfg)return {cfg:null,items:[],complete:false};
    const imdb=/^tt\d+$/.test(identity)?identity:null,tmdbId=identity.match(/^tmdb:(\d+)$/)?.[1] || String(tmdb || '');
    if(!imdb && !/^\d+$/.test(tmdbId))return {cfg,items:[],complete:false};
    const mappings=await read((await jfNamespace(cfg))+'.matches',{});
    const manual=Object.entries(mappings).find(([,match])=>(match.id===identity || (/^\d+$/.test(tmdbId) && match.id===`tmdb:${tmdbId}`)) && (!type || match.type===type));
    if(manual){const item=await jfRequest(`/Users/${encodeURIComponent(cfg.user_id)}/Items/${encodeURIComponent(manual[0])}`);return {cfg,items:item && (!type || item.Type===(type==='series'?'Series':'Movie'))?[item]:[],complete:true};}
    const filter=[imdb && `imdb.${imdb}`,/^\d+$/.test(tmdbId) && `tmdb.${tmdbId}`].filter(Boolean).join(',');
    const data=await jfRequest(`/Users/${encodeURIComponent(cfg.user_id)}/Items`,{params:{Recursive:true,IncludeItemTypes:type==='series'?'Series':type==='movie'?'Movie':'Movie,Series',AnyProviderIdEquals:filter,Fields:'ProviderIds,MediaSources,UserData,RunTimeTicks',EnableUserData:true,EnableTotalRecordCount:true,Limit:200}});
    const list=arrayResponse(data.Items,'Jellyfin'),items=list.filter(item=>(!type || item.Type===(type==='series'?'Series':'Movie')) && Object.entries(item.ProviderIds || {}).some(([provider,value])=>(imdb && provider.toLowerCase()==='imdb' && String(value).toLowerCase()===imdb.toLowerCase()) || (tmdbId && provider.toLowerCase()==='tmdb' && String(value)===tmdbId)));
    return {cfg,items,complete:Number.isInteger(data.TotalRecordCount)&&data.TotalRecordCount>=0&&data.TotalRecordCount<=list.length};
  };
  const jfMarkTitle = async (imdbId, played, episodePairs = [], movieDate) => {
    const cfg = await jfConfig();
    if (!cfg?.url || !cfg?.api_key || !cfg?.user_id) return { updated: 0, failed: 0, local_only: true };
    const uid = encodeURIComponent(cfg.user_id);
    const {items}=await jfFindTitle(imdbId,episodePairs.length?'series':undefined);
    const title=items.find(item=>item.UserData?.Played) || items[0];
    if (!title) return { updated: 0, failed: 0, local_only: true };
    let targets=items.map(item=>item.Id),missing=0;
    if (episodePairs.length) {
      if (title.Type !== "Series") return { updated: 0, failed: episodePairs.length, local_only: false };
      const episodes=await readJellyfinEpisodes(jfRequest,cfg.user_id,items.filter(item=>item.Type==="Series").map(item=>item.Id));
      targets=[];
      for(const pair of episodePairs){const matching=episodes.items.filter(item=>Number(item.ParentIndexNumber)===pair.season && Number(item.IndexNumber)===pair.episode);if(!matching.length)missing++;targets.push(...matching.map(item=>({id:item.Id,date:pair.date})));}
      if(!episodes.complete)missing++;

    }
    const unique=new Map(targets.map(target=>typeof target==='string'?[target,{id:target,date:movieDate}]:[target.id,target]));
    const settled = await Promise.allSettled([...unique.values()].map(target => jfRequest(
      `/Users/${uid}/PlayedItems/${encodeURIComponent(target.id)}`, { method: played ? "POST" : "DELETE", ...(played && target.date?{params:{DatePlayed:watchedTimestamp(target.date)}}:{}) }
    )));
    return {
      updated: settled.filter(x => x.status === "fulfilled").length,
      failed: settled.filter(x => x.status === "rejected").length + missing,
      local_only: false
    };
  };
  // Watched state is local-first; Jellyfin sync is best-effort when configured.
  if (path === "/tracking/state" && method === "get") return result(config, (await read(key("tracking"), [])).map(safeTracking));
  const trackingTitle = path.match(/^\/tracking\/title\/([^/]+)$/);
  if (trackingTitle && method === "get") {
    const id = decodeURIComponent(trackingTitle[1]);
    const state = await read(key("tracking"), []);
    const item = state.find(x => x.imdb_id === id) || null;
    return result(config, { item, episodes: Array.isArray(item?.episodes) ? item.episodes.filter(x => x && typeof x === "object") : [] });
  }
  if (path === "/tracking/mark" && method === "post") {
    const id = String(body?.imdb_id || "");
    if (!id) throw new Error("Title identifier is required");
    const state = await read(key("tracking"), []);
    const previous = state.find(x => x.imdb_id === id);
    const played = !!body?.played,editedAt=new Date().toISOString();
    const movieDate=played?watchedTimestamp(body?.watched_on):null;
    const supplied=Array.isArray(body?.watched_dates)?body.watched_dates:[];
    const episodeDates=new Map();
    for(const entry of supplied){if(!entry || !Number.isInteger(entry.season) || !Number.isInteger(entry.episode))throw new Error('Invalid episode watched date.');watchedTimestamp(entry.date);episodeDates.set(`${entry.season}:${entry.episode}`,entry.date);}

    const incoming = Array.isArray(body?.episodes) ? body.episodes : [];
    const episodeMap = new Map((Array.isArray(previous?.episodes) ? previous.episodes.filter(x => x && typeof x === "object") : []).map(e => [`${e.season}:${e.episode}`, e]));
    for (const pair of incoming) {
      const season = Number(Array.isArray(pair) ? pair[0] : pair.season);
      const episode = Number(Array.isArray(pair) ? pair[1] : pair.episode);
      if (!Number.isInteger(season) || !Number.isInteger(episode) || season < 0 || episode < 0) continue;
      episodeMap.set(`${season}:${episode}`, { ...episodeMap.get(`${season}:${episode}`), season, episode, played, source:"manual",updated_at:editedAt,sync_pending:true,synced_at:null,progress: played ? 100 : 0, last_played: played ? watchedTimestamp(episodeDates.get(`${season}:${episode}`) || body?.watched_on) : episodeMap.get(`${season}:${episode}`)?.last_played || null });
    }
    const item = { ...previous, imdb_id: id, type: body.type || previous?.type || "movie", name: body.name || previous?.name || "", ...(Number.isInteger(body.total_episodes) && body.total_episodes>0 && body.total_episodes<=20000?{total_episodes:body.total_episodes}:{}), played: incoming.length ? (previous?.played || false) : played, ...(!incoming.length && played?{last_played:movieDate,progress:100}:!incoming.length?{progress:0}:{}), episodes: [...episodeMap.values()], updated_at: editedAt, ...(!incoming.length?{source:"manual",sync_pending:true,synced_at:null}:{}) };
    if(item.type==='series' && Number(item.total_episodes)>0)item.played=new Set(item.episodes.filter(row=>row.season>0 && row.played).map(row=>`${row.season}:${row.episode}`)).size>=item.total_episodes;
    await write(key("tracking"), [item, ...state.filter(x => x.imdb_id !== id)]);
    let sync;
    try { sync = await jfMarkTitle(id, played, incoming.map(pair => ({
      season: Number(Array.isArray(pair) ? pair[0] : pair.season),
      episode: Number(Array.isArray(pair) ? pair[1] : pair.episode),
      date:episodeDates.get(`${Number(Array.isArray(pair)?pair[0]:pair.season)}:${Number(Array.isArray(pair)?pair[1]:pair.episode)}`) || body?.watched_on
    })),body?.watched_on); }
    catch (e) { sync = { updated: 0, failed: Math.max(1, incoming.length), local_only: false, error: e.message }; }
    const synchronized=sync.failed===0 && !sync.local_only;
    const completedAt=new Date().toISOString();
    if(incoming.length)item.episodes=item.episodes.map(episode=>episode.updated_at===editedAt?{...episode,sync_pending:sync.failed>0,synced_at:synchronized?completedAt:null}:episode);
    else {item.sync_pending=sync.failed>0;item.synced_at=synchronized?completedAt:null;}
    await write(key("tracking"),[item,...state.filter(x=>x.imdb_id!==id)]);
    return result(config, { ok: true, jellyfin_updated: sync.updated, jellyfin_failed: sync.failed, local_only: sync.local_only, ...(sync.error ? { sync_error: sync.error } : {}) });
  }
  if (path === "/system/health" && method === "get") return result(config, { api: "Android local", mongodb: "Not required" });
  if (path === "/media/continue" && method === "get") {
    const history = await jfHistory();
    return result(config, { items: history.continue });
  }
  if (path === "/media/overview" && method === "get") {
    const [torrents, watchlist] = await Promise.all([torbox("/torrents/mylist", account), read(key("watchlist"), [])]);
    const items = torboxList(torrents);
    return result(config, { torbox: { torrents: items.length, total_bytes: items.reduce((n, t) => n + Number(t.size || t.total_size || 0), 0) }, watchlist: watchlist.length });
  }
  if (path === "/media/duplicates" && method === "get") {
    const data = await torbox("/torrents/mylist", account);
    const items = torboxList(data);
    const groups = new Map();
    for (const item of items) { const hash = String(item.hash || item.info_hash || "").toLowerCase(); if (hash) groups.set(hash, [...(groups.get(hash) || []), item]); }
    const duplicates = [...groups].filter(([, values]) => values.length > 1).map(([hash, values]) => ({ hash, items: values }));
    return result(config, { duplicate_groups: duplicates.length, groups: duplicates });
  }
  if (path === "/media/upcoming" && method === "get") {
    const days = Math.max(1, Math.min(31, Number(params.days) || 7));
    const calendar = await executeAdapter({ ...config, url: "/calendar", params: { scope: "following", days } });
    return result(config, { items: calendar.data.events, errors: calendar.data.errors });
  }
  if (path === "/calendar" && method === "get") {
    const range=calendarRange(params);
    const followed = await read(key("watchlist"), []);
    return result(config,await calendarEntries(followed,range,await metadataContext(config)));
  }
  if (path === "/tracking/history" && method === "get") return result(config, await jfHistory());
  if (path === "/jellyfin/sync" && method === "post") {
    const cfg = await jfConfig();
    if (!cfg) throw new Error("Connect Jellyfin in Settings first.");
    const data = await jfHistory();
    if(data.sync_error)throw new Error(data.sync_error);
    const active=await jfConfig();
    if(config.signal?.aborted || !active || active.url!==cfg.url || active.user_id!==cfg.user_id || active.api_key!==cfg.api_key)throw new Error('Jellyfin connection changed. Retry progress sync.');
    const previous = await read(key("tracking"), []);
    const merged = new Map(previous.map(x => [x.imdb_id, x]));
    for (const row of [...data.recent, ...data.continue]) {
      const old = merged.get(row.imdb_id) || { imdb_id: row.imdb_id, episodes: [] };
      const newerManual=(Date.parse(old.updated_at || '') || 0)>(Date.parse(row.last_played || '') || 0);
      if(newerManual && (row.type!=='episode' || old.episodes?.some(episode=>episode.season===row.season && episode.episode===row.episode)))continue;
      if (row.type === "episode") { const episodes = new Map((Array.isArray(old.episodes) ? old.episodes.filter(x => x && typeof x === "object") : []).map(x => [`${x.season}:${x.episode}`, x])); episodes.set(`${row.season}:${row.episode}`, row); merged.set(row.imdb_id, { ...old, name: row.series_name || old.name, type: "series", ...(row.total_episodes?{total_episodes:row.total_episodes}:{}), episodes: [...episodes.values()] }); }
      else merged.set(row.imdb_id, { ...old, ...row });
    }
    await write(key("tracking"), [...merged.values()]);
    const stats = { titles: data.stats.movies, episodes: data.stats.episodes };
    await write(jfKey, { ...cfg, last_sync: new Date().toISOString(), last_stats: stats });
    await saveJellyfinConnection({ ...cfg, last_sync: new Date().toISOString(), last_stats: stats });
    return result(config, { ...stats, local_only: true });
  }
  if (path === "/jellyfin/scan" && method === "post") {
    await jfRequest("/Library/Refresh", { method: "POST" });
    return result(config, { ok: true });
  }
  if (path === "/jellyfin/sessions" && method === "get") {
    const data = await jfRequest("/Sessions");
    return result(config, { sessions: arrayResponse(data, "Jellyfin sessions").map(s => ({ id: s.Id, device: s.DeviceName, client: s.Client, controllable: !!s.SupportsRemoteControl, user: s.UserName || "", title: s.NowPlayingItem?.Name || "", is_paused: !!s.PlayState?.IsPaused, position_ticks: Number(s.PlayState?.PositionTicks) || 0 })) });
  }
  if (path === "/jellyfin/title-availability" && method === "get") {
    const cfg = await jfConfig();
    if (!cfg) return result(config, { state: 'disconnected', connected: false, in_library: null });
    const identity=String(params.imdb_id || ''),found=await jfFindTitle(identity,params.type,String(params.tmdb_id || ''));
    const item=found.items.find(row=>row.UserData?.Played===true) || found.items[0];
    if(!item)return result(config,{state:found.complete?'unavailable':'unknown',connected:true,in_library:found.complete?false:null});
    let current=item,episodeAvailable=null,episodeState=[],resumePlayback=null,coverage={complete:true,error:''};
    if(item.Type==='Series'){
      coverage=await readJellyfinEpisodes(jfRequest,cfg.user_id,found.items.map(row=>row.Id));
      episodeState=jellyfinEpisodes(coverage.items);
      const selected=episodeState.find(row=>row.season===Number(params.season)&&row.episode===Number(params.episode));
      const matches=coverage.items.filter(row=>Number(row.ParentIndexNumber)===Number(params.season)&&Number(row.IndexNumber)===Number(params.episode));
      current=matches.find(row=>row.UserData?.Played===true) || matches[0];
      episodeAvailable=selected?true:coverage.complete?false:null;
      resumePlayback=episodeState.filter(row=>row.played!==true&&row.position_ticks>0&&row.runtime_ticks>0).sort((a,b)=>String(b.last_played || '').localeCompare(String(a.last_played || '')))[0] || null;
    }
    const active=await jfConfig();
    if(config.signal?.aborted || !active || active.url!==cfg.url || active.user_id!==cfg.user_id || active.api_key!==cfg.api_key)throw new Error('Jellyfin connection changed. Refresh watched state.');
    return result(config,{state:'available',connected:true,in_library:true,item_id:item.Id,item_type:item.Type,title_state:jellyfinPlayback(item),playback:jellyfinPlayback(current),resume_playback:resumePlayback,episodes:episodeState,episodes_complete:coverage.complete,episodes_error:coverage.error || null,episode_available:episodeAvailable,web_url:`${cfg.url}/web/#/details?id=${encodeURIComponent(current?.Id || item.Id)}`,media_streams:item.MediaSources?.flatMap(source=>source.MediaStreams || []) || []});
  }

  const jfItemMatch = path.match(/^\/jellyfin\/item\/([^/]+)$/);
  const jfWatchedMatch = path.match(/^\/jellyfin\/item\/([^/]+)\/watched$/);
  if (jfItemMatch && method === "get") {
    const cfg = await jfConfig();
    if (!cfg) throw new Error("Connect Jellyfin in Settings first.");
    const id = encodeURIComponent(decodeURIComponent(jfItemMatch[1]));
    const item = await jfRequest(`/Users/${encodeURIComponent(cfg.user_id)}/Items/${id}`);
    const episodes = item.Type === "Series"
      ? (await jfRequest(`/Shows/${id}/Episodes`, { params: { UserId: cfg.user_id, Fields: "Overview,MediaSources,ProviderIds", Limit: 1000 } })).Items || []
      : [];
    const parent = item.Type === 'Episode' && item.SeriesId ? await jfRequest(`/Users/${encodeURIComponent(cfg.user_id)}/Items/${encodeURIComponent(item.SeriesId)}`).catch(() => null) : item;
    return result(config, { item, episodes: arrayResponse(episodes, "Jellyfin episodes"), title_imdb_id: parent?.ProviderIds?.Imdb || null, web_url: `${cfg.url}/web/#/details?id=${id}` });
  }
  if (jfWatchedMatch && method === "post") {
    const cfg = await jfConfig();
    if (!cfg) throw new Error("Connect Jellyfin in Settings first.");
    const id = encodeURIComponent(decodeURIComponent(jfWatchedMatch[1]));
    const played = params.played === true || params.played === "true";
    await jfRequest(`/Users/${encodeURIComponent(cfg.user_id)}/PlayedItems/${id}`, { method: played ? "POST" : "DELETE" });
    return result(config, { ok: true, played });
  }
  if (path === "/jellyfin/playback" && method === "post") {
    const action = String(body?.action || "").toLowerCase();
    const allowed = { play: "Unpause", pause: "Pause", stop: "Stop", next: "NextTrack", previous: "PreviousTrack" };
    if (!allowed[action] || !body?.session_id) throw new Error("Select a device and a playback command.");
    await jfRequest(`/Sessions/${encodeURIComponent(body.session_id)}/Playing/${allowed[action]}`, { method: "POST" });
    return result(config, { ok: true });
  }
  if (path === "/jellyfin/play" && method === "post") {
    if (!body?.session_id || !body?.item_id) throw new Error("Select a device and a title first.");
    const cfg = await jfConfig();
    if (!cfg) throw new Error("Connect Jellyfin in Settings first.");
    const item = body.resume ? await jfRequest(`/Users/${encodeURIComponent(cfg.user_id)}/Items/${encodeURIComponent(body.item_id)}`) : null;
    await jfRequest(`/Sessions/${encodeURIComponent(body.session_id)}/Playing`, {
      method: "POST", params: { playCommand: "PlayNow", itemIds: body.item_id, startPositionTicks: Number(item?.UserData?.PlaybackPositionTicks) || 0 }
    });
    return result(config, { ok: true });
  }
  if (path === "/jellyfin/image" || path.startsWith("/jellyfin/image/")) {
    // Binary images require a native binary bridge, not a JSON Axios adapter.
    const error = new Error("Jellyfin images need the native image bridge.");
    error.code = "ERR_OFFLINE_UNIMPLEMENTED";
    throw error;
  }
  if (path === "/config" && method === "get") return result(config, {});
  // Prevent silent calls to a missing backend and misleading success states.
  const error = new Error(`Offline API not yet implemented: ${method.toUpperCase()} ${path}`);
  error.code = "ERR_OFFLINE_UNIMPLEMENTED";
  return Promise.reject(error);
}

const writeQueues = new Map();
export function offlineAdapter(config) {
  const method = (config.method || 'get').toLowerCase();
  const path = String(config.url || '').split('?')[0];
  if (method === 'get' || (!/^\/(watchlist|shares|indexers|tracking|prefs|backup|metadata)(?:\/|$)/.test(path) && path!=='/jellyfin/sync')) return executeAdapter(config);
  const account = String(config.headers?.get?.('X-TorBox-Key') || config.headers?.['X-TorBox-Key'] || 'guest');
  const previous = writeQueues.get(account) || Promise.resolve();
  const next = previous.catch(() => {}).then(() => executeAdapter(config));
  writeQueues.set(account, next);
  const clear = () => { if (writeQueues.get(account) === next) writeQueues.delete(account); };
  next.then(clear, clear);
  return next;
}
