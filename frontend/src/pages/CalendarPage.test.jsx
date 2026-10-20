import React from 'react';
import {MemoryRouter} from 'react-router-dom';
import {act,deferred,mount,until} from '../testUtils';
import CalendarPage from './CalendarPage';
import {api} from '../lib/api';
import {monthCells,dateKey} from '../lib/calendarModel';
jest.mock('../components/AppShell',()=>({REFRESH_EVENT:'dmm-refresh'}));
jest.mock('../lib/api',()=>({IS_CAPACITOR_OFFLINE:true,api:{get:jest.fn()},errMsg:e=>e.message}));
jest.mock('../context/AppContext',()=>{const session={key:'',scope:'calendar-test',controller:new AbortController()};return {useApp:()=>({key:'',session,watchIds:new Set(),tracking:new Map(),toggleWatch:jest.fn()})};});
let mockToday='';
jest.mock('../lib/useLocalToday',()=>({useLocalToday:()=>mockToday || new Date().toISOString().slice(0,10)}));
let view;
beforeEach(()=>{mockToday='';require('../lib/api').IS_CAPACITOR_OFFLINE=true;});
afterEach(async()=>{if(view)await view.close();view=null;jest.clearAllMocks();});
const button=text=>[...view.container.querySelectorAll('button')].find(button=>button.textContent===text);
test('month grid asks for the full civil date range and shows grouped, ordered episode links',async()=>{
 const today=dateKey(),cells=monthCells(today.slice(0,7),1);
 api.get.mockImplementation(async(path,{params})=>({data:{today,range_start:params.start,range_end:params.end,events:[{imdb_id:'tt123',type:'series',name:'Example',date:today,season:1,episode:10},{imdb_id:'tt123',type:'series',name:'Example',date:today,season:1,episode:9}],errors:[]}}));
 view=await mount(<MemoryRouter><CalendarPage preferences={{date:today,calendarView:'month',scope:'following',days:30,weekStart:1}}/></MemoryRouter>);
 await until(()=>view.container.querySelectorAll('.calendar-episode-link').length===2);
 expect(api.get.mock.calls[0][1].params).toEqual({scope:'following',start:cells[0].date,end:cells[41].date});
 expect(view.container.querySelectorAll('[role="gridcell"]')).toHaveLength(42);
 expect(view.container.querySelectorAll('.calendar-release-card')).toHaveLength(1);
 expect([...view.container.querySelectorAll('.calendar-episode-link')].map(link=>link.getAttribute('href'))).toEqual(['/title/series/tt123?season=1&episode=9','/title/series/tt123?season=1&episode=10']);
});
test('out-of-order schedules cannot overwrite a different month and cached selection can be revisited',async()=>{
 const old=deferred();let initial;
 api.get.mockImplementation((path,{params})=>{if(!initial)initial=params.start;return params.start===initial?old.promise:Promise.resolve({data:{today:dateKey(),range_start:params.start,range_end:params.end,events:[],errors:[]}});});
 view=await mount(<MemoryRouter><CalendarPage/></MemoryRouter>);
 await until(()=>api.get.mock.calls.length>0);
 await act(async()=>view.container.querySelector('[aria-label="Next month"]').click());
 await until(()=>view.container.querySelector('[data-testid="calendar-empty"]'));
 await act(async()=>old.resolve({data:{today:dateKey(),range_start:initial,range_end:'old',events:[{imdb_id:'tt456',type:'movie',name:'Obsolete selection',date:dateKey()}],errors:[]}}));
 expect(view.container.textContent).not.toContain('Obsolete selection');
 expect(api.get.mock.calls[1][1].params.start).not.toBe(initial);
});
test('failed month fetch has an explicit retry and unknown grid labels',async()=>{
 api.get.mockRejectedValue(new Error('Schedule unavailable'));
 view=await mount(<MemoryRouter><CalendarPage/></MemoryRouter>);
 await until(()=>view.container.querySelector('[data-testid="calendar-error"]'));
 expect(view.container.querySelector('[data-calendar-date]').getAttribute('aria-label')).toContain('schedule not loaded');
 expect(view.container.querySelector('[data-testid="calendar-empty"]')).toBeNull();
 expect(button('Retry')).not.toBeNull();
});

