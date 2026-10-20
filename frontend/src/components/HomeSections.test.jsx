import React from 'react';
import {MemoryRouter} from 'react-router-dom';
import { act,mount } from '../testUtils';
import JellyfinScan from "./JellyfinScan";
import HomeSections from './HomeSections';
import {api} from '../lib/api';
jest.mock('../lib/home',()=>({useHomePrefs:()=>[[]],sectionInfo:()=>null}));
jest.mock('../context/AppContext',()=>({useApp:()=>({session:{scope:'home-test'},watchlist:[]})}));
jest.mock('../lib/useAccountQuery',()=>({useAccountQuery:()=>({data:{connected:true,is_admin:true}})}));
jest.mock('../pages/SearchPage',()=>({hideFilters})=><input data-testid="media-search-input" data-hide-filters={hideFilters}/>);
jest.mock('./MediaHero',()=>()=> <div data-testid="media-hero"/>);jest.mock('./OnboardingCard',()=>()=>null);
jest.mock('../lib/api',()=>({api:{get:jest.fn(),post:jest.fn()},errMsg:error=>error.message,isCancelled:()=>false}));
test('Home keeps one search and library scanning remains a real reusable action',async()=>{
  jest.useFakeTimers();let status=0;api.post.mockResolvedValue({data:{ok:true}});
  api.get.mockImplementation(path=>Promise.resolve({data:path==='/jellyfin/scan-status'?(status++?{supported:true,state:'Running',progress:42}:{supported:true,state:'Idle',last_result:{end_time:'previous'}}):path==='/jellyfin/counts'?{movies:13,series:13}:{items:[]}}));
  const view=await mount(<MemoryRouter><HomeSections/><JellyfinScan/></MemoryRouter>);
  try{expect(view.container.querySelector('[data-testid="media-search-input"]').dataset.hideFilters).toBe('true');expect(view.container.querySelector('.scan-trigger .scan-jellyfin-logo').getAttribute('viewBox')).toBe('0 0 64 64');expect(view.container.querySelectorAll('.scan-trigger [data-jellyfin-logo] path')).toHaveLength(3);expect(view.container.querySelector('.scan-trigger').classList.contains('nothing-action')).toBe(true);await act(async()=>{view.container.querySelector('button').click();await Promise.resolve();await Promise.resolve();});await act(async()=>jest.advanceTimersByTime(1000));expect(view.container.querySelector('[role="progressbar"]').getAttribute('aria-valuenow')).toBe('42');expect(document.querySelector('[role="dialog"]')).toBeNull();expect(api.post).toHaveBeenCalledWith('/jellyfin/scan',undefined,expect.anything());}finally{await view.close();jest.useRealTimers();jest.clearAllMocks();}
});

test('search unmounts Home content rather than leaving hidden provider effects running',async()=>{
 const view=await mount(<MemoryRouter initialEntries={['/?q=movie']}><HomeSections/></MemoryRouter>);
 try{expect(view.container.querySelector('[data-testid="media-search-input"]')).not.toBeNull();expect(view.container.querySelector('[data-testid="media-hero"]')).toBeNull();expect(view.container.querySelector('.home-feed')).toBeNull();}finally{await view.close();}
});
