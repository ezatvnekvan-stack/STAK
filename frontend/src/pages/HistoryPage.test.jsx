import React from 'react';
import {MemoryRouter} from 'react-router-dom';
import {act,mount,until} from '../testUtils';
import HistoryPage from './HistoryPage';
import {api} from '../lib/api';
jest.mock('../components/AppShell',()=>({REFRESH_EVENT:'dmm-refresh'}));
jest.mock('../lib/api',()=>({IS_CAPACITOR_OFFLINE:true,api:{get:jest.fn(),post:jest.fn()},errMsg:e=>e.message,isCancelled:()=>false}));
jest.mock('../context/AppContext',()=>{const session={key:'',scope:'activity-test',controller:new AbortController()};return {useApp:()=>({key:'',session,watchIds:new Set(),tracking:new Map(),refreshTracking:jest.fn().mockResolvedValue(undefined)})};});
let view;afterEach(async()=>{if(view)await view.close();view=null;jest.clearAllMocks();});
test('compact Activity summary uses selected-period content runtimes and retains continue-watching links',async()=>{
 api.get.mockResolvedValue({data:{recent:[{imdb_id:'tt123',type:'movie',name:'Recent movie',played:true,last_played:new Date().toISOString(),duration_hours:1.5},{imdb_id:'tt456',type:'episode',name:'Recent episode',series_name:'Series',season:1,episode:2,played:true,last_played:new Date().toISOString()}],continue:[{imdb_id:'tt789',type:'episode',name:'Continue episode',series_name:'Other',season:1,episode:3,progress:20}],local_only:false}});
 view=await mount(<MemoryRouter><HistoryPage/></MemoryRouter>);
 await until(()=>view.container.querySelector('[data-testid="history-stat-movies"]'));
 expect(view.container.querySelector('[data-testid="history-stat-movies"]').textContent).toBe('1');expect(view.container.querySelector('[data-testid="history-stat-episodes"]').textContent).toBe('1');expect(view.container.querySelector('[data-testid="history-stat-hours"]').textContent).toBe('1.5h');
 expect(view.container.textContent).toContain('Known runtime');expect(view.container.querySelector('[data-testid="continue-item-0"]').getAttribute('href')).toContain('season=1&episode=3');
 await act(async()=>[...view.container.querySelectorAll('button')].find(button=>button.textContent==='Movies').click());
 expect(view.container.querySelectorAll('[data-testid^="recent-item-"]')).toHaveLength(1);
 expect(view.container.querySelector('[aria-label="About activity"]')).not.toBeNull();
});
test('all-time history shows one compact series row with progress and All/Movies/Series filters',async()=>{
 api.get.mockResolvedValue({data:{recent:[{imdb_id:'tt456',type:'episode',name:'Episode one',series_name:'Series',season:1,episode:1,total_episodes:4,played:true,last_played:'2020-01-01'},{imdb_id:'tt456',type:'episode',name:'Episode two',series_name:'Series',season:1,episode:2,total_episodes:4,played:true,last_played:'2020-01-08'}],continue:[],local_only:true}});
 view=await mount(<MemoryRouter><HistoryPage/></MemoryRouter>);await until(()=>view.container.querySelector('[data-testid="recent-item-0"]'));
 expect(view.container.querySelectorAll('[data-testid^="recent-item-"]')).toHaveLength(1);expect(view.container.textContent).not.toContain('Episode one');expect(view.container.textContent).not.toContain('Episode two');expect(view.container.textContent).toContain('2 / 4 episodes watched');
 expect(view.container.querySelector('[aria-label="Series series progress"]').getAttribute('aria-valuenow')).toBe('50');
 expect(view.container.querySelector('[aria-label="Activity period"]').textContent).toContain('All time');
 expect([...view.container.querySelectorAll('[aria-label="Activity media type"] button')].map(button=>button.textContent)).toEqual(['All','Movies','Series']);
});
test('selecting an older activity day still shows a series watched again on a later day',async()=>{
 api.get.mockResolvedValue({data:{recent:[{imdb_id:'tt456',type:'episode',series_name:'Series',season:1,episode:1,played:true,last_played:'2020-01-01'},{imdb_id:'tt456',type:'episode',series_name:'Series',season:1,episode:2,played:true,last_played:'2020-01-08'}],continue:[],local_only:true}});
 view=await mount(<MemoryRouter><HistoryPage preferences={{day:'2020-01-01'}}/></MemoryRouter>);await until(()=>view.container.querySelector('[data-testid="recent-item-0"]'));
 expect(view.container.querySelector('[data-testid="recent-item-0"]').textContent).toContain('Series');expect(view.container.querySelectorAll('[data-testid^="recent-item-"]')).toHaveLength(1);
});
