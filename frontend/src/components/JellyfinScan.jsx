import ProgressIndicator from './ProgressIndicator';
import { useEffect, useRef, useState } from 'react';
import { RefreshCw } from 'lucide-react';
import { api, errMsg, isCancelled } from '../lib/api';
import { scanFinished } from '../lib/jellyfinLibraryMetrics';
import JellyfinLogo from './JellyfinLogo';

export default function JellyfinScan({onRefresh}) {
  const [status,setStatus]=useState({phase:'idle'});
  const job=useRef(null),timer=useRef(null),snapshot=useRef(null);
  useEffect(()=>()=>{job.current?.abort();clearTimeout(timer.current);},[]);
  const run=async(monitorOnly=false)=>{
    job.current?.abort();clearTimeout(timer.current);
    const controller=new AbortController();job.current=controller;
    const alive=()=>job.current===controller && !controller.signal.aborted;
    const get=async path=>(await api.get(path,{signal:controller.signal})).data;
    setStatus({phase:'requesting'});
    try {
      const before=await Promise.allSettled(['/jellyfin/scan-status','/jellyfin/counts','/jellyfin/scan-recent'].map(get));
      if(!alive())return;
      const observed=before[0].status==='fulfilled'?before[0].value:null;
      const baseline=monitorOnly && snapshot.current?snapshot.current.task:observed;
      const counts=monitorOnly && snapshot.current?snapshot.current.counts:before[1].status==='fulfilled'?before[1].value:null;
      const recent=monitorOnly && snapshot.current?snapshot.current.recent:before[2].status==='fulfilled'?before[2].value?.items:null;
      if(!monitorOnly)snapshot.current={task:baseline,counts,recent};
      const sawRunning=observed?.state==='Running' || observed?.state==='Cancelling';
      if(!monitorOnly && !sawRunning)await api.post('/jellyfin/scan',undefined,{signal:controller.signal});
      if(!alive())return;
      if(!observed?.supported){setStatus({phase:'unconfirmed',message:'Live library task progress is unavailable to this user. Check Jellyfin for scan status; completion and additions cannot be verified here.'});return;}
      const deadline=Date.now()+5*60_000;
      const poll=async()=>{
        if(!alive())return;
        if(Date.now()>deadline){setStatus({phase:'unconfirmed',message:'Monitoring paused after five minutes. The server may still be scanning. Check status again; no completion has been assumed.'});return;}
        if(document.visibilityState==='hidden'){timer.current=setTimeout(poll,5000);return;}
        try {
          const current=await get('/jellyfin/scan-status');if(!alive())return;
          if(!current.supported){setStatus({phase:'unconfirmed',message:'The server no longer exposes the library task. Check Jellyfin for scan completion.'});return;}
          if(scanFinished(current,baseline)) {
            const result=current.last_result?.status;
            if(result!=='Completed'){setStatus({phase:'error',message:`Jellyfin scan ended with status: ${result || 'unknown'}.`});onRefresh();return;}
            setStatus({phase:'verifying',progress:100});
            const after=await Promise.allSettled(['/jellyfin/counts','/jellyfin/scan-recent'].map(get));if(!alive())return;
            const updated=after[0].status==='fulfilled'?after[0].value:null;
            const latest=after[1].status==='fulfilled'?after[1].value?.items:null;
            const oldIds=new Set((recent || []).map(item=>item.Id));
            setStatus({phase:'complete',changes:counts && updated?{movies:updated.movies-counts.movies,series:updated.series-counts.series}:null,added:recent && latest?latest.filter(item=>!oldIds.has(item.Id)):null});
            onRefresh();return;
          }
          setStatus({phase:current.state==='Running'?'running':current.state==='Cancelling'?'cancelling':'queued',progress:current.state==='Running' || current.state==='Cancelling'?current.progress:null});
          timer.current=setTimeout(poll,5000);
        }catch(error){if(alive() && !isCancelled(error))setStatus({phase:'unconfirmed',message:`Progress monitoring failed: ${errMsg(error)} The server scan may still continue.`});}
      };
      setStatus({phase:sawRunning?'running':'queued',progress:baseline?.state==='Running'?baseline.progress:null});
      timer.current=setTimeout(poll,1000);
    }catch(error){if(alive() && !isCancelled(error))setStatus({phase:'error',message:errMsg(error)});}
  };
  const active=['requesting','queued','running','cancelling','verifying'].includes(status.phase);
  const progress=Number.isFinite(status.progress)?status.progress:null;
  const title={idle:'Library scan',requesting:'Preparing scan',queued:'Scan queued',running:'Scanning your library',cancelling:'Server is stopping the scan',verifying:'Checking library changes',complete:'Scan complete',error:'Scan could not finish',unconfirmed:'Scan status unconfirmed'}[status.phase];
  const signed=n=>`${n>0?'+':''}${n}`;
  return <><button type="button" className="n-chip nothing-action scan-trigger" disabled={active} onClick={()=>run()}><JellyfinLogo className="scan-jellyfin-logo"/><RefreshCw size={13} className={active?'mr-2 animate-spin':'mr-2'}/>{active?'Scanning…':'Scan library'}</button>
    {status.phase!=='idle' && <section className="inline-library-scan w-full min-w-0 space-y-3" aria-label="Library scan status"><h3 className="n-label">{title}</h3>
      {active && <div className="space-y-3" role="status"><ProgressIndicator label="Jellyfin library scan" value={progress}/><p className="font-nmono text-xs text-neutral-400">{progress==null?'Waiting for server progress…':`${Math.round(progress)}% reported by Jellyfin`}</p></div>}
      {status.message && <p role={status.phase==='error'?'alert':'status'} className="text-sm text-neutral-400">{status.message}</p>}
      {status.phase==='complete' && <div className="scan-results space-y-3"><div className="font-dot text-3xl" aria-hidden="true">✓</div><h3 className="n-label">Library changes since request</h3>{status.changes?<p className="text-sm">{signed(status.changes.movies)} movies · {signed(status.changes.series)} shows</p>:<p className="text-xs text-neutral-400">Title count changes could not be verified.</p>}{status.added?.length>0 && <><p className="n-label">Newly observed · latest 30 items checked</p><ul className="space-y-2">{status.added.map(item=><li key={item.Id} className="n-card p-3 text-xs">{item.Type==='Episode'?`${item.SeriesName || item.Name} · S${item.ParentIndexNumber} E${item.IndexNumber} · ${item.Name}`:`${item.Name} · ${item.Type==='Series'?'Series':'Movie'}`}</li>)}</ul></>}{status.added && !status.added.length && <p className="text-xs text-neutral-400">No new movies, shows or episodes observed in the latest 30 items.</p>}<p className="text-[10px] text-neutral-500">Episode additions and metadata updates may not change movie/show totals. These are observed changes, not a complete file import log.</p></div>}
      {['error','unconfirmed','complete'].includes(status.phase) && <button type="button" className="n-chip" onClick={()=>run(status.phase!=='complete')}>{status.phase==='complete'?'Scan again':'Check status again'}</button>}
    </section>}
  </>;
}