test('old Trending, Week and Agenda preferences always request the Following month',async()=>{
 api.get.mockImplementation(async(path,{params})=>({data:{range_start:params.start,range_end:params.end,events:[],errors:[]}}));
 view=await mount(<MemoryRouter><CalendarPage preferences={{scope:'trending',calendarView:'agenda',date:dateKey()}}/></MemoryRouter>);
 await until(()=>view.container.querySelector('[data-testid="calendar-empty"]'));
 expect(api.get.mock.calls[0][1].params.scope).toBe('following');expect(api.get.mock.calls[0][1].params.days).toBeUndefined();
 expect(view.container.querySelectorAll('[role="gridcell"]')).toHaveLength(42);
 for(const label of ['Trending','Week','Agenda'])expect(button(label)).toBeUndefined();
 expect(view.container.querySelector('.calendar-grid-legend')).toBeNull();expect(view.container.querySelector('.calendar-loaded-at')).toBeNull();
});
test('type and artwork filters stay in a sheet and do not change the requested schedule',async()=>{
 mockToday=dateKey();
 api.get.mockImplementation(async(path,{params})=>({data:{range_start:params.start,range_end:params.end,events:[{imdb_id:'tt123',type:'series',name:'Series',date:mockToday,season:1,episode:1,background:'/backdrop.png'},{imdb_id:'tt456',type:'movie',name:'Movie',date:mockToday,background:'/movie.png'}],errors:[]}}));
 view=await mount(<MemoryRouter><CalendarPage/></MemoryRouter>);
 await until(()=>view.container.querySelector('.calendar-release-card'));
 expect(view.container.querySelector(`[data-calendar-date="${mockToday}"] img`)).toBeNull();
 await act(async()=>view.container.querySelector('[aria-label="Calendar filters"]').click());
 await act(async()=>[...document.querySelectorAll('button')].find(button=>button.textContent==='Artwork in calendar').click());
 const artwork=view.container.querySelector(`[data-calendar-date="${mockToday}"] img`);expect(artwork).not.toBeNull();
 await act(async()=>artwork.dispatchEvent(new Event('error')));expect(artwork.closest('button').dataset.artwork).toBe('false');
 await act(async()=>artwork.dispatchEvent(new Event('load')));expect(artwork.closest('button').dataset.artwork).toBe('true');
 await act(async()=>[...document.querySelectorAll('button')].find(button=>button.textContent==='Movies').click());
 expect(view.container.querySelectorAll('.calendar-release-card')).toHaveLength(1);
 expect(view.container.querySelector('.calendar-release-backdrop').getAttribute('href')).toContain('tt456');
 expect(api.get.mock.calls).toHaveLength(1);
});
test('missing artwork keeps the date cell and readable release links functional',async()=>{
 api.get.mockImplementation(async(path,{params})=>({data:{range_start:params.start,range_end:params.end,events:[{imdb_id:'tt123',type:'series',name:'No artwork',date:dateKey(),season:1,episode:1}],errors:[]}}));
 view=await mount(<MemoryRouter><CalendarPage/></MemoryRouter>);
 await until(()=>view.container.querySelector('.calendar-release-card'));
 expect(view.container.querySelector(`[data-calendar-date="${dateKey()}"] img`)).toBeNull();
 expect(view.container.querySelector('.calendar-episode-link').getAttribute('href')).toContain('season=1&episode=1');
});
test('partial legacy calendar preferences receive valid date and week defaults',async()=>{
 api.get.mockImplementation(async(path,{params})=>({data:{range_start:params.start,range_end:params.end,events:[],errors:[]}}));
 view=await mount(<MemoryRouter><CalendarPage preferences={{scope:'following'}}/></MemoryRouter>);
 await until(()=>view.container.querySelector('[data-testid="calendar-empty"]'));
 expect(view.container.querySelectorAll('[role="gridcell"]')).toHaveLength(42);
 expect(view.container.querySelector('.stak-recovery')).toBeNull();
});

test('an unauthenticated server preview offers setup instead of a permanently loading calendar',async()=>{
 require('../lib/api').IS_CAPACITOR_OFFLINE=false;
 view=await mount(<MemoryRouter><CalendarPage/></MemoryRouter>);
 expect(view.container.querySelector('[data-testid="loading-indicator"]')).toBeNull();expect(api.get).not.toHaveBeenCalled();
 expect(view.container.querySelector('a[href="/account"]')).not.toBeNull();
 require('../lib/api').IS_CAPACITOR_OFFLINE=true;
});
