import { act, deferred, flush, mount, until } from "../testUtils";
import React from "react";
import { AppProvider, useApp } from "./AppContext";
import { api, checkCached } from "../lib/api";
import { accountQueryKey } from "../lib/accountQueries";
import { useHomePreferences } from "../lib/homePreferences";
import { useConnections } from "../lib/useConnections";
import { useAvailability } from "../lib/useAvailability";
import { fetchStreams } from "../lib/torrentio";

jest.mock("../lib/api", () => ({
  KEY_STORAGE: "tb_api_key",
  getKey: () => global.localStorage.getItem("tb_api_key") || "",
  errMsg: (error) => error?.message || "Connection error",
  isCancelled: (error) => error?.code === "ERR_CANCELED",
  api: { get: jest.fn(), post: jest.fn(), put: jest.fn(), delete: jest.fn() },
  checkCached: jest.fn(async (hashes, config) => {
    const { data } = await require("../lib/api").api.post("/torbox/cached", { hashes }, config);
    return new Set(data.cached);
  }),
}));
jest.mock("../lib/torrentio", () => ({ fetchStreams: jest.fn() }));

let view, app, preferences, connections, availability;
const DEFAULTS = [{ id: "first", on: true }, { id: "second", on: false }];
const ITEMS = [{ imdb_id: "tt0133093", type: "movie" }];

function Probe({ extras = false }) {
  app = useApp();
  return extras ? <Extras /> : null;
}
function Extras() {
  preferences = useHomePreferences(DEFAULTS);
  connections = useConnections();
  availability = useAvailability(ITEMS, Boolean(app.key), 0);
  return null;
}

function response(path, config) {
  const key = config.headers["X-TorBox-Key"];
  const data = {
    "/torbox/me": { email: `${key}@example.invalid` },
    "/torbox/torrents": [{ id: key, hash: key }],
    "/watchlist": [], "/tracking/state": [], "/jellyfin": { connected: false },
    "/indexers": [], "/prefs": { home: [] },
  }[path];
  return Promise.resolve({ data });
}

beforeEach(() => {
  localStorage.clear(); sessionStorage.clear();
  localStorage.setItem("tb_api_key", "account-a");
  jest.clearAllMocks();
  api.get.mockImplementation(response);
  api.post.mockResolvedValue({ data: { cached: [] } });
  checkCached.mockImplementation(async (hashes, config) => {
    const { data } = await api.post("/torbox/cached", { hashes }, config);
    return new Set(data.cached);
  });
  api.put.mockResolvedValue({ data: { home: [] } });
  api.delete.mockResolvedValue({ data: { ok: true } });
  fetchStreams.mockResolvedValue([{ hash: "hash" }]);
});
afterEach(async () => { if (view) await view.close(); view = null; jest.restoreAllMocks(); });

const start = async (extras = false) => {
  view = await mount(<React.StrictMode><AppProvider><Probe extras={extras} /></AppProvider></React.StrictMode>);
  await until(() => app.me && !app.libLoading && !app.watchLoading);
};

test("key replacement clears old state immediately and ignores delayed old responses", async () => {
  const oldLibrary = deferred();
  const newLibrary = deferred();
  let oldSignal;
  api.get.mockImplementation((path, config) => {
    if (path === "/torbox/torrents") {
      if (config.headers["X-TorBox-Key"] === "account-a") { oldSignal = config.signal; return oldLibrary.promise; }
      return newLibrary.promise;
    }
    return response(path, config);
  });
  view = await mount(<AppProvider><Probe /></AppProvider>);
  const oldSession = app.session;
  await act(async () => app.setKey("account-b"));
  expect(app.library).toEqual([]);
  expect(app.me?.email).not.toBe("account-a@example.invalid");
  expect(oldSignal.aborted).toBe(true);
  expect(view.client.getQueriesData({ queryKey: ["account", oldSession.scope] })).toEqual([]);
  await act(async () => oldLibrary.resolve({ data: [{ id: "old-private-torrent" }] }));
  expect(app.library).toEqual([]);
  await act(async () => newLibrary.resolve({ data: [{ id: "new-torrent" }] }));
  await until(() => app.library.length > 0);
  expect(app.library[0].id).toBe("new-torrent");
});

