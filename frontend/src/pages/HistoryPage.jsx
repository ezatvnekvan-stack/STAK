import {useCallback,useEffect,useMemo,useState} from 'react';
import {Link} from 'react-router-dom';
import {Info,RefreshCw} from 'lucide-react';
import {toast} from 'sonner';
import {useApp} from '../context/AppContext';
import {api,IS_CAPACITOR_OFFLINE,errMsg,isCancelled} from '../lib/api';
import {useAccountQuery} from '../lib/useAccountQuery';
import {accountRequest} from '../lib/accountQueries';
import {DotLoader,Empty,NeedKey} from '../components/Bits';
import ActivityInsights from '../components/ActivityInsights';
import AnimatedNumber from '../components/design/AnimatedNumber';
import {ActivityDay,ViewingRow} from '../components/ViewingActivity';
import {compactHistory} from '../lib/historyModel';
import {groupActivity,localDay} from '../lib/viewingActivity';
import {activityPeriodRows,activitySummary,normalizeTracking} from '../lib/trackingModel';
import {TRACKING_REFRESH} from '../lib/trackingEvents';
import {REFRESH_EVENT} from '../components/AppShell';
import NothingSelect from '../components/NothingSelect';
import {Popover,PopoverTrigger,PopoverContent} from '../components/ui/popover';

