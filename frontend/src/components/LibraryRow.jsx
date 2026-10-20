import ProgressIndicator from './ProgressIndicator';
import {useEffect,useRef} from 'react';
import {FileQuestion,FileVideo,ChevronRight} from 'lucide-react';
import {Checkbox} from './ui/checkbox';
import {fmtBytes,fmtSpeed,statusOf,STATUS_COLOR} from '../lib/format';
import {torrentPresentation} from '../lib/collectionModel';
import {buzz} from '../lib/haptics';

export default function LibraryRow({t,index,selected,selecting=false,onToggle,onOpen}) {
  const st=statusOf(t),info=torrentPresentation(t),Icon=info.unidentified?FileQuestion:FileVideo;
  const progress=Number(t.progress),pct=t.progress!=null && Number.isFinite(progress)?Math.round(Math.min(1,Math.max(0,progress))*100):null;
  const timer=useRef(null),fired=useRef(false),start=useRef({x:0,y:0});
  useEffect(()=>()=>clearTimeout(timer.current),[]);
  const cancel=()=>clearTimeout(timer.current);
  const down=e=>{if(e.button!==0)return;fired.current=false;start.current={x:e.clientX,y:e.clientY};cancel();timer.current=setTimeout(()=>{fired.current=true;buzz(18);onToggle();},420);};
  const click=e=>{if(fired.current){fired.current=false;e.preventDefault();return;}if(selecting)onToggle();else onOpen();};
  return <div data-testid={`library-row-${index}`} data-selected={selected} className="collection-torrent-row">
    {selecting && <div className="collection-selection-target"><Checkbox aria-label={`Select ${info.title}`} data-testid={`library-select-${index}`} checked={selected} onCheckedChange={onToggle}/></div>}
    <button data-testid={`library-open-${index}`} aria-label={`${selecting?'Select':'View files for'} ${info.title}`} className="collection-torrent-open" onPointerDown={down} onPointerUp={cancel} onPointerLeave={cancel} onPointerCancel={cancel} onPointerMove={e=>{if(Math.hypot(e.clientX-start.current.x,e.clientY-start.current.y)>10)cancel();}} onContextMenu={e=>{e.preventDefault();cancel();if(!fired.current){fired.current=true;onToggle();}}} onClick={click}>
      {!selecting && <span className="collection-file-icon"><Icon size={22} aria-hidden="true"/></span>}
      <span className="collection-torrent-body"><span className="collection-title">{info.title}</span>{info.coordinates && <span className="collection-note">{info.coordinates}</span>}{info.unidentified && <span className="collection-note">{info.identifier}… · original details available</span>}
        <span className="collection-torrent-meta"><span className="collection-task-status" style={{'--task-color':STATUS_COLOR[st]}}><span aria-hidden="true"/>{st==='done'?'Completed':st==='error'?'Needs attention':'Downloading'}</span><span>{fmtBytes(t.size)}</span><span>{(t.files||[]).length} {(t.files||[]).length===1?'file':'files'}</span></span>
        {st==='active' && <span className="collection-download-progress"><ProgressIndicator value={pct} label={`Download progress for ${info.title}`} variant="continuous"/><span>{pct==null?'Waiting':`${pct}%`}{Number(t.download_speed)>0?` · ${fmtSpeed(t.download_speed)}`:''}</span></span>}
        {st!=='error' && t.download_state && !['cached','completed','downloading'].includes(t.download_state.toLowerCase()) && <span className="collection-note">{t.download_state.replace(/_/g,' ')}</span>}
        {st==='error' && <span className="collection-task-error">{String(t.download_state||'Task failed').replace(/_/g,' ')} · Open for details</span>}
      </span><ChevronRight size={16} className="collection-row-chevron" aria-hidden="true"/>
    </button>
  </div>;
}
