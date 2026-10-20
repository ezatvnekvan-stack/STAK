import { useState } from 'react';
import {SlidersHorizontal} from 'lucide-react';
import NothingSelect from './NothingSelect';
import {CollectionSheet} from './CollectionControls';
import {PREFER_HUN_KEY} from '../lib/score';
const QUALITY=[['hdr','HDR'],['atmos','Atmos'],['webdl','WEB-DL'],['remux','REMUX']];
export default function StreamFilters({f,setF,counts,total}) {
 const [open,setOpen]=useState(false),set=(key,value)=>setF({...f,[key]:value});
 const count=Number(!!f.dv)+Number(!!f.maxSize)+Number(!!f.preferHun);
 const reset=()=>{try{localStorage.setItem(PREFER_HUN_KEY,'0');}catch{/* optional preference storage */}setF({...f,res:'All',text:'',hdr:false,dv:false,atmos:false,remux:false,webdl:false,maxSize:'',preferHun:false});};
 return <div className="source-filters">
  <div className="resolution-chips" role="group" aria-label="Resolution">{['4K','1080p'].map(res=><button type="button" key={res} data-testid={`stream-filter-res-${res.toLowerCase()}`} className="nothing-badge-filter" data-active={f.res===res} aria-pressed={f.res===res} onClick={()=>set('res',f.res===res?'All':res)}>{res}<span className="source-chip-count">{counts[res] || 0}</span></button>)}<button className="n-icon-btn source-options" aria-label="Filters and sorting" onClick={()=>setOpen(true)}><SlidersHorizontal size={16}/>{count>0 && <span className="source-option-count">{count}</span>}</button></div>
  <div className="source-quality-badges" role="group" aria-label="Release quality">{QUALITY.map(([key,label])=><button key={key} data-testid={`stream-filter-${key}`} data-active={!!f[key]} aria-pressed={!!f[key]} onClick={()=>set(key,!f[key])} className="nothing-badge-filter">{label}</button>)}</div>
  <div className="source-results-heading"><span aria-live="polite">{total} cached {total===1?'release':'releases'}</span><NothingSelect data-testid="stream-sort-select" aria-label="Sort releases" value={f.sort} onChange={e=>set('sort',e.target.value)} className="n-input"><option value="best">Recommended</option><option value="seeders">Most seeds</option><option value="size">Largest size</option></NothingSelect></div>
  <CollectionSheet open={open} onOpenChange={setOpen} title="Source filters" testId="source-filter-sheet" onReset={reset}>
   <section><h3 className="n-label">Additional preferences</h3><div className="source-quality-options"><button data-testid="stream-filter-dv" className="n-chip" aria-pressed={!!f.dv} data-active={!!f.dv} onClick={()=>set('dv',!f.dv)}>Dolby Vision</button><button data-testid="stream-filter-hun" data-active={!!f.preferHun} aria-pressed={!!f.preferHun} onClick={()=>{try{localStorage.setItem(PREFER_HUN_KEY,f.preferHun?'0':'1');}catch{/* optional storage */}set('preferHun',!f.preferHun);}} className="n-chip">HUN first</button></div></section>
   <label className="source-size-label">Maximum size<NothingSelect data-testid="stream-filter-max-size" aria-label="Maximum source size" value={f.maxSize || ''} onChange={e=>set('maxSize',e.target.value)} className="n-input"><option value="">Any size</option>{[5,10,20,50].map(size=><option key={size} value={size}>≤ {size} GB</option>)}</NothingSelect></label>
   <p className="source-filter-note">Badges are inferred from release names. Only confirmed TorBox cache results are shown.</p>
  </CollectionSheet>
 </div>;
}
