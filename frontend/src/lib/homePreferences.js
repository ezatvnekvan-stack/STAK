import { useCallback, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useApp } from "../context/AppContext";
import { api, IS_CAPACITOR_OFFLINE, errMsg } from "./api";
import { accountQueryKey, accountQueryOptions, accountRequest } from "./accountQueries";
import { readJSON, writeStored } from "./storage";

export function normalizeHome(value, defaults) {
  const seen = new Set();
  const stored = Array.isArray(value) ? value.filter((row) => {
    if (!row || row.id === "torbox_recent" || typeof row.id !== "string" || !row.id || typeof row.on !== "boolean" || seen.has(row.id)) return false;
    seen.add(row.id);
    return true;
  }).map((row) => ({ id: row.id, on: row.on })) : [];
  return [...stored, ...defaults.filter((row) => !seen.has(row.id)).map((row) => ({ ...row }))];
}

export function useHomePreferences(defaults) {
  const { session } = useApp();
  const localAccount = Boolean(session.key) || IS_CAPACITOR_OFFLINE;
  const queryClient = useQueryClient();
  const query = useQuery(accountQueryOptions(session, "/prefs"));
  // Keep the legacy guest preference value intact. Authenticated preferences come from that account.
  const [guestHome, setGuestHome] = useState(() => normalizeHome(readJSON("home_prefs", null, Array.isArray), defaults));
  const [storageError, setStorageError] = useState("");
  const mutation = useMutation({
    mutationKey: ["account", session.scope, "prefs"],
    scope: { id: `prefs-${session.scope}` },
    retry: false,
    mutationFn: (next) => api.put("/prefs", { home: next }, accountRequest(session)),
    onMutate: async (next) => {
      accountRequest(session);
      await queryClient.cancelQueries({ queryKey: accountQueryKey(session, "/prefs") });
      accountRequest(session);
      const previous = queryClient.getQueryData(accountQueryKey(session, "/prefs"));
      queryClient.setQueryData(accountQueryKey(session, "/prefs"), { home: next });
      return { previous };
    },
    onError: (error, next, context) => {
      if (session.controller.signal.aborted) return;
      const current = queryClient.getQueryData(accountQueryKey(session, "/prefs"));
      // A failed older save must not roll back a newer edit.
      if (JSON.stringify(current?.home) === JSON.stringify(next)) {
        queryClient.setQueryData(accountQueryKey(session, "/prefs"), context?.previous || { home: defaults });
        queryClient.invalidateQueries({ queryKey: accountQueryKey(session, "/prefs") });
      }
    },
  });
  const { mutate } = mutation;
  const save = useCallback((value) => {
    const next = normalizeHome(value, defaults);
    if (localAccount) { mutate(next); return; }
    setGuestHome(next);
    setStorageError(writeStored("home_prefs", JSON.stringify(next)) ? "" : "Browser storage is unavailable. These preferences will last until this page closes.");
  }, [defaults, localAccount, mutate]);
  const home = localAccount ? normalizeHome(query.data?.home ?? (!session.key ? guestHome : undefined), defaults) : guestHome;
  const error = mutation.error || query.error;
  return [home, save, {
    loading: Boolean(localAccount) && query.isPending,
    saving: mutation.isPending,
    error: error ? errMsg(error) : storageError,
    retry: () => { mutation.reset(); return query.refetch(); },
  }];
}
