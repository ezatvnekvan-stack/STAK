import {dateKey,validDate,addDays,shiftMonth,monthCells,weekCells,calendarRange,orderedCalendarEvents,calendarTitleGroups,localeWeekStart,dateLabel} from './calendarModel';
test('civil-date math handles leap days, year boundaries and DST without UTC drift',()=>{
 expect(validDate('2024-02-29')).toBe(true);expect(validDate('2025-02-29')).toBe(false);
 expect(validDate('2026-02-31')).toBe(false);expect(validDate('2026-13-01')).toBe(false);
 expect(addDays('2026-03-08',1)).toBe('2026-03-09');expect(addDays('2026-11-01',1)).toBe('2026-11-02');
 expect(addDays('2026-12-31',1)).toBe('2027-01-01');expect(shiftMonth('2026-01',-1)).toBe('2025-12');expect(dateKey('2026-10-19')).toBe('2026-10-19');
});
test('month grids have 42 consecutive cells and respect the chosen first weekday',()=>{
 for(const first of [0,1]){const cells=monthCells('2026-02',first);expect(cells).toHaveLength(42);expect(new Date(cells[0].date+'T12:00:00').getDay()).toBe(first);expect(new Set(cells.map(cell=>cell.date)).size).toBe(42);expect(cells.filter(cell=>cell.inMonth)).toHaveLength(28);}
 expect(localeWeekStart('en-US')).toBe(0);expect(localeWeekStart('hu-HU')).toBe(1);
});
test('week grids span year boundaries, respect week starts and contain exactly seven dates',()=>{
 expect(weekCells('2027-01-01',1).map(cell=>cell.date)).toEqual(['2026-12-28','2026-12-29','2026-12-30','2026-12-31','2027-01-01','2027-01-02','2027-01-03']);
 expect(weekCells('2027-01-01',0)[0].date).toBe('2026-12-27');
 expect(weekCells('2024-02-29',1).some(cell=>cell.date==='2024-02-29')).toBe(true);
});
test('calendar labels retain weekday semantics and survive invalid device locales and dates',()=>{
 expect(dateLabel('2026-10-19',{weekday:'narrow'})).toBe('M');
 const language=jest.spyOn(navigator,'language','get').mockReturnValue('invalid_locale');
 try{expect(dateLabel('2026-10-19',{month:'long',day:'numeric'})).toBe('October 19');expect(dateLabel('invalid')).toBe('invalid');}finally{language.mockRestore();}
});
test('explicit month requests are bounded and legacy agenda windows remain compatible',()=>{
 expect(calendarRange({start:'2026-02-01',end:'2026-03-14'},'2026-10-19')).toEqual({today:'2026-10-19',start:'2026-02-01',end:'2026-03-14'});
 for(const params of [{start:'2026-02-01',end:'2026-03-15'},{start:'bad',end:'2026-02-01'},{start:'2026-03-01',end:'2026-02-01'},{start:'2026-02-01'}])expect(()=>calendarRange(params)).toThrow();
 expect(calendarRange({days:7},'2026-10-19').end).toBe('2026-10-26');
});
test('release grouping removes exact duplicates while preserving distinct same-day episodes in numeric order',()=>{
 const event={type:'series',imdb_id:'tt123',name:'Example',date:'2026-10-19',season:1};
 const events=[{...event,episode:10},{...event,episode:9},{...event,episode:10},{...event,episode:11,date:'bad'}];
 expect(orderedCalendarEvents(events).map(item=>item.episode)).toEqual([9,10]);
 const groups=calendarTitleGroups(events);expect(groups).toHaveLength(1);expect(groups[0].episodes).toHaveLength(2);
 expect(calendarTitleGroups([...events,{...event,imdb_id:'tt456',episode:1}])).toHaveLength(2);
});
