import {useState} from 'react';
import {useQueryClient} from '@tanstack/react-query';
import {Capacitor} from '@capacitor/core';
import {api,errMsg} from '../lib/api';
import {useMetadataSources} from '../lib/useMetadataSources';
import {metadataChanged} from '../lib/metadata/runtime';
import {useT} from '../lib/i18n';
import NothingSelect from './NothingSelect';
import {Button} from './StakUI';
export default function MetadataSources(){
 const t=useT(),client=useQueryClient(),query=useMetadataSources(),{settings,status}=query;
 const [busy,setBusy]=useState(''),[error,setError]=useState(''),[message,setMessage]=useState(''),[keys,setKeys]=useState({}),[visible,setVisible]=useState({});
 const changed=async()=>{metadataChanged();await client.invalidateQueries({predicate:query=>['metadata','unified-discovery','streaming-providers','discover-genres','home-row'].includes(query.queryKey[0])});};
 const run=async(name,work)=>{setBusy(name);setError('');setMessage('');try{await work();await changed();}catch(e){setError(errMsg(e));await query.refetch();}finally{setBusy('');}};
 const save=(name,value)=>run(name,()=>api.put('/metadata/settings',{...settings,[name]:value}));
 const select=(name,label,options)=><label className="filter-label" key={name}>{t(label)}<NothingSelect aria-label={t(label)} data-testid={`metadata-${name}`} value={settings[name]} disabled={!!busy || query.isPending} onChange={e=>save(name,e.target.value)}>{options.map(([value,text])=><option key={value} value={value}>{t(text)}</option>)}</NothingSelect></label>;
 const toggle=(name,label)=><label className="flex min-h-11 items-center justify-between gap-3 text-sm" key={name}><span>{t(label)}</span><input type="checkbox" aria-label={t(label)} data-testid={`metadata-${name}`} checked={settings[name]} disabled={!!busy || query.isPending} onChange={e=>save(name,e.target.checked)}/></label>;
 return <div data-testid="metadata-sources" className="space-y-4">
  {query.error && <p role="alert">{errMsg(query.error)} <Button variant="outlined" onClick={()=>query.refetch()}>{t('Retry')}</Button></p>}
  <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
   {select('search','Search source',[['tmdb','TMDb']])}
   {select('movie','Movie metadata source',[['tmdb','TMDb'],['cinemeta','Cinemeta']])}
   {select('series','Series metadata source',[['tmdb','TMDb'],['cinemeta','Cinemeta']])}
  </div>
  {toggle('ratings','Ratings: IMDb/RT/Metacritic via OMDb')}{toggle('imdbOnLists','Show IMDb rating on lists')}
  <p className="text-xs text-neutral-500">{t('IMDb list ratings and IMDb filters use your daily OMDb quota. Cached ratings are reused for at least 24 hours.')}</p>
  {settings.search==='tmdb' && status.tmdb==='missing' && <p role="status" className="text-xs text-amber-400">{t('TMDb key missing. Search is using Cinemeta. Configure TMDb in Metadata sources.')}</p>}
  {['tmdb','omdb'].map(provider=><section key={provider} className="n-card space-y-2 p-3"><div className="flex items-center justify-between gap-2"><h3 className="n-h2">{provider.toUpperCase()}</h3><span data-testid={`metadata-status-${provider}`} className="n-label">{t(status[provider] || 'missing')}</span></div><label className="sr-only" htmlFor={`metadata-key-${provider}`}>{provider.toUpperCase()} {t('API key')}</label><input id={`metadata-key-${provider}`} data-testid={`metadata-key-${provider}`} className="n-input" type={visible[provider]?'text':'password'} autoComplete="off" value={keys[provider] || ''} placeholder={t(provider==='tmdb'?'API key or read access token':'API key')} disabled={!!busy} onChange={e=>setKeys(previous=>({...previous,[provider]:e.target.value}))}/>
   <div className="flex flex-wrap gap-2"><Button variant="subtle" aria-label={`Toggle ${provider.toUpperCase()} key visibility`} onClick={()=>setVisible(previous=>({...previous,[provider]:!previous[provider]}))}>{visible[provider]?'Hide':'Show'}</Button><Button variant="outlined" disabled={!!busy || !keys[provider]?.trim()} onClick={()=>run(provider,async()=>{const value=keys[provider].trim();await api.post('/metadata/test',{provider,value});await api.put('/metadata/credentials',{provider,value});setKeys(previous=>({...previous,[provider]:''}));setMessage(`${provider.toUpperCase()} · Connection verified and saved`);})}>Save & verify</Button><Button variant="outlined" disabled={!!busy || !keys[provider]?.trim()} onClick={()=>run(provider,async()=>{if(keys[provider]?.trim())await api.put('/metadata/credentials',{provider,value:keys[provider]});setKeys(previous=>({...previous,[provider]:''}));setMessage(t('Credential saved securely. Test the connection.'));})}>{t('Save')}</Button><Button variant="outlined" data-testid={`metadata-test-${provider}`} disabled={!!busy || status[provider]==='missing' || !status[provider]} onClick={()=>run(provider,async()=>{await api.post('/metadata/test',{provider});setMessage(`${provider.toUpperCase()} · ${t('Connection verified')}`);})}>{t('Test connection')}</Button><Button variant="subtle" disabled={!!busy || status[provider]==='missing'} onClick={()=>run(provider,()=>api.put('/metadata/credentials',{provider,value:''}))}>{t('Remove')}</Button></div>
  <a className="settings-provider-link" href={provider==='tmdb'?'https://www.themoviedb.org/settings/api':'https://www.omdbapi.com/apikey.aspx'} target="_blank" rel="noreferrer">Get a {provider.toUpperCase()} key ↗</a></section>)}
  <p className="text-xs text-neutral-500">This product uses the TMDB API but is not endorsed or certified by TMDB.</p>
  {busy && <p role="status" className="n-label">{t('Saving…')}</p>}{error && <p role="alert" className="text-xs text-red-400">{error}</p>}{message && <p role="status" className="text-xs">{message}</p>}
  {!Capacitor.isNativePlatform() && <p className="text-xs text-neutral-500">{t('Browser preview keeps new provider credentials only for this session. Android stores them encrypted with Keystore.')}</p>}
 </div>;
}
