import ProgressIndicator from './ProgressIndicator';
import MediaQuickActions from "./MediaQuickActions";
import { Link } from 'react-router-dom';
import { activityHref, boundedProgress } from '../lib/viewingActivity';
export function ActivityStats({stats={},localOnly=false,prefix='activity'}) {
  return <div className="activity-stats mb-5">{[['Movies',stats.movies,'movies'],['Episodes',stats.episodes,'episodes'],[localOnly?'Hours unavailable':'Runtime hours',localOnly?'—':stats.hours,'hours']].map(([label,value,id])=><div className="n-card p-3" key={id}><div className="n-label mb-2">{label}</div><div className="font-dot text-3xl" data-testid={`${prefix}-stat-${id}`}>{value ?? '—'}</div></div>)}</div>;
}
export function ViewingRow({item:d,index:i=0,prefix='activity',compact=false}) {
  const progress=boundedProgress(d.progress),isEpisode=d.type==='episode',isSeries=d.type==='series';
  const image=/^tt\d+$/.test(d.imdb_id || '')?`https://images.metahub.space/poster/small/${d.imdb_id}/img`:null;
  return <MediaQuickActions item={{...d,type:isEpisode?'series':d.type,name:d.series_name || d.name}}><Link to={activityHref(d)} data-testid={`${prefix}-item-${i}`} className={`activity-row n-card flex min-h-[80px] items-center gap-3 p-2.5 ${compact?'activity-row--compact':''}`}>
    <div className="h-16 w-11 shrink-0 overflow-hidden rounded-lg bg-[color:var(--c-222222)]">{image && <img src={image} alt="" loading="lazy" decoding="async" onError={e=>{e.currentTarget.style.visibility='hidden';}} className="h-full w-full object-cover"/>}</div>
    <div className="min-w-0 flex-1"><div className="truncate text-sm font-semibold">{isEpisode?d.series_name || d.name:d.name}</div><div className="mt-1 truncate font-nmono text-[10px] text-neutral-500">{isEpisode?`S${String(d.season).padStart(2,'0')} E${String(d.episode).padStart(2,'0')} · ${d.name || ''}`:isSeries?`${d.watched_count || 0}${d.total_episodes?` / ${d.total_episodes}`:''} episodes watched${d.played?' · Completed':''}`:d.year || 'Movie'}</div>{isSeries && (d.total_episodes || d.played) && <ProgressIndicator value={progress} label={`${d.name} series progress`} variant="continuous" className="mt-2"/>}{!isSeries && !d.played && progress>0 && <ProgressIndicator value={progress} label="Playback progress" variant="continuous" className="mt-2"/>}</div>
    <span className="shrink-0 px-1 font-nmono text-[10px] text-neutral-400" aria-label={d.played?'Watched':`${Math.round(progress)} percent played`}>{d.played?'✓':isSeries?'':`${Math.round(progress)}%`}</span>
  </Link></MediaQuickActions>;
}
export function ActivityDay({day}) {
  const value=day==='undated'?'Date unavailable':new Date(`${day}T12:00:00`).toLocaleDateString("en-US",{weekday:'short',month:'short',day:'numeric'});
  return <h3 className="activity-day mb-3 font-nmono text-[10px] uppercase tracking-widest text-neutral-400">{value}</h3>;
}
