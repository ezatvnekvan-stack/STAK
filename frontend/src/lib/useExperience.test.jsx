import React from 'react';
import { act, deferred, mount, until } from '../testUtils';
import { useExperience } from './useExperience';
import { api } from './api';
import { createAccountSession } from './accountQueries';
import { accountQueryKey } from './accountQueries';
import { useApp } from '../context/AppContext';
jest.mock('../context/AppContext',()=>({useApp:jest.fn()}));
jest.mock('./api',()=>({IS_CAPACITOR_OFFLINE:true,api:{get:jest.fn(),put:jest.fn()},errMsg:error=>error.message,isCancelled:()=>false}));
let view,preference,setPreference;
function Probe(){[preference,setPreference]=useExperience('sources',{});return <p>{preference.res}</p>;}
beforeEach(()=>{jest.clearAllMocks();useApp.mockReturnValue({session:createAccountSession('test-only')});api.put.mockResolvedValue({data:{sources:{res:'4K'}}});});
afterEach(async()=>{if(view)await view.close();view=null;});
test('source preferences hydrate once and keep a user edit when a delayed read arrives',async()=>{
  const read=deferred();api.get.mockReturnValue(read.promise);view=await mount(<Probe/>);
  await act(async()=>setPreference(value=>({...value,res:'4K'})));
  await act(async()=>read.resolve({data:{sources:{res:'720p'}}}));
  expect(preference.res).toBe('4K');expect(api.put.mock.calls[0][1].value.res).toBe('4K');
});
test('saved source preferences load without writing defaults over the existing record',async()=>{
  api.get.mockResolvedValue({data:{sources:{res:'1080p',cachedOnly:true,maxSize:'20'}}});view=await mount(<Probe/>);
  await until(()=>preference.res==='1080p');expect(preference.cachedOnly).toBe(true);expect(api.put).not.toHaveBeenCalled();
});
test('a cache containing another section does not block saved source preferences arriving later',async()=>{
  api.get.mockResolvedValue({data:{discovery:{type:'movie'}}});view=await mount(<Probe/>);
  await until(()=>view.client.getQueryData(accountQueryKey(useApp.mock.results[0].value.session,'/prefs/experience'))?.discovery);
  await act(async()=>view.client.setQueryData(accountQueryKey(useApp.mock.results[0].value.session,'/prefs/experience'),{discovery:{type:'movie'},sources:{res:'1080p',cachedOnly:true}}));
  await until(()=>preference.res==='1080p');expect(preference.cachedOnly).toBe(true);expect(api.put).not.toHaveBeenCalled();
});