test("logout clears all account collections, errors and cached credentials", async () => {
  await start();
  const oldSession = app.session;
  await act(async () => app.setKey(""));
  expect(app.key).toBe("");
  expect(app.me).toBeNull();
  expect(app.library).toEqual([]);
  expect(app.watchlist).toEqual([]);
  expect(app.tracking.size).toBe(0);
  expect(app.meError).toBe("");
  expect(oldSession.controller.signal.aborted).toBe(true);
  expect(view.client.getQueriesData({ queryKey: ["account", oldSession.scope] })).toEqual([]);
  expect(JSON.stringify(view.client.getQueryCache().getAll().map((query) => query.queryKey))).not.toContain("account-a");
});

test("storage events switch the account in another tab and clear previous data", async () => {
  await start();
  await act(async () => {
    localStorage.setItem("tb_api_key", "account-b");
    window.dispatchEvent(new StorageEvent("storage", { key: "tb_api_key", newValue: "account-b" }));
  });
  await until(() => app.me?.email === "account-b@example.invalid");
  expect(app.library.every((item) => item.id === "account-b")).toBe(true);
});

test("authentication and library errors settle without automatic retries and can be retried", async () => {
  api.get.mockImplementation((path, config) => path === "/torbox/torrents" ? Promise.reject(new Error("TorBox unavailable")) : response(path, config));
  view = await mount(<AppProvider><Probe /></AppProvider>);
  await until(() => app.libraryError);
  expect(app.libLoading).toBe(false);
  expect(api.get.mock.calls.filter(([path]) => path === "/torbox/torrents")).toHaveLength(1);
  api.get.mockImplementation(response);
  await act(async () => app.refreshLibrary());
  await until(() => !app.libraryError && app.library.length > 0);
});

test("failed watchlist reads are explicit errors and recover through refresh", async () => {
  api.get.mockImplementation((path, config) => path === "/watchlist" ? Promise.reject(new Error("Watchlist unavailable")) : response(path, config));
  view = await mount(<AppProvider><Probe /></AppProvider>);
  await until(() => app.watchError);
  expect(app.watchLoading).toBe(false);
  api.get.mockImplementation(response);
  await act(async () => app.refreshWatchlist());
  await until(() => !app.watchError);
});

test("duplicate bookmark clicks share one mutation and failure does not invent saved data", async () => {
  await start();
  const pending = deferred();
  api.post.mockReturnValue(pending.promise);
  let one, two;
  await act(async () => {
    one = app.toggleWatch({ id: "tt0133093", type: "movie", name: "The Matrix" });
    two = app.toggleWatch({ id: "tt0133093", type: "movie", name: "The Matrix" });
  });
  expect(one).toBe(two);
  await until(() => api.post.mock.calls.length > 0);
  expect(api.post).toHaveBeenCalledTimes(1);
  const failure = one.catch((error) => error.message);
  await act(async () => pending.reject(new Error("Save failed")));
  expect(await failure).toBe("Save failed");
  expect(app.watchlist).toEqual([]);
  expect(api.post).toHaveBeenCalledTimes(1);
});

test("an in-flight bookmark cannot populate a replacement account", async () => {
  await start();
  const pending = deferred();
  api.post.mockReturnValue(pending.promise);
  let result;
  await act(async () => { result = app.toggleWatch({ id: "tt0133093", type: "movie" }).catch((error) => error.code); });
  await until(() => api.post.mock.calls.length > 0);
  const config = api.post.mock.calls[0][2];
  await act(async () => app.setKey("account-b"));
  expect(config.signal.aborted).toBe(true);
  await act(async () => pending.resolve({ data: { imdb_id: "tt0133093" } }));
  expect(await result).toBe("ERR_CANCELED");
  expect(app.watchlist).toEqual([]);
});

test("shared connection checks reuse existing account reads", async () => {
  await start(true);
  await until(() => !connections.busy);
  expect(connections.st.tb).toBe("ok");
  // StrictMode may cancel the first mount; subsequent observers must reuse the settled read.
  const count = api.get.mock.calls.filter(([path]) => path === "/torbox/me").length;
  await flush();
  expect(api.get.mock.calls.filter(([path]) => path === "/torbox/me")).toHaveLength(count);
});

test("guest connections do not make authenticated requests or wait forever", async () => {
  localStorage.removeItem("tb_api_key");
  view = await mount(<AppProvider><Probe extras /></AppProvider>);
  expect(connections.st).toEqual({ tb: "bad", jf: "bad", ix: "bad", ixCount: 0 });
  await act(async () => connections.check());
  expect(api.get).not.toHaveBeenCalled();
});