export default function HistoryPage({preferences,onPreferencesChange,onBusyChange}) {
 const {key,session,refreshTracking,tracking}=useApp();
 const [local,setLocal]=useState(normalizeTracking),prefs=normalizeTracking(preferences || local);
 const update=patch=>onPreferencesChange?onPreferencesChange(patch):setLocal(previous=>({...previous,...patch}));
 const {data,error,refetch,isFetching}=useAccountQuery('/tracking/history');
 const [busy,setBusy]=useState(false),[limit,setLimit]=useState(40);
 const recent=useMemo(()=>Array.isArray(data?.recent)?data.recent:[],[data]);
 const periodRows=useMemo(()=>activityPeriodRows(recent,prefs.period),[recent,prefs.period]);
 const stats=useMemo(()=>activitySummary(periodRows),[periodRows]);
 const selectedRows=useMemo(()=>periodRows.filter(item=>!prefs.day || localDay(item.last_played)===prefs.day),[periodRows,prefs.day]);
 const titles=useMemo(()=>compactHistory(selectedRows,tracking),[selectedRows,tracking]);
 const filtered=useMemo(()=>titles.filter(item=>prefs.filter==='all' || item.type===prefs.filter),[titles,prefs.filter]);
 const groups=useMemo(()=>groupActivity(filtered.slice(0,limit)),[filtered,limit]);
 const load=useCallback(()=>refetch({throwOnError:true,cancelRefetch:false}),[refetch]);
 useEffect(()=>{onBusyChange?.(busy || isFetching);},[busy,isFetching,onBusyChange]);
 useEffect(()=>{const refresh=event=>{if(!event.detail || event.detail==='activity')load().catch(()=>{});};window.addEventListener(REFRESH_EVENT,refresh);window.addEventListener(TRACKING_REFRESH,refresh);return()=>{window.removeEventListener(REFRESH_EVENT,refresh);window.removeEventListener(TRACKING_REFRESH,refresh);};},[load]);
 const sync=async()=>{setBusy(true);try{const {data:result}=await api.post('/jellyfin/sync',undefined,accountRequest(session));accountRequest(session);toast.success(`Synced ${result.titles} titles · ${result.episodes} episodes`);await load();refreshTracking().catch(()=>{});}catch(error){if(!isCancelled(error))toast.error(errMsg(error));}finally{setBusy(false);}};
 if(!key && !IS_CAPACITOR_OFFLINE)return <NeedKey/>;
 return <div className="tracking-activity" data-testid="tracking-activity">
  <h2 className="sr-only">Activity</h2>
  {error && <div role="alert"><Empty title="Activity could not update" text={errMsg(error)} testId="history-error" actionLabel="Retry" onAction={()=>load().catch(()=>{})}/>{data && <p className="tracking-note">Showing the last loaded activity.</p>}</div>}
  {data?.sync_error && <p role="status" className="tracking-notice">{data.sync_error}</p>}
  {!data?!error && <DotLoader label="Loading activity"/>:<>
   <section className="tracking-summary" aria-label="Viewing summary">
    <div className="tracking-summary-header"><NothingSelect aria-label="Activity period" value={prefs.period} onChange={e=>{update({period:e.target.value,day:''});setLimit(40);}}><option value="week">Last 7 days</option><option value="month">Last 30 days</option><option value="all">All time</option></NothingSelect><Popover><PopoverTrigger asChild><button className="n-icon-btn" aria-label="About activity"><Info size={17}/></button></PopoverTrigger><PopoverContent className="tracking-info"><h3>About activity</h3><p>{data.local_only?'Manual history stored on this device.':'Jellyfin returns its latest 300 watched items. Manual tracking remains available when Jellyfin is not configured.'}</p><p>All time includes saved history and up to 300 latest Jellyfin items; it is not a complete server audit.</p><p>Runtime estimates use known title lengths. They are not measured viewing duration. Repeated viewings may not be represented.</p>{stats.knownRuntime<stats.total && <p>Runtime is missing for {stats.total-stats.knownRuntime} loaded items in this period.</p>}</PopoverContent></Popover></div>
    <div className="tracking-summary-values"><div><strong data-testid="history-stat-movies"><AnimatedNumber value={stats.movies}/></strong><span>Movies</span></div><div><strong data-testid="history-stat-episodes"><AnimatedNumber value={stats.episodes}/></strong><span>Episodes</span></div><div><strong data-testid="history-stat-hours">{stats.knownRuntime?<AnimatedNumber value={stats.hours} decimals={1} suffix="h"/>:stats.total?'—':'0h'}</strong><span>{!stats.knownRuntime && stats.total?'Runtime unknown':stats.knownRuntime<stats.total?'Known runtime':'Est. runtime'}</span></div></div>
    <div className="tracking-summary-source"><span>{data.local_only?'Manual history':'Jellyfin history'}</span><span>{data.local_only?<Link className="tracking-text-action" to="/account?section=jellyfin">Connect Jellyfin</Link>:<button data-testid="history-sync-button" className="tracking-text-action" disabled={busy} onClick={sync}><RefreshCw size={13} aria-hidden="true"/>Sync Jellyfin</button>}</span></div>
   </section>
   {data.continue?.length>0 && <section><h3 className="tracking-section-title">Continue watching</h3><div data-testid="history-continue-list" className="tracking-rows">{data.continue.map((item,i)=><ViewingRow key={`${item.imdb_id}:${item.season}:${item.episode}`} item={item} index={i} prefix="continue"/>)}</div></section>}
   <div className="tracking-history-toolbar"><h3 className="tracking-section-title">{prefs.day?`Watched ${prefs.day}`:'History'} <span>{filtered.length}</span></h3><div className="tracking-segments" role="group" aria-label="Activity media type">{[['all','All'],['movie','Movies'],['series','Series']].map(([value,label])=><button key={value} aria-pressed={prefs.filter===value} onClick={()=>{update({filter:value});setLimit(40);}}>{label}</button>)}</div>{prefs.day && <button className="tracking-text-action" onClick={()=>update({day:''})}>Show all dates</button>}</div>
   {!filtered.length?<Empty title={recent.length?'No activity in this selection':'Nothing watched yet'} text={recent.length?'Choose another period or media type.':'Mark a title as watched or connect Jellyfin to populate your journal.'} testId="history-empty" actionLabel={recent.length?'Show loaded history':undefined} onAction={()=>update({period:'all',filter:'all',day:''})}>{!recent.length && <Link to="/account?section=jellyfin" className="n-btn-ghost" data-testid="history-connect-jellyfin">Connect Jellyfin</Link>}</Empty>:<div data-testid="history-recent-list" className="tracking-timeline">{groups.map(([day,items])=><section key={day}><ActivityDay day={day}/><div className="tracking-rows">{items.map((item,i)=><ViewingRow key={`${item.type}:${item.imdb_id}`} item={item} index={i} prefix="recent" compact/>)}</div></section>)}{filtered.length>limit && <button className="n-chip w-full" onClick={()=>setLimit(value=>value+40)}>Show more · {limit} / {filtered.length}</button>}</div>}
   <ActivityInsights recent={recent} day={prefs.day} onDayChange={value=>{update({period:'all',day:value===prefs.day?'':value});setLimit(40);}}/>
  </>}
 </div>;
}
