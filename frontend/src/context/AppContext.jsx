import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, IS_CAPACITOR_OFFLINE, errMsg, getKey, isCancelled, KEY_STORAGE } from "../lib/api";
import { accountQueryKey, accountQueryOptions, accountRequest, createAccountSession, retireAccount } from "../lib/accountQueries";
import { removeStored, writeStored } from "../lib/storage";

import {effectiveTracking} from "../lib/watchedState";

const Ctx = createContext(null);
const EMPTY = [];
const watchFingerprint=record=>JSON.stringify(record?[record.imdb_id,record.type,record.name || '',record.poster || '',String(record.year || ''),String(record.rating || ''),record.genres || [],record.added_at || record.created_at || '']:null);

function AccountProvider({ session, setKey, children }) {
  const queryClient = useQueryClient();
  const meQuery = useQuery(accountQueryOptions(session, "/torbox/me"));
  const libraryQuery = useQuery(accountQueryOptions(session, "/torbox/torrents"));
  const watchQuery = useQuery(accountQueryOptions(session, "/watchlist"));
  const trackingQuery = useQuery(accountQueryOptions(session, "/tracking/state"));
  const jellyfinQuery = useQuery(accountQueryOptions(session, "/jellyfin"));
  const catalogQuery=useQuery({...accountQueryOptions(session,'/jellyfin/catalog'),enabled:Boolean(IS_CAPACITOR_OFFLINE) && Boolean(jellyfinQuery.data?.connected),staleTime:300000});
  const library = useMemo(() => Array.isArray(libraryQuery.data) ? libraryQuery.data.filter(x => x && typeof x === "object") : EMPTY, [libraryQuery.data]);
  const watchlist = useMemo(() => Array.isArray(watchQuery.data) ? watchQuery.data.filter(x => x && typeof x === "object") : EMPTY, [watchQuery.data]);
  const [titleAvailability,setTitleAvailability]=useState(()=>new Map());
  const rememberTitleAvailability=useCallback((id,type,data,observedAt,ids=[])=>{
    if(!id || !['available','unavailable'].includes(data.state))return;
    const value={id,type,ids,checked_at:observedAt?new Date(observedAt).toISOString():new Date().toISOString(),played:data.title_state?.played,last_played:data.title_state?.last_played || null,episodes:data.episodes || [],excluded:data.state==='unavailable'};
    setTitleAvailability(previous=>{if(JSON.stringify(previous.get(id))===JSON.stringify(value))return previous;const next=new Map(previous);next.delete(id);next.set(id,value);if(next.size>200)next.delete(next.keys().next().value);return next;});
  },[]);
  const tracking=useMemo(()=>effectiveTracking(trackingQuery.data,catalogQuery.error?null:catalogQuery.data,[...titleAvailability.values()]),[trackingQuery.data,catalogQuery.data,catalogQuery.error,titleAvailability]);
  const libraryHashes = useMemo(() => new Set(library.map((t) => String(t.hash || "").toLowerCase())), [library]);
  const watchIds = useMemo(() => new Set(watchlist.map((w) => w.imdb_id)), [watchlist]);
  const pendingWatch = useRef(new Map()),watchVersions=useRef(new Map());
  const [cachedTitles,setCachedTitles]=useState(()=>new Map());
  const rememberCachedTitle=useCallback(id=>setCachedTitles(previous=>{const next=new Map(previous);next.delete(id);next.set(id,Date.now());if(next.size>100)next.delete(next.keys().next().value);return next;}),[]);
  const { refetch: refetchLibrary } = libraryQuery;
  const { refetch: refetchWatch } = watchQuery;
  const { refetch: refetchTracking } = trackingQuery;

  const refreshLibrary = useCallback(() => session.key
    ? refetchLibrary({ throwOnError: true, cancelRefetch: false })
    : Promise.resolve(), [session.key, refetchLibrary]);
  const refreshWatchlist = useCallback(() => (session.key || IS_CAPACITOR_OFFLINE)
    ? refetchWatch({ throwOnError: true, cancelRefetch: false })
    : Promise.resolve(), [session.key, refetchWatch]);
  const refreshTracking = useCallback(() => (session.key || IS_CAPACITOR_OFFLINE)
    ? refetchTracking({ throwOnError: true, cancelRefetch: false })
    : Promise.resolve(), [session.key, refetchTracking]);

  const { mutateAsync: mutateWatch } = useMutation({
    mutationKey: ["account", session.scope, "watchlist"],
    scope: { id: `watchlist-${session.scope}` },
    retry: false,
    mutationFn: async (m) => {
      const config = accountRequest(session);
      if (!session.key && !IS_CAPACITOR_OFFLINE) throw new Error("Add your TorBox API key first");
      const imdb_id = m.imdb_id || m.id;
      // Read the current cache when this serialized mutation starts, not an old render.
      const current = await queryClient.ensureQueryData(accountQueryOptions(session, "/watchlist"));
      await queryClient.cancelQueries({ queryKey: accountQueryKey(session, "/watchlist") });
      accountRequest(session);
      const exists=current.some(w=>w.imdb_id===imdb_id);
      if((m.expectedVersion!==undefined && (watchVersions.current.get(imdb_id) || 0)!==m.expectedVersion) || (m.expectedRecord!==undefined && watchFingerprint(current.find(w=>w.imdb_id===imdb_id))!==m.expectedRecord))throw new Error('Watchlist changed since that action. Check its current state.');
      if(typeof m.expectedCurrent==='boolean' && exists!==m.expectedCurrent)throw new Error('Watchlist changed since that action. Check its current state.');
      if(typeof m.desired==='boolean' && exists===m.desired)return exists;
      if (exists) {
        await api.delete(`/watchlist/${imdb_id}`, config);
        accountRequest(session);
        queryClient.setQueryData(accountQueryKey(session, "/watchlist"), (list = []) => list.filter((w) => w.imdb_id !== imdb_id));
        watchVersions.current.set(imdb_id,(watchVersions.current.get(imdb_id) || 0)+1);
        return false;
      }
      const { data } = await api.post("/watchlist", {
        imdb_id, type: m.type, name: m.name, poster: m.poster || "",
        genres: Array.isArray(m.genres) ? m.genres.filter(x => typeof x === "string") : [],
        year: String(m.releaseInfo || m.year || ""), rating: String(m.imdbRating || ""),
      }, config);
      accountRequest(session);
      queryClient.setQueryData(accountQueryKey(session, "/watchlist"), (list = []) => [data, ...list.filter((w) => w.imdb_id !== imdb_id)]);
      watchVersions.current.set(imdb_id,(watchVersions.current.get(imdb_id) || 0)+1);
      return true;
    },
  });
  const toggleWatch = useCallback((m, options={}) => {
    const id = m.imdb_id || m.id;
    if (pendingWatch.current.has(id)) return pendingWatch.current.get(id);
    const promise = mutateWatch({...m,...options}).finally(() => pendingWatch.current.delete(id));
    pendingWatch.current.set(id, promise);
    return promise;
  }, [mutateWatch]);

  const watchUndo=useCallback((item,added)=>{
    const id=item.imdb_id || item.id,expectedVersion=watchVersions.current.get(id) || 0;
    const expectedRecord=watchFingerprint((queryClient.getQueryData(accountQueryKey(session,'/watchlist')) || []).find(record=>record.imdb_id===id));
    return ()=>toggleWatch(item,{desired:!added,expectedCurrent:added,expectedVersion,expectedRecord});
  },[queryClient,session,toggleWatch]);

  const [syncError, setSyncError] = useState("");
  const foregroundSync=useRef({at:0,pending:false});
  useEffect(()=>{
    let nativeActive=true,disposed=false,failures=0;
    const refresh=()=>{
      const sync=foregroundSync.current;
      if(disposed || !nativeActive || document.visibilityState==='hidden' || navigator.onLine===false || !jellyfinQuery.data?.connected || sync.pending || Date.now()-sync.at<Math.min(300000,60000*2**failures))return;
      sync.pending=true;sync.at=Date.now();
      api.post('/jellyfin/sync',undefined,accountRequest(session)).then(()=>{
        accountRequest(session);if(disposed)return;failures=0;setSyncError('');setTitleAvailability(new Map());
        return queryClient.invalidateQueries({predicate:query=>query.queryKey[0]==='jellyfin-library' || (query.queryKey[0]==='account' && query.queryKey[1]===session.scope && /^\/(tracking|jellyfin)(?:\/|$)/.test(query.queryKey[2] || ''))});
      }).catch(error=>{if(!disposed && !isCancelled(error)){failures++;setSyncError(`Jellyfin progress sync failed: ${errMsg(error)}`);}}).finally(()=>{sync.pending=false;});
    };
    const resume=()=>{nativeActive=true;refresh();},active=event=>{nativeActive=event.detail!==false;if(nativeActive)refresh();};
    refresh();const timer=setInterval(refresh,60000);
    window.addEventListener('dmm-foreground',resume);window.addEventListener('stak-app-active',active);
    document.addEventListener('visibilitychange',refresh);window.addEventListener('online',refresh);
    return()=>{disposed=true;clearInterval(timer);window.removeEventListener('dmm-foreground',resume);window.removeEventListener('stak-app-active',active);document.removeEventListener('visibilitychange',refresh);window.removeEventListener('online',refresh);};
  },[jellyfinQuery.data?.connected,queryClient,session]);
  useEffect(() => {
    const changed = () => {
      foregroundSync.current.at=0;setSyncError('');setTitleAvailability(new Map());
      queryClient.cancelQueries({ queryKey: ['account', session.scope] }).then(() => {queryClient.setQueryData(accountQueryKey(session,'/jellyfin/catalog'),null);return queryClient.invalidateQueries({ queryKey: ['account', session.scope] });}).catch(error => setSyncError(errMsg(error)));
    };
    window.addEventListener('dmm-jellyfin-change', changed);
    return () => window.removeEventListener('dmm-jellyfin-change', changed);
  }, [session, queryClient]);

  return (
    <Ctx.Provider value={{
      key: session.key, session, setKey,
      me: meQuery.data || null, meError: meQuery.error ? errMsg(meQuery.error) : "", meLoading: meQuery.isFetching,
      library, libLoading: libraryQuery.isFetching, libraryError: libraryQuery.error ? errMsg(libraryQuery.error) : "", refreshLibrary, libraryHashes,
      watchlist, watchIds, toggleWatch, watchUndo, watchLoading: watchQuery.isPending && (Boolean(session.key) || IS_CAPACITOR_OFFLINE),
      watchError: watchQuery.error ? errMsg(watchQuery.error) : "", refreshWatchlist,
      cachedTitles,rememberCachedTitle,
      catalog:catalogQuery.error?null:catalogQuery.data, catalogError:catalogQuery.error, refreshCatalog:catalogQuery.refetch,
      tracking, rememberTitleAvailability, refreshTracking, trackingError: trackingQuery.error ? errMsg(trackingQuery.error) : syncError,
    }}>
      {children}
    </Ctx.Provider>
  );
}

