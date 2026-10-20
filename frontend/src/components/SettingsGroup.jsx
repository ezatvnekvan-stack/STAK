import AppErrorBoundary from './AppErrorBoundary';
import {useSearchParams} from 'react-router-dom';
import {ArrowLeft,ChevronRight,KeyRound,Database,Radio,MonitorPlay,Palette,Shield,LayoutGrid} from 'lucide-react';

// Focused settings destinations are real URLs: reload, deep links and Android
// back navigation work without a separate modal navigation stack.
export default function SettingsGroup({id,title,hint,children}) {
 const [params,setParams]=useSearchParams(),active=['account','metadata','sources','jellyfin','appearance','home','advanced'].includes(params.get('section'))?params.get('section'):null,open=active===id;
 const Icon={account:KeyRound,metadata:Database,sources:Radio,jellyfin:MonitorPlay,appearance:Palette,home:LayoutGrid,advanced:Shield}[id] || Shield;
 if(active && !open)return null;
 const choose=value=>{const next=new URLSearchParams(params);if(value)next.set('section',value);else next.delete('section');setParams(next);window.scrollTo(0,0);requestAnimationFrame(()=>document.querySelector('[data-testid="account-title"]')?.focus());};
 return <section data-testid={`settings-group-${id}`} className={open?'settings-detail':'settings-row'}>
  <button type="button" onClick={()=>choose(open?null:id)} aria-expanded={open} aria-controls={`settings-content-${id}`} className="settings-group-trigger">
   {open?<ArrowLeft size={18}/>:<span className="settings-row-icon"><Icon size={20}/></span>}
   <span className="min-w-0"><span className="settings-row-title">{open?'All settings':title}</span>{!open && hint && <span className="settings-row-summary">{hint}</span>}</span>
   {!open && <ChevronRight size={17} className="settings-row-chevron"/>}
  </button>
  {open && <div id={`settings-content-${id}`} className="settings-group-content space-y-4"><AppErrorBoundary>{children}</AppErrorBoundary></div>}
 </section>;
}
