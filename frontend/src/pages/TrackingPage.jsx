import {useCallback,useEffect,useState} from 'react';
import {Navigate,useLocation,useSearchParams} from 'react-router-dom';
import {RefreshCw} from 'lucide-react';
import {AnimatedSelection,TransitionPanel} from '../components/design/MotionSystem';
import {useApp} from '../context/AppContext';
import {useExperience} from '../lib/useExperience';
import {normalizeTracking} from '../lib/trackingModel';
import {useTrackingScroll} from '../lib/useTrackingScroll';
import HistoryPage from './HistoryPage';
import CalendarPage from './CalendarPage';
import {TRACKING_REFRESH} from '../lib/trackingEvents';
export function TrackingRedirect({view}) {
  const {search}=useLocation(),params=new URLSearchParams(search);params.set('view',view);
  return <Navigate to={`/tracking?${params}`} replace/>;
}
export default function TrackingPage() {
  const {session}=useApp();
  const [saved,setSaved]=useExperience('tracking',normalizeTracking());
  const [params,setParams]=useSearchParams(),[busy,setBusy]=useState(false);
  const requested=params.get('view'),view=['activity','calendar'].includes(requested)?requested:saved.view;
  const update=useCallback(patch=>setSaved(previous=>({...previous,...patch})),[setSaved]);
  useEffect(()=>{if(requested && ['activity','calendar'].includes(requested) && saved.view!==requested)update({view:requested});},[requested,saved.view,update]);
  const rememberScroll=useTrackingScroll(view,session.scope);
  const choose=next=>{if(next===view)return;rememberScroll?.();const value=new URLSearchParams(params);value.set('view',next);setParams(value,{replace:true});update({view:next});};
  return <div className="tracking-page" data-testid="tracking-page">
    <header className="tracking-heading"><div><h1 className="stak-page-title font-dot" data-testid="tracking-title">Tracking</h1><p>{view==='activity'?'Your viewing journal':'Episodes & releases'}</p></div><button className="n-icon-btn" aria-label={`Refresh ${view==='activity'?'activity':'calendar'}`} disabled={busy} onClick={()=>window.dispatchEvent(new CustomEvent(TRACKING_REFRESH,{detail:view}))}><RefreshCw size={18} className={busy?'animate-spin':''}/></button></header>
    <AnimatedSelection value={view} className="tracking-tabs" role="tablist" aria-label="Tracking views">{[['activity','Activity'],['calendar','Calendar']].map(([id,label])=><button key={id} data-value={id} id={`tracking-tab-${id}`} role="tab" aria-selected={view===id} aria-controls={`tracking-panel-${id}`} tabIndex={view===id?0:-1} onClick={()=>choose(id)} onKeyDown={event=>{if(['ArrowLeft','ArrowRight','Home','End'].includes(event.key)){event.preventDefault();const next=event.key==='Home'?'activity':event.key==='End'?'calendar':view==='activity'?'calendar':'activity';choose(next);requestAnimationFrame(()=>document.getElementById(`tracking-tab-${next}`)?.focus());}}}>{label}</button>)}</AnimatedSelection>
    <TransitionPanel panelKey={view} id={`tracking-panel-${view}`} role="tabpanel" aria-labelledby={`tracking-tab-${view}`} className="tracking-content">
      {view==='activity'?<HistoryPage preferences={saved} onPreferencesChange={update} onBusyChange={setBusy}/>:<CalendarPage preferences={saved} onPreferencesChange={update} onBusyChange={setBusy}/>}
    </TransitionPanel>
  </div>;
}
