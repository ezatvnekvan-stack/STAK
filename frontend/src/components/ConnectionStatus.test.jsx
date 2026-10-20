import React from 'react';
import {MemoryRouter} from 'react-router-dom';
import {act,mount,until} from '../testUtils';
import ConnectionStatus from './ConnectionStatus';
import {api} from '../lib/api';
jest.mock('../lib/useConnections',()=>({useConnections:()=>({st:{tb:'ok',jf:'ok',ix:'ok',ixCount:2},busy:false,check:async()=>{}})}));
jest.mock('../context/AppContext',()=>{const session={key:'test-only',scope:'connections-test',controller:new AbortController()};return {useApp:()=>({session})};});
jest.mock('../lib/api',()=>({api:{get:jest.fn()}}));
let view;
afterEach(async()=>{if(view)await view.close();view=null;jest.clearAllMocks();});
test('configured Jellyfin and indexers are not advertised as verified connections',async()=>{
 view=await mount(<MemoryRouter><ConnectionStatus/></MemoryRouter>);
 const rows=view.container.querySelectorAll('.connection-row');
 expect(rows[0].textContent).toContain('Verified');expect(rows[1].textContent).toContain('Configured');expect(rows[2].textContent).toContain('2 configured');
 expect(rows[1].getAttribute('href')).toBe('/account?section=jellyfin');expect(api.get).not.toHaveBeenCalled();
});
test('Jellyfin verification requires every real diagnostic check to pass',async()=>{
 api.get.mockResolvedValue({data:{connected:true,checks:[{ok:true},{ok:false}]}});
 view=await mount(<MemoryRouter><ConnectionStatus/></MemoryRouter>);
 await act(async()=>view.container.querySelector('button').click());await until(()=>view.container.textContent.includes('Unavailable'));
 expect(api.get.mock.calls[0][1].timeout).toBe(15000);
 api.get.mockResolvedValue({data:{connected:true,checks:[{ok:true},{ok:true},{ok:true}]}});
 await act(async()=>view.container.querySelector('button').click());await until(()=>view.container.querySelectorAll('.connection-detail')[1].textContent==='Verified');
});
