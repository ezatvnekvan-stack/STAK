import { IS_CAPACITOR_OFFLINE } from "./api";
import { useAccountQuery } from "./useAccountQuery";
import { useApp } from "../context/AppContext";

// Shared account-scoped reads; changing accounts remounts consumers and cancels old queries.
export function useConnections() {
  const { key } = useApp();
  const tb = useAccountQuery("/torbox/me");
  const jf = useAccountQuery("/jellyfin");
  const ix = useAccountQuery("/indexers");
  const list = Array.isArray(ix.data) ? ix.data.filter((item) => item.enabled) : [];
  const state = (query, ok, needsKey = true) => !key && needsKey ? "bad" : query.isFetching || query.isPending ? "wait" : !query.error && ok ? "ok" : "bad";
  const check = () => !key && !IS_CAPACITOR_OFFLINE ? Promise.resolve() : Promise.all([
    ...(key ? [tb.refetch({ cancelRefetch: false })] : []), jf.refetch({ cancelRefetch: false }), ix.refetch({ cancelRefetch: false }),
  ]);
  return {
    st: { tb: state(tb, Boolean(tb.data)), jf: state(jf, jf.data?.connected, !IS_CAPACITOR_OFFLINE), ix: state(ix, list.length > 0, !IS_CAPACITOR_OFFLINE), ixCount: list.length },
    busy: tb.isFetching || jf.isFetching || ix.isFetching,
    check,
  };
}