test("corrupt legacy Home preferences cannot crash the application", async () => {
  localStorage.removeItem("tb_api_key");
  localStorage.setItem("home_prefs", "{invalid");
  view = await mount(<AppProvider><Probe extras /></AppProvider>);
  expect(preferences[0]).toEqual(DEFAULTS);
  expect(localStorage.getItem("home_prefs")).toBe("{invalid");
});

test("Home preferences are account-scoped and failed saves show feedback", async () => {
  api.get.mockImplementation((path, config) => path === "/prefs"
    ? Promise.resolve({ data: { home: [{ id: "first", on: config.headers["X-TorBox-Key"] === "account-a" }] } })
    : response(path, config));
  await start(true);
  await until(() => !preferences[2].loading);
  api.put.mockRejectedValue(new Error("Preferences were not saved"));
  await act(async () => preferences[1]([{ id: "first", on: false }]));
  await until(() => preferences[2].error);
  expect(preferences[2].error).toBe("Preferences were not saved");
  expect(api.put).toHaveBeenCalledTimes(1);
  await act(async () => app.setKey("account-b"));
  await until(() => !preferences[2].loading);
  expect(preferences[0][0].on).toBe(false);
  expect(preferences[2].error).toBe("");
});

test("rapid preference edits save in order and keep the newest optimistic state", async () => {
  await start(true);
  await until(() => !preferences[2].loading);
  const first = deferred();
  api.put.mockImplementationOnce(() => first.promise).mockResolvedValue({ data: {} });
  await act(async () => preferences[1]([{ id: "first", on: false }]));
  await until(() => api.put.mock.calls.length === 1);
  await act(async () => preferences[1]([{ id: "first", on: true }, { id: "second", on: true }]));
  // Query and mutation observers notify in separate batches. Verify the newest
  // optimistic UI while the older network save is still pending.
  await until(() => preferences[0].find(row => row.id === "second")?.on === true);
  expect(view.client.getQueryData(accountQueryKey(app.session, "/prefs")).home[1].on).toBe(true);
  expect(api.put).toHaveBeenCalledTimes(1);
  await act(async () => first.resolve({ data: {} }));
  await until(() => api.put.mock.calls.length === 2 && !preferences[2].saving && preferences[0].find(row => row.id === "second")?.on === true);
  expect(api.put.mock.calls[0][1].home[0].on).toBe(false);
  expect(api.put.mock.calls[1][1].home[1].on).toBe(true);
  expect(preferences[0][1].on).toBe(true);
});

test("availability ignores legacy global cache and is rechecked for a new account", async () => {
  sessionStorage.setItem("watch_avail", JSON.stringify({ tt0133093: 99 }));
  api.post.mockImplementation((path, body, config) => Promise.resolve({ data: { cached: config.headers["X-TorBox-Key"] === "account-a" ? ["hash"] : [] } }));
  await start(true);
  await until(() => availability[0].tt0133093 !== undefined);
  expect(view.client.getQueryCache().findAll({ queryKey: ["account", app.session.scope, "availability"] }).map((query) => query.state.error?.message)).toEqual([undefined]);
  expect(availability[0].tt0133093).toBe(1);
  const oldSession = app.session;
  await act(async () => app.setKey("account-b"));
  await until(() => availability[0].tt0133093 !== undefined);
  expect(availability[0].tt0133093).toBe(0);
  expect(view.client.getQueriesData({ queryKey: ["account", oldSession.scope] })).toEqual([]);
});

test("failed availability checks stay unknown and refresh retries them", async () => {
  api.post.mockRejectedValue(new Error("Cache unavailable"));
  await start(true);
  await until(() => availability[0].tt0133093 === -1);
  api.post.mockResolvedValue({ data: { cached: ["hash"] } });
  await act(async () => availability[1]());
  await until(() => availability[0].tt0133093 === 1);
});

test("blocked credential storage leaves the verified current account intact", async () => {
  await start();
  jest.spyOn(Storage.prototype, "setItem").mockImplementation(() => { throw new Error("quota"); });
  expect(() => app.setKey("account-b")).toThrow(/storage is unavailable/);
  expect(app.key).toBe("account-a");
  expect(view.client.getQueryData(accountQueryKey(app.session, "/torbox/me"))).toBeDefined();
});

