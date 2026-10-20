import {useSyncExternalStore} from 'react';
import {getKey} from '../api';
let revision=0,account,lastAccount,scope=0;
const subscribers=new Set();
export function metadataSnapshot(){account=typeof getKey==='function'?getKey():'';if(lastAccount!==account){lastAccount=account;scope++;}return `${scope}:${revision}`;}
export function metadataChanged(){revision++;subscribers.forEach(fn=>fn());window.dispatchEvent(new Event('stak-metadata-changed'));}
export function useMetadataRevision(){return useSyncExternalStore(fn=>{subscribers.add(fn);return()=>subscribers.delete(fn);},metadataSnapshot);}
export function metadataAccount(){return typeof getKey==='function'?getKey():'';}
