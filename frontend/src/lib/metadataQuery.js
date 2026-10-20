import { useQuery } from '@tanstack/react-query';
import { api } from './api';
import {metadataSnapshot,metadataAccount,useMetadataRevision} from './metadata/runtime';
export function metadataOptions(path, params = {}) {
  const namespace=metadataSnapshot(),account=metadataAccount();
  return {
    queryKey: ['metadata',namespace,path,params],
    queryFn: async ({ signal }) => (await api.get(path, {params,signal,headers:{'X-TorBox-Key':account || ''}})).data,
    staleTime:24*60*60_000,gcTime:30*60_000,retry:false,refetchOnWindowFocus:false,
  };
}
export function useMetadataQuery(path,params={},options={}){useMetadataRevision();return useQuery({...metadataOptions(path,params),...options});}
