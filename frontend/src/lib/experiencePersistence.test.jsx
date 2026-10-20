import React from 'react';
import {useQueryClient} from '@tanstack/react-query';
import {accountQueryKey} from './accountQueries';
import {MemoryRouter} from 'react-router-dom';
import {act,deferred,mount,until} from '../testUtils';
import {useExperience} from './useExperience';
import {api} from './api';
jest.mock('./api',()=>({api:{put:jest.fn()},errMsg:error=>error.message,isCancelled:()=>false}));
jest.mock('./useAccountQuery',()=>({useAccountQuery:()=>({data:{discovery:{type:'series',region:'HU',category:'top',filters:{provider:'netflix'}}}})}));
jest.mock('../context/AppContext',()=>{const session={scope:'prefs-test',key:'fixture',controller:new AbortController()};return {useApp:()=>({session})};});
let queryClient;
function Probe(){queryClient=useQueryClient();const [value,setValue]=useExperience('discovery',{});return <><p>{value.type} {value.region} {value.filters.provider}</p><button onClick={()=>setValue(previous=>({...previous,filters:{...previous.filters,provider:'prime'}}))}>Prime</button><button onClick={()=>setValue(previous=>({...previous,filters:{...previous.filters,provider:'hulu'}}))}>Hulu</button></>;}
test('cached filters hydrate immediately and rapid changes persist in order without stale UI',async()=>{
 const first=deferred(),second=deferred();api.put.mockReturnValueOnce(first.promise).mockReturnValueOnce(second.promise);
 const view=await mount(<MemoryRouter><Probe/></MemoryRouter>);
 try{expect(view.container.textContent).toContain('series HU netflix');const buttons=view.container.querySelectorAll('button');await act(async()=>buttons[0].click());await act(async()=>buttons[1].click());expect(api.put).toHaveBeenCalledTimes(1);expect(view.container.textContent).toContain('hulu');expect(queryClient.getQueryData(accountQueryKey({scope:'prefs-test'},'/prefs/experience')).discovery.filters.provider).toBe('hulu');await act(async()=>first.resolve({data:{discovery:{filters:{provider:'prime'}}}}));await until(()=>api.put.mock.calls.length===2);expect(view.container.textContent).toContain('hulu');expect(api.put.mock.calls[1][1].value.filters.provider).toBe('hulu');await act(async()=>second.resolve({data:{discovery:{filters:{provider:'hulu'}}}}));}finally{await view.close();jest.clearAllMocks();}
});
