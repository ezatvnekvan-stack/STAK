import {dateKey} from './calendarModel';
import {watchedTimestamp,releaseDay,watchedDates} from './watchedDate';
test('watched dates preserve civil dates, reject impossible/future dates and keep edits separate',()=>{
 const timestamp=watchedTimestamp('2020-02-29',new Date('2026-10-20T08:00:00'));
 expect(dateKey(new Date(timestamp))).toBe('2020-02-29');
 for(const value of ['2026-02-31','not-a-date','2099-01-01','2026'])expect(()=>watchedTimestamp(value)).toThrow();
 const now=new Date('2026-10-20T08:00:00');expect(watchedTimestamp(dateKey(now),now)).toBe(now.toISOString());
});
test('release-day season marking keeps each episode date and rejects missing dates',()=>{
 const meta={videos:[{season:1,episode:1,released:'2020-01-01T00:00:00Z'},{season:1,episode:2,released:'2020-01-08'}]};
 expect(watchedDates(meta,[[1,1],[1,2]],null,true)).toEqual({watched_dates:[{season:1,episode:1,date:'2020-01-01'},{season:1,episode:2,date:'2020-01-08'}]});
 expect(()=>watchedDates(meta,[[1,3]],null,true)).toThrow();expect(releaseDay({year:'2020'})).toBeNull();
 expect(watchedDates({release_date:'2020-02-03'},[],null,true)).toEqual({watched_on:'2020-02-03'});
});
