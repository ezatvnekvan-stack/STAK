import {activityPeriodRows,activitySummary,normalizeTracking} from './trackingModel';
import {migrateNavigation,readNavPrefs,NAV_PREF_KEY} from './trackingNavigation';
afterEach(()=>localStorage.clear());
test('Activity period counts exclude future/invalid dates and use known content runtime only',()=>{
 const items=[{type:'movie',last_played:'2026-10-19T10:00:00',duration_hours:1.5},{type:'episode',last_played:'2026-10-13T10:00:00'},{type:'movie',last_played:'2026-10-12T10:00:00',duration_hours:2},{type:'movie',last_played:'2026-10-20T10:00:00'},{type:'movie',last_played:'bad'}];
 const rows=activityPeriodRows(items,'week',new Date('2026-10-19T12:00:00'));expect(rows).toHaveLength(2);
 expect(activitySummary(rows)).toEqual({movies:1,episodes:1,hours:1.5,knownRuntime:1,total:2});expect(activityPeriodRows(items,'all')).toBe(items);
});
test('malformed Tracking preferences cannot select invalid dates or unsupported modes',()=>{
 const prefs=normalizeTracking({date:'2026-02-31',view:'broken',calendarView:'broken',days:100000,weekStart:4,period:'forever',filter:'series'});
 expect(prefs.date).not.toBe('2026-02-31');expect(prefs.view).toBe('activity');expect(prefs.days).toBe(30);expect(prefs.weekStart).toBe('locale');expect(prefs.filter).toBe('series');expect(prefs.scope).toBe('following');expect(prefs.calendarView).toBe('month');expect(prefs.period).toBe('all');
});
test('old Activity/Calendar navigation entries become one Tracking entry while keeping user order and TorBox',()=>{
 expect(migrateNavigation(['torbox','calendar','home','history','progress','account'])).toEqual(['torbox','tracking','home','account']);
 localStorage.setItem(NAV_PREF_KEY,JSON.stringify(['home','calendar','discover','history','account']));expect(readNavPrefs()).toEqual(['home','tracking','discover','account']);
 localStorage.setItem(NAV_PREF_KEY,'broken');expect(readNavPrefs()).toEqual(['home','discover','watchlist','tracking','library','account']);
});