test('watchlist Undo uses an explicit expected state and rejects stale undo instead of toggling newer changes',async()=>{
  await start();const title={id:'tt0133093',type:'movie',name:'The Matrix'};
  api.post.mockResolvedValue({data:{imdb_id:title.id,type:'movie',name:title.name}});
  await act(async()=>{await app.toggleWatch(title);});await until(()=>app.watchIds.has(title.id));expect(app.watchIds.has(title.id)).toBe(true);
  await act(async()=>{await app.toggleWatch(title,{desired:false,expectedCurrent:true});});await until(()=>!app.watchIds.has(title.id));expect(app.watchIds.has(title.id)).toBe(false);
  await act(async()=>{await expect(app.toggleWatch(title,{desired:false,expectedCurrent:true})).rejects.toThrow('Watchlist changed');});
  expect(api.delete).toHaveBeenCalledTimes(1);
});


test('watchlist Undo rejects an intervening remove-and-readd even when the final boolean matches',async()=>{
  await start();const title={id:'tt0133093',type:'movie',name:'The Matrix'};
  api.post.mockResolvedValue({data:{imdb_id:title.id,type:'movie',name:title.name}});
  await act(async()=>{await app.toggleWatch(title);});const undo=app.watchUndo(title,true);
  await act(async()=>{await app.toggleWatch(title);await app.toggleWatch(title);});
  await act(async()=>{await expect(undo()).rejects.toThrow('Watchlist changed');});
  await until(()=>app.watchIds.has(title.id));expect(app.watchIds.has(title.id)).toBe(true);
});


test('foreground progress refresh is throttled and uses the captured account session',async()=>{
  api.get.mockImplementation((path,config)=>path==='/jellyfin'?Promise.resolve({data:{connected:true,last_sync:new Date().toISOString()}}):response(path,config));
  await start();await until(()=>api.get.mock.calls.some(([path])=>path==='/jellyfin'));
  await act(async()=>{window.dispatchEvent(new Event('dmm-foreground'));window.dispatchEvent(new Event('dmm-foreground'));});
  await until(()=>api.post.mock.calls.some(([path])=>path==='/jellyfin/sync'));
  const calls=api.post.mock.calls.filter(([path])=>path==='/jellyfin/sync');expect(calls).toHaveLength(1);expect(calls[0][2].headers['X-TorBox-Key']).toBe('account-a');
});

test('observed Jellyfin watched state is app state and is cleared when the server or account changes',async()=>{
  view=await mount(<AppProvider><Probe/></AppProvider>);
  await until(()=>app.key==='account-a');
  await act(async()=>app.rememberTitleAvailability('tt123','series',{state:'available',title_state:{played:true},episodes:[]},Date.now(),['tmdb:42']));
  expect(app.tracking.get('tt123').played).toBe(true);expect(app.tracking.get('tmdb:42').played).toBe(true);
  const {filterKnownTitles}=require('../lib/experience');expect(filterKnownTitles([{id:'tt123',type:'series'}],app.tracking,null,{hideWatched:true})).toHaveLength(0);
  await act(async()=>window.dispatchEvent(new Event('dmm-jellyfin-change')));
  await until(()=>!app.tracking.has('tt123'));
  await act(async()=>app.rememberTitleAvailability('tt123','series',{state:'available',title_state:{played:true}},Date.now()));
  await act(async()=>app.setKey('account-b'));
  await until(()=>app.key==='account-b');expect(app.tracking.has('tt123')).toBe(false);
});

test('visible-app polling refreshes shared watched state and pauses while the native app is inactive',async()=>{
 let poll,remotePlayed=false;
 jest.spyOn(global,'setInterval').mockImplementation((callback,ms)=>{if(ms===60000)poll=callback;return 1;});
 const now=Date.now();let clock=now;jest.spyOn(Date,'now').mockImplementation(()=>clock);
 api.get.mockImplementation((path,config)=>path==='/jellyfin'?Promise.resolve({data:{connected:true}}):path==='/tracking/state'?Promise.resolve({data:[{imdb_id:'tt123',type:'movie',played:remotePlayed}]}):response(path,config));
 await start();await until(()=>app.tracking.has('tt123'));await until(()=>api.post.mock.calls.some(([path])=>path==='/jellyfin/sync'));
 await flush();remotePlayed=true;clock+=61000;
 await act(async()=>poll());await until(()=>app.tracking.get('tt123')?.played===true);
 const count=api.post.mock.calls.length;
 await act(async()=>window.dispatchEvent(new CustomEvent('stak-app-active',{detail:false})));clock+=61000;
 await act(async()=>poll());expect(api.post.mock.calls).toHaveLength(count);
 await act(async()=>window.dispatchEvent(new Event('dmm-foreground')));await until(()=>api.post.mock.calls.length>count);
});
