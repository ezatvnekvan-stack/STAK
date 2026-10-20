import React from 'react';
import { MemoryRouter } from 'react-router-dom';
import { act, mount, until } from '../testUtils';
import JellyfinLibrary from './JellyfinLibrary';
import { api } from '../lib/api';
jest.mock('../lib/api', () => ({ api: { get: jest.fn(), post: jest.fn() }, errMsg: error => error.message }));
jest.mock('../context/AppContext',()=>({useApp:()=>({watchIds:new Set(),tracking:new Map(),toggleWatch:jest.fn()})}));
jest.mock('./JellyfinImage', () => () => <span>Artwork</span>);
let view;
afterEach(async () => { if (view) await view.close(); view = null; jest.clearAllMocks(); });
test('authentication failure offers reconnect and never claims that the library is empty', async () => {
  let denied = true;
  api.get.mockImplementation(path => path === '/jellyfin' ? Promise.resolve({ data: { connected: true } })
    : denied ? Promise.reject(new Error('Jellyfin rejected the saved session')) : Promise.resolve({ data: { items: [] } }));
  view = await mount(<MemoryRouter><JellyfinLibrary /></MemoryRouter>);
  await until(() => view.container.querySelector('[role="alert"]'));
  expect(view.container.textContent).toContain('Jellyfin rejected');
  expect(view.container.textContent).not.toContain('No libraries');
  expect(view.container.querySelector('a[href="/account?section=jellyfin"]')).not.toBeNull();
  denied = false;
  await act(async () => view.container.querySelector('[role="alert"] button').click());
  await until(() => view.container.textContent.includes('No libraries are available'));
});
test('non-admin library browsing does not expose server scan', async () => {
  api.get.mockResolvedValue({ data: { connected: true, is_admin: false, items: [] } });
  view = await mount(<MemoryRouter><JellyfinLibrary /></MemoryRouter>);
  await until(() => view.container.textContent.includes('No libraries are available'));
  expect(view.container.textContent).not.toContain('Request library scan');expect(view.container.textContent).not.toContain('Connection diagnostics');
});
test('only the selected library is requested; switching uses real parent IDs and cached pages',async()=>{
  api.get.mockImplementation((path,config)=>Promise.resolve({data:path==='/jellyfin'?{connected:true}:path==='/jellyfin/libraries'?{items:[{Id:'movies',Name:'Films'},{Id:'tv',Name:'Shows'}]}:{items:[{Id:`${config.params.parent_id}-1`,Name:'Real title',Type:'Movie',ProviderIds:{Imdb:'tt123'}}],total:1}}));
  view=await mount(<MemoryRouter><JellyfinLibrary/></MemoryRouter>);
  await until(()=>view.container.textContent.includes('Real title'));
  expect(api.get.mock.calls.filter(([p])=>p==='/jellyfin/library-items').map(([,c])=>c.params.parent_id)).toEqual([undefined]);
  await act(async()=>[...view.container.querySelectorAll('button')].find(b=>b.textContent==='Shows').click());
  await until(()=>api.get.mock.calls.some(([p,c])=>p==='/jellyfin/library-items' && c.params.parent_id==='tv'));
  await until(()=>view.container.textContent.includes('Real title'));
  await act(async()=>[...view.container.querySelectorAll('button')].find(b=>b.textContent==='Films').click());
  expect(api.get.mock.calls.filter(([p])=>p==='/jellyfin/library-items')).toHaveLength(3);
});


