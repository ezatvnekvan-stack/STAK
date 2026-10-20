import {Link} from 'react-router-dom';
import {Check} from 'lucide-react';
import MediaQuickActions from './MediaQuickActions';
import {calendarTitleGroups} from '../lib/calendarModel';
import {activityHref} from '../lib/viewingActivity';
import {useApp} from '../context/AppContext';
export default function CalendarReleases({events,cinematic=false}) {
 const {tracking}=useApp();
 return <div className="calendar-release-list">{calendarTitleGroups(events).map(group=>{
  const item=group.event,episode=item.type==='series';
  const rows=group.episodes.map((event,index)=>{const watched=episode?tracking?.get(event.imdb_id)?.episodes?.some(e=>e.season===event.season && e.episode===event.episode && e.played):tracking?.get(event.imdb_id)?.played;
   return <Link key={`${event.date}:${event.season}:${event.episode}`} data-testid={`calendar-event-${event.imdb_id}-${event.season || 0}-${event.episode || index}`} className="calendar-episode-link" to={activityHref({...event,type:episode?'episode':event.type})}><span>{episode?`S${String(event.season).padStart(2,'0')}E${String(event.episode).padStart(2,'0')}`:'Movie release'}</span><span>{event.title || (episode?'Episode details':'View details')}</span>{watched && <Check size={14} aria-label="Watched"/>}</Link>;});
  return <article className={`calendar-release-card ${cinematic?'calendar-cinematic-card':''}`} key={group.key}>
   {cinematic && item.background && <Link className="calendar-release-backdrop" to={activityHref(item)} aria-label={`Open ${item.name}`}><img src={item.background} alt="" loading="lazy" decoding="async" onLoad={event=>{if(event.currentTarget.parentElement)event.currentTarget.parentElement.dataset.failed='false';}} onError={event=>{if(event.currentTarget.parentElement)event.currentTarget.parentElement.dataset.failed='true';}}/><span aria-hidden="true"/><strong>{item.name}</strong></Link>}
   <MediaQuickActions item={item}><Link className="calendar-release-heading" to={activityHref(item)}><img src={item.poster || '/icon.svg'} alt="" loading="lazy" decoding="async" onError={event=>{event.currentTarget.onerror=null;event.currentTarget.src='/icon.svg';}}/><div><h4>{item.name}</h4><p>{episode?`${group.episodes.length} ${group.episodes.length===1?'episode':'episodes'}`:'Movie'}</p></div></Link></MediaQuickActions>
   {rows.length>3?<details className="calendar-episode-disclosure"><summary>Show all {rows.length} episodes</summary>{rows}</details>:rows}
  </article>;
 })}</div>;
}
