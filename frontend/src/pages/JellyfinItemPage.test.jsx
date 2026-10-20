import React from 'react';
import { act, mount, until } from '../testUtils';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import JellyfinItemPage from './JellyfinItemPage';
import { api } from '../lib/api';
jest.mock('../lib/api',()=>({api:{get:jest.fn()},errMsg:error=>error.message,openExternal:jest.fn()}));
let view;
function Location(){const location=useLocation();return <p data-testid="resolved-title">{location.pathname}{location.search}</p>;}
async function open(){view=await mount(<MemoryRouter initialEntries={['/jellyfin/item/episode']}><Routes><Route path="/jellyfin/item/:id" element={<JellyfinItemPage/>}/><Route path="/title/:type/:id" element={<Location/>}/></Routes></MemoryRouter>);}
afterEach(async()=>{if(view)await view.close();view=null;jest.clearAllMocks();});
test('legacy Jellyfin links redirect to normal Details with exact episode coordinates',async()=>{
 api.get.mockResolvedValue({data:{identity:{id:'tt0903747',type:'series',season:1,episode:2}}});await open();await until(()=>view.container.querySelector('[data-testid="resolved-title"]'));expect(view.container.textContent).toContain('/title/series/tt0903747?season=1&episode=2');expect(view.container.textContent).not.toContain('Play on a device');
});
test('unmatched titles offer manual search instead of guessing or opening a playback page',async()=>{
 api.get.mockResolvedValue({data:{name:'Unknown show',reason:'No provider identifier'}});await open();await until(()=>view.container.textContent.includes('No provider identifier'));expect(view.container.querySelector('a[href="/search?q=Unknown%20show"]')).not.toBeNull();
});
test('a failed title match is recoverable with a retry',async()=>{
 api.get.mockRejectedValueOnce(new Error('Connection denied')).mockResolvedValue({data:{name:'Recovered',reason:'No provider identifier'}});await open();await until(()=>view.container.textContent.includes('Connection denied'));await act(async()=>view.container.querySelector('button').click());await until(()=>view.container.textContent.includes('No provider identifier'));expect(api.get).toHaveBeenCalledTimes(2);
});
