import { useQuery } from "@tanstack/react-query";
import { useApp } from "../context/AppContext";
import { accountQueryOptions } from "./accountQueries";

export function useAccountQuery(path, options = {}) {
  const { session } = useApp();
  return useQuery({ ...accountQueryOptions(session, path), ...options });
}