test('saved library results show freshness and retry instead of a false live or empty state',async()=>{
  api.get.mockImplementation(path=>Promise.resolve({data:path==='/jellyfin'?{connected:true}:path==='/jellyfin/libraries'?{items:[{Id:'movies',Name:'Films'}]}:path==='/jellyfin/library-items'?{offline:true,cached_at:'2026-10-15T10:00:00Z',error:'Session expired',items:[{Id:'cached',Name:'Saved movie',Type:'Movie',ProviderIds:{Imdb:'tt123'}}],total:1}:{}}));
  view=await mount(<MemoryRouter><JellyfinLibrary/></MemoryRouter>);
  await until(()=>view.container.textContent.includes('Saved movie'));
  expect(view.container.textContent).toContain('Saved library');expect(view.container.textContent).toContain('Session expired');
  expect([...view.container.querySelectorAll('button')].some(button=>button.textContent==='Retry live library')).toBe(true);
  expect(view.container.textContent).not.toContain('No matching titles');
});


test('returning from Details restores the selected library without crossing a server change',async()=>{
  api.get.mockImplementation((path,config)=>Promise.resolve({data:path==='/jellyfin'?{connected:true}:path==='/jellyfin/libraries'?{items:[{Id:'movies',Name:'Films'},{Id:'tv',Name:'Shows'}]}:path==='/jellyfin/library-items'?{items:[{Id:config.params.parent_id,Name:'A title',Type:'Movie',ProviderIds:{Imdb:'tt123'}}],total:1}:{}}));
  view=await mount(<MemoryRouter><JellyfinLibrary/></MemoryRouter>);await until(()=>view.container.textContent.includes('A title'));
  await act(async()=>[...view.container.querySelectorAll('button')].find(button=>button.textContent==='Shows').click());await until(()=>api.get.mock.calls.some(([path,config])=>path==='/jellyfin/library-items' && config.params.parent_id==='tv'));
  await view.close();view=null;api.get.mockClear();
  view=await mount(<MemoryRouter><JellyfinLibrary/></MemoryRouter>);await until(()=>view.container.textContent.includes('A title'));
  expect(api.get.mock.calls.filter(([path])=>path==='/jellyfin/library-items')[0][1].params.parent_id).toBe('tv');
  await act(async()=>window.dispatchEvent(new Event('dmm-jellyfin-change')));
  await until(()=>[...view.container.querySelectorAll('button')].find(button=>button.textContent==='All')?.getAttribute('aria-pressed')==='true');
});

test('storage is deferred to Server details while browsing and layout changes reuse loaded pages',async()=>{
 await act(async()=>window.dispatchEvent(new Event('dmm-jellyfin-change')));
 api.get.mockImplementation(path=>Promise.resolve({data:path==='/jellyfin'?{connected:true,is_admin:true}:path==='/jellyfin/libraries'?{items:[{Id:'films',Name:'Films'}]}:path==='/jellyfin/library-items'?{items:[{Id:'a',Name:'A movie',Type:'Movie',ProviderIds:{Imdb:'tt123'}}],total:20}:path==='/jellyfin/counts'?{movies:14,series:21}:path==='/jellyfin/storage'?{bytes:1e9,complete:true}:{}}));
 view=await mount(<MemoryRouter><JellyfinLibrary/></MemoryRouter>);await until(()=>view.container.textContent.includes('A movie'));
 expect(api.get.mock.calls.some(([path])=>path==='/jellyfin/storage')).toBe(false);
 expect(view.container.textContent).toContain('1 titles loaded · 20 server items');
 const before=api.get.mock.calls.filter(([path])=>path==='/jellyfin/library-items').length;
 await act(async()=>view.container.querySelector('[aria-label="List view"]').click());
 expect(view.container.querySelector('[data-testid="jellyfin-collection"]').getAttribute('data-layout')).toBe('list');
 expect(api.get.mock.calls.filter(([path])=>path==='/jellyfin/library-items')).toHaveLength(before);
 await act(async()=>view.container.querySelector('[aria-label="Server details"]').click());
 await until(()=>document.querySelector('[data-testid="jellyfin-server-details"]'));
 expect(api.get.mock.calls.some(([path])=>path==='/jellyfin/storage')).toBe(true);
 expect(document.querySelector('a[href="/remote"]')).not.toBeNull();
});