export function AppProvider({ children }) {
  const queryClient = useQueryClient();
  const [session, setSession] = useState(() => createAccountSession(getKey()));
  const current = useRef(session);
  const switchAccount = useCallback((key) => {
    if (key === current.current.key) return;
    retireAccount(queryClient, current.current);
    const next = createAccountSession(key);
    current.current = next;
    setSession(next);
  }, [queryClient]);
  const setKey = useCallback((key) => {
    const next = String(key || "").trim();
    const saved = getKey() === next || (next ? writeStored(KEY_STORAGE, next) : removeStored(KEY_STORAGE));
    if (!saved) throw new Error("Browser storage is unavailable. Allow storage to save your TorBox connection.");
    switchAccount(next);
  }, [switchAccount]);

  useEffect(() => queryClient.getMutationCache().subscribe((event) => {
    const mutation = event.mutation;
    const mutationKey = mutation?.options.mutationKey;
    if (event.type === "updated" && mutationKey?.[0] === "account" && mutationKey[1] !== current.current.scope && mutation.state.status !== "pending") {
      queryClient.getMutationCache().remove(mutation);
    }
  }), [queryClient]);

  useEffect(() => {
    const onStorage = (event) => {
      if (event.key === KEY_STORAGE || event.key === null) switchAccount(getKey());
    };
    window.addEventListener("storage", onStorage);
    return () => window.removeEventListener("storage", onStorage);
  }, [switchAccount]);

  // Remount account-dependent screens immediately so local state cannot cross logins.
  return <AccountProvider key={session.scope} session={session} setKey={setKey}>{children}</AccountProvider>;
}

export const useApp = () => useContext(Ctx);
