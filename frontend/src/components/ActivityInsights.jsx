import { useMemo } from 'react';
const dayKey=date=>`${date.getFullYear()}-${String(date.getMonth()+1).padStart(2,'0')}-${String(date.getDate()).padStart(2,'0')}`;
export default function ActivityInsights({recent,day,onDayChange}){
  const {week,cells}=useMemo(()=>{
    const today=new Date();today.setHours(0,0,0,0);const start=new Date(today);start.setDate(start.getDate()-77-((today.getDay()+6)%7));
    const counts=new Map();let week=0;
    for(const item of recent){const date=new Date(item.last_played);if(Number.isNaN(date.getTime()))continue;const key=dayKey(date);counts.set(key,(counts.get(key)||0)+1);if(date.getTime()<=Date.now() && Date.now()-date.getTime()<=7*864e5)week++;}
    return {week,cells:Array.from({length:84},(_,i)=>{const date=new Date(start);date.setDate(start.getDate()+i);return {date,key:dayKey(date),future:date>today,count:counts.get(dayKey(date)) || 0};})};
  },[recent]);
  return <details className="activity-surface mb-4"><summary className="flex min-h-11 cursor-pointer flex-wrap items-center justify-between gap-2"><span className="n-label">Viewing activity</span><span className="font-nmono text-xs">{week} watched in 7 days</span></summary><p className="my-3 text-xs text-neutral-500">Last 12 weeks · tap a day to filter your history.</p><div className="grid grid-flow-col grid-rows-7 gap-1.5" role="group" aria-label="Activity over the last 12 weeks">{cells.map(cell=><button key={cell.key} disabled={cell.future} className="aspect-square min-w-0 rounded-full" style={{opacity:cell.future?0:1,background:cell.count?'var(--n-accent)':'var(--c-262626)',outline:day===cell.key?'2px solid var(--fg-1)':undefined}} aria-label={`${cell.key}: ${cell.count} watched`} aria-pressed={day===cell.key} onClick={()=>onDayChange(cell.key)}/>)}</div></details>;
}
