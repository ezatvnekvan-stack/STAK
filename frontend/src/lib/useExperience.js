import { useCallback, useEffect, useRef, useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { useApp } from '../context/AppContext';
import { api, errMsg, isCancelled } from './api';
import { accountQueryKey, accountRequest } from './accountQueries';
import { useAccountQuery } from './useAccountQuery';
import { experienceSection } from './experience';

export function useExperience(section, defaults) {
  const {session}=useApp(),client=useQueryClient();
  const query=useAccountQuery('/prefs/experience',{staleTime:Infinity});
  const hasSection=Object.prototype.hasOwnProperty.call(query.data || {},section);
  const [value,setValue]=useState(()=>experienceSection(section,query.data?.[section] || defaults));
  const current=useRef(value),edited=useRef(false),hydrated=useRef(hasSection),queue=useRef(Promise.resolve()),revision=useRef(0);
  useEffect(()=>{
    if(hasSection && !hydrated.current){hydrated.current=true;if(!edited.current){current.current=experienceSection(section,query.data[section]);setValue(current.current);}}
  },[hasSection,query.data,section]);
  const change=useCallback(next=>{
    edited.current=true;
    const updated=experienceSection(section,typeof next==='function'?next(current.current):next);
    current.current=updated;setValue(updated);
    const cacheKey=accountQueryKey(session,'/prefs/experience');
    client.cancelQueries({queryKey:cacheKey});
    client.setQueryData(cacheKey,previous=>({...previous,[section]:updated}));
    const version=++revision.current;
    const task=queue.current.catch(()=>{}).then(()=>api.put('/prefs/experience',{section,value:updated},accountRequest(session))).then(({data})=>{accountRequest(session);if(version===revision.current)client.setQueryData(accountQueryKey(session,'/prefs/experience'),data);});
    queue.current=task;task.catch(error=>{if(!isCancelled(error))toast.error(`Preference was not saved: ${errMsg(error)}`);});
  },[client,section,session]);
  return [value,change];
}
