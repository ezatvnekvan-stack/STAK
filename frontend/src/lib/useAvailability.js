import { useEffect, useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { useApp } from "../context/AppContext";
import { checkCached, isCancelled } from "./api";
import { accountRequest } from "./accountQueries";
import { fetchStreams } from "./torrentio";

const TTL = 5 * 60 * 1000;

export function useAvailability(items, enabled, tick) {
  const { session } = useApp();
  const queryClient = useQueryClient();
  const [map, setMap] = useState({});
  const [version, setVersion] = useState(0);

  useEffect(() => {
    if (!enabled || !session.key) { setMap({}); return; }
    let live = true;
    const prefix = ["account", session.scope, "availability"];
    const known = {};
    for (const item of items) {
      const state = queryClient.getQueryState([...prefix, item.type, item.imdb_id]);
      if (state?.dataUpdatedAt > Date.now() - TTL && Number.isFinite(state.data)) known[item.imdb_id] = state.data;
    }
    setMap(known);
    const queue = items.filter((item) => !(item.imdb_id in known));
    const worker = async () => {
      while (queue.length && live) {
        const item = queue.shift();
        let count;
        try {
          count = await queryClient.fetchQuery({
            queryKey: [...prefix, item.type, item.imdb_id],
            staleTime: TTL,
            retry: false,
            queryFn: async ({ signal }) => {
              const config = accountRequest(session, { signal });
              const streamId = item.type === "series" ? `${item.imdb_id}:1:1` : item.imdb_id;
              const streams = await fetchStreams(item.type, streamId, config);
              accountRequest(session);
              return (await checkCached(streams.map((stream) => stream.hash), config)).size;
            },
          });
        } catch (error) {
          if (!live || isCancelled(error)) return;
          count = -1; // Unknown stays retryable; failed checks are not cached as a miss.
        }
        if (live) setMap((previous) => ({ ...previous, [item.imdb_id]: count }));
      }
    };
    // Preserve the existing maximum of three simultaneous title checks.
    Promise.all([worker(), worker(), worker()]);
    return () => {
      live = false;
      queryClient.cancelQueries({ queryKey: prefix });
    };
  }, [items, enabled, tick, version, session, queryClient]);

  const reset = () => {
    const queryKey = ["account", session.scope, "availability"];
    queryClient.cancelQueries({ queryKey });
    queryClient.removeQueries({ queryKey });
    setMap({});
    setVersion((value) => value + 1);
  };
  return [map, reset];
}
