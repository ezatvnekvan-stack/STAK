import { api, IS_CAPACITOR_OFFLINE } from "./api";

let sequence = 0;

// An opaque, in-memory login scope: credentials never become query/storage keys.
export function createAccountSession(key) {
  return { key, scope: `login-${++sequence}`, controller: new AbortController() };
}

export const accountQueryKey = (session, path) => ["account", session.scope, path];

export function accountRequest(session, config = {}) {
  if (session.controller.signal.aborted) {
    const error = new Error("Account changed");
    error.code = "ERR_CANCELED";
    throw error;
  }
  return {
    ...config,
    signal: config.signal || session.controller.signal,
    headers: { ...config.headers, "X-TorBox-Key": session.key },
  };
}

export function accountQueryOptions(session, path) {
  return {
    queryKey: accountQueryKey(session, path),
    queryFn: async ({ signal }) => {
      const { data } = await api.get(path, accountRequest(session, { signal }));
      return data;
    },
    enabled: Boolean(session.key || (IS_CAPACITOR_OFFLINE && !path.startsWith("/torbox"))),
    staleTime: 60_000,
    refetchOnWindowFocus: false,
    // Retrying is an explicit user action; authentication errors are never retried.
    retry: false,
  };
}

export function retireAccount(queryClient, session) {
  session.controller.abort();
  const filter = { queryKey: ["account", session.scope] };
  queryClient.cancelQueries(filter);
  queryClient.removeQueries(filter);
  for (const mutation of queryClient.getMutationCache().findAll({ mutationKey: ["account", session.scope] })) {
    // Pending serial mutations must settle so their queue can advance and reject stale work.
    if (mutation.state.status !== "pending") queryClient.getMutationCache().remove(mutation);
  }
}
