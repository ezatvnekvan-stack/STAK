import {useEffect,useMemo,useState} from 'react';
import {SlidersHorizontal} from 'lucide-react';
import {useApp} from '../context/AppContext';
import {api,IS_CAPACITOR_OFFLINE,errMsg} from '../lib/api';
import {DotLoader,Empty,NeedKey} from '../components/Bits';
import {useAccountQuery} from '../lib/useAccountQuery';
import {accountQueryKey,accountRequest} from '../lib/accountQueries';
import {REFRESH_EVENT} from '../components/AppShell';
import {TRACKING_REFRESH} from '../lib/trackingEvents';
import {useMetadataRevision} from '../lib/metadata/runtime';
import {useLocalToday} from '../lib/useLocalToday';
import {normalizeTracking} from '../lib/trackingModel';
import {dateLabel,monthCells,localeWeekStart,orderedCalendarEvents} from '../lib/calendarModel';
import NothingSelect from '../components/NothingSelect';
import {CollectionSheet} from '../components/CollectionControls';
import CalendarMonth from '../components/CalendarMonth';
import CalendarReleases from '../components/CalendarReleases';

export default function CalendarPage({preferences,onPreferencesChange,onBusyChange}) {
 const {key,session,watchlist=[]}=useApp(),revision=useMetadataRevision(),today=useLocalToday();
 const [local,setLocal]=useState(normalizeTracking),[filters,setFilters]=useState(false),prefs=normalizeTracking(preferences || local);
 const update=patch=>onPreferencesChange?onPreferencesChange(patch):setLocal(previous=>({...previous,...patch}));
 const month=prefs.date.slice(0,7),weekStart=prefs.weekStart==='locale'?localeWeekStart():prefs.weekStart,cells=monthCells(month,weekStart),start=cells[0].date,end=cells[41].date;
 const followed=JSON.stringify(watchlist.map(title=>[title.imdb_id,title.type]));
 const {data,error,refetch,isFetching,isPending}=useAccountQuery('/calendar',{queryKey:[...accountQueryKey(session,'/calendar'),revision,'following',start,end,followed],enabled:Boolean(key || IS_CAPACITOR_OFFLINE),queryFn:async({signal})=>(await api.get('/calendar',accountRequest(session,{signal,params:{scope:'following',start,end}}))).data,staleTime:300000});
 const events=useMemo(()=>orderedCalendarEvents(Array.isArray(data?.events)?data.events:[]).filter(event=>prefs.calendarType==='all' || event.type===prefs.calendarType),[data,prefs.calendarType]);
 const eventsByDate=useMemo(()=>{const map=new Map();events.forEach(event=>{if(!map.has(event.date))map.set(event.date,[]);map.get(event.date).push(event);});return map;},[events]);
 const counts=useMemo(()=>new Map([...eventsByDate].map(([date,items])=>[date,items.length])),[eventsByDate]);
 const loaded=Boolean(data && data.range_start===start && data.range_end===end),selected=eventsByDate.get(prefs.date) || [];
 useEffect(()=>{onBusyChange?.(isFetching);},[isFetching,onBusyChange]);
 useEffect(()=>{const refresh=event=>{if(!event.detail || event.detail==='calendar')refetch();};window.addEventListener(REFRESH_EVENT,refresh);window.addEventListener(TRACKING_REFRESH,refresh);return()=>{window.removeEventListener(REFRESH_EVENT,refresh);window.removeEventListener(TRACKING_REFRESH,refresh);};},[refetch]);
 if(!key && !IS_CAPACITOR_OFFLINE)return <NeedKey/>;
 return <div className="tracking-calendar calendar-simple" data-testid="tracking-calendar">
  <h2 className="sr-only">Calendar</h2>
  <div className="calendar-simple-toolbar"><span className="calendar-following-label">Following{prefs.calendarType!=='all'?` · ${prefs.calendarType==='movie'?'Movies':'Episodes'}`:''}</span><button className="tracking-text-action" onClick={()=>update({date:today})}>Today</button><button className="n-icon-btn" aria-label="Calendar filters" aria-pressed={prefs.calendarType!=='all'} onClick={()=>setFilters(true)}><SlidersHorizontal size={17}/></button></div>
  <CalendarMonth selected={prefs.date} today={today} weekStart={weekStart} counts={counts} eventsByDate={eventsByDate} loaded={loaded} showArtwork={prefs.calendarArtwork} simple onSelect={date=>update({date})}/>
  {isFetching && <p className="calendar-updating" role="status">{loaded?'Updating releases…':'Loading schedule…'}</p>}
  {error && <div role="alert"><Empty title="Calendar could not update" text={errMsg(error)} testId="calendar-error" actionLabel="Retry" onAction={()=>refetch()}/>{loaded && <p className="tracking-note">Showing saved releases.</p>}</div>}
  {data?.errors?.length>0 && <p role="status" className="tracking-notice">Some releases could not be checked. <button className="tracking-text-action" onClick={()=>refetch()}>Retry</button></p>}
  {!loaded?isPending?<DotLoader label="Loading releases"/>:!error && <Empty title="Schedule unavailable" text="Retry to load releases for this month." actionLabel="Retry" onAction={()=>refetch()} testId="calendar-coverage-error"/>:<section className="calendar-selected-day" data-testid="calendar-selected-day"><h3 className="tracking-day-heading">{prefs.date===today?'Today · ':''}{dateLabel(prefs.date,{weekday:'short',month:'short',day:'numeric'})}<span>{selected.length?`${selected.length} releases`:''}</span></h3>{selected.length?<CalendarReleases events={selected} cinematic/>:<div className="calendar-day-empty" data-testid="calendar-empty"><p>No releases for this day.</p>{data?.errors?.length>0?<p>Some dates could not be checked.</p>:!watchlist.length?<p>Add movies or series to your watchlist to follow their releases.</p>:null}</div>}</section>}
  <CollectionSheet open={filters} onOpenChange={setFilters} title="Calendar filters" testId="calendar-filter-sheet" onReset={()=>update({calendarType:'all',calendarArtwork:false,weekStart:'locale'})}>
   <section><h3 className="n-label">Releases</h3><div className="source-quality-options">{[['all','All'],['series','Episodes'],['movie','Movies']].map(([value,label])=><button key={value} className="n-chip" data-active={prefs.calendarType===value} aria-pressed={prefs.calendarType===value} onClick={()=>update({calendarType:value})}>{label}</button>)}</div></section>
   <button className="n-chip" aria-pressed={prefs.calendarArtwork} onClick={()=>update({calendarArtwork:!prefs.calendarArtwork})}>Artwork in calendar</button>
   <label className="filter-label">Week starts<NothingSelect aria-label="Week starts" value={String(prefs.weekStart)} onChange={e=>update({weekStart:e.target.value==='locale'?'locale':Number(e.target.value)})}><option value="locale">Device preference</option><option value="1">Monday</option><option value="0">Sunday</option></NothingSelect></label>
   <p className="tracking-note">Following uses your watchlist. Release dates are reported by metadata providers and may change.</p>
  </CollectionSheet>
 </div>;
}
