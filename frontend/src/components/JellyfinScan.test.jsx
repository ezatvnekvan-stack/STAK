import React from 'react';
import { act, mount, until } from '../testUtils';
import JellyfinScan from './JellyfinScan';
import { api } from '../lib/api';
jest.mock('../lib/api',()=>({api:{get:jest.fn(),post:jest.fn()},errMsg:e=>e.message,isCancelled:e=>e.code==='ERR_CANCELED'}));
afterEach(()=>{jest.useRealTimers();jest.clearAllMocks();});
test('scan uses server progress and only reports additions after a new completed task execution',async()=>{
  jest.useFakeTimers();let polls=0,counts=0,recent=0;
  api.post.mockResolvedValue({data:{ok:true}});
  api.get.mockImplementation(path=>Promise.resolve({data:path==='/jellyfin/scan-status'?[{supported:true,state:'Idle',last_result:{end_time:'old',status:'Completed'}},{supported:true,state:'Running',progress:37},{supported:true,state:'Idle',last_result:{end_time:'new',status:'Completed'}}][Math.min(polls++,2)]:path==='/jellyfin/counts'?{movies:counts++?14:13,series:13}:{items:recent++?[{Id:'new',Name:'New movie',Type:'Movie'},{Id:'ep-new',Name:'New episode',SeriesName:'Test series',Type:'Episode',ParentIndexNumber:1,IndexNumber:2}]:[]}}));
  const refreshed=jest.fn(),view=await mount(<JellyfinScan onRefresh={refreshed}/>);
  try{
    await act(async()=>{view.container.querySelector('button').click();await Promise.resolve();await Promise.resolve();});
    await act(async()=>{jest.advanceTimersByTime(1000);});
    expect(document.querySelector('[role="progressbar"]').getAttribute('aria-valuenow')).toBe('37');
    expect(document.querySelector('[role="dialog"]')).toBeNull();
    expect(document.body.textContent).not.toContain('Scan complete');
    await act(async()=>{jest.advanceTimersByTime(5000);});
    expect(document.body.textContent).toContain('Scan complete');expect(document.body.textContent).toContain('New movie');expect(document.body.textContent).toContain('Test series · S1 E2');expect(api.get.mock.calls.some(([path])=>path==='/jellyfin/scan-recent')).toBe(true);expect(document.body.textContent).toContain('+1 movies');expect(api.post).toHaveBeenCalledTimes(1);expect(refreshed).toHaveBeenCalledTimes(1);
  }finally{await view.close();}
});
test('unsupported task monitoring never displays fictional completion or a percentage',async()=>{
  api.post.mockResolvedValue({data:{ok:true}});api.get.mockResolvedValue({data:{supported:false,items:[]}});
  const view=await mount(<JellyfinScan onRefresh={jest.fn()}/>);
  try{await act(async()=>view.container.querySelector('button').click());await until(()=>document.body.textContent.includes('Scan status unconfirmed'));expect(document.body.textContent).not.toContain('Scan complete');expect(document.querySelector('[role="progressbar"]')).toBeNull();}finally{await view.close();}
});
