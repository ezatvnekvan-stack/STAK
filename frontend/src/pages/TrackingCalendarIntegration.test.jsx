import React from 'react';
import {MemoryRouter} from 'react-router-dom';
import {act,mount,until} from '../testUtils';
import TrackingPage from './TrackingPage';
import {api} from '../lib/api';
jest.mock('../lib/api',()=>({IS_CAPACITOR_OFFLINE:true,api:{get:jest.fn(),put:jest.fn()},errMsg:e=>e.message,isCancelled:()=>false}));
jest.mock('../context/AppContext',()=>{const session={key:'test-only',scope:'tracking-integration',controller:new AbortController()};return {useApp:()=>({key:'test-only',session,tracking:new Map(),watchIds:new Set(),refreshTracking:jest.fn()})};});
jest.mock('../lib/useTrackingScroll',()=>({useTrackingScroll:()=>()=>{}}));
jest.mock('../components/AppShell',()=>({REFRESH_EVENT:'dmm-refresh'}));
test.each(['month','agenda'])('Activity switches into the actual Calendar with saved %s layout, and migrates removed layouts to Month',async layout=>{
 let preferences={tracking:{view:'activity',calendarView:layout}};
 api.get.mockImplementation(async(path,config)=>({data:path==='/prefs/experience'?preferences:path==='/tracking/history'?{recent:[],continue:[],stats:{},local_only:true}:path==='/calendar'?{events:[],range_start:config.params.start,range_end:config.params.end}:undefined}));
 api.put.mockImplementation(async(path,body)=>{preferences={...preferences,[body.section]:body.value};return {data:preferences};});
 const view=await mount(<MemoryRouter initialEntries={['/tracking?view=activity']}><TrackingPage/></MemoryRouter>);
 try{
  await until(()=>view.container.querySelector('[data-testid="history-stat-movies"]'));
  await act(async()=>view.container.querySelector('#tracking-tab-calendar').click());
  await until(()=>view.container.querySelector('[data-testid="tracking-calendar"]'));
  await until(()=>view.container.querySelector('[data-testid="calendar-month"]'));
  expect(view.container.querySelector('[aria-label="Calendar layout"]')).toBeNull();
  expect(view.container.querySelectorAll('[role="gridcell"]')).toHaveLength(42);
  expect(view.container.querySelector('.stak-recovery')).toBeNull();
 }finally{await view.close();jest.clearAllMocks();}
});
