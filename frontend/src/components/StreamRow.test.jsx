import React from 'react';
import { MemoryRouter, useLocation } from 'react-router-dom';
import { act, mount, until } from '../testUtils';
import StreamRow from './StreamRow';
import { useApp } from '../context/AppContext';
import { addTorrent, copyText } from '../lib/api';
jest.mock('../context/AppContext',()=>({useApp:jest.fn()}));
jest.mock('../lib/api',()=>({addTorrent:jest.fn(),copyText:jest.fn(),buildMagnet:hash=>`magnet:?xt=urn:btih:${hash}`,errMsg:error=>error.message}));
jest.mock('../lib/haptics',()=>({buzz:jest.fn()}));
const hash='a'.repeat(40),source={hash:hash.toUpperCase(),name:'A real release',resolution:'1080p',size:100,seeders:12};
let view;
function Probe(){const location=useLocation();return <p data-testid="location">{location.pathname}{location.search}</p>;}
beforeEach(()=>{jest.clearAllMocks();useApp.mockReturnValue({key:'test-only',library:[{id:42,hash,download_finished:true}],libraryHashes:new Set([hash]),refreshLibrary:jest.fn()});});
afterEach(async()=>{if(view)await view.close();view=null;});
test('duplicate source opens its existing TorBox item without sending another add request',async()=>{
  view=await mount(<MemoryRouter><StreamRow s={source} index={0} cached/><Probe/></MemoryRouter>);
  expect(view.container.textContent).toContain('Ready on TorBox');
  await act(async()=>view.container.querySelector('[data-testid="add-to-torbox-button-0"]').click());
  expect(addTorrent).not.toHaveBeenCalled();expect(view.container.querySelector('[data-testid="location"]').textContent).toBe('/torbox?torrent=42');
});
test('clipboard failures settle without an unhandled rejection',async()=>{
  copyText.mockRejectedValue(new Error('Clipboard denied'));
  view=await mount(<MemoryRouter><StreamRow s={source} index={0}/></MemoryRouter>);
  await act(async()=>view.container.querySelector('[data-testid="copy-magnet-button-0"]').click());
  await until(()=>copyText.mock.calls.length===1);
  expect(copyText).toHaveBeenCalledTimes(1);
});

test('successful add stays added even before a background library refresh completes',async()=>{
 const refreshLibrary=jest.fn().mockResolvedValue();useApp.mockReturnValue({key:'test-only',library:[],libraryHashes:new Set(),refreshLibrary});addTorrent.mockResolvedValue({data:{detail:'Added'}});
 view=await mount(<MemoryRouter><StreamRow s={source} index={0} cached/><Probe/></MemoryRouter>);
 await act(async()=>view.container.querySelector('[data-testid="add-to-torbox-button-0"]').click());
 expect(view.container.textContent).toContain('Open TorBox');
 await act(async()=>view.container.querySelector('[data-testid="add-to-torbox-button-0"]').click());
 expect(addTorrent).toHaveBeenCalledTimes(1);expect(view.container.querySelector('[data-testid="location"]').textContent).toBe('/torbox');
});
test('failed add shows retry and preserves the error instead of claiming success',async()=>{
 useApp.mockReturnValue({key:'test-only',library:[],libraryHashes:new Set(),refreshLibrary:jest.fn()});addTorrent.mockRejectedValue(new Error('TorBox unavailable'));
 view=await mount(<MemoryRouter><StreamRow s={source} index={0}/></MemoryRouter>);
 await act(async()=>view.container.querySelector('[data-testid="add-to-torbox-button-0"]').click());
 expect(view.container.querySelector('[role="alert"]').textContent).toBe('TorBox unavailable');expect(view.container.textContent).toContain('Retry add');expect(view.container.textContent).not.toContain('Open TorBox');
});
test('a cached prologue remains selectable but cannot receive a suggested match badge',async()=>{
 view=await mount(<MemoryRouter><StreamRow s={{...source,name:'The Odyssey - Prologue (2025) 4K ProRes'}} index={0} cached best matchContext={{meta:{name:'The Odyssey',year:'2026',type:'movie'}}}/></MemoryRouter>);
 expect(view.container.textContent).toContain('Check title match');expect(view.container.querySelector('.source-suggestion')).toBeNull();expect(view.container.querySelector('[data-testid="add-to-torbox-button-0"]')).not.toBeNull();
});
test('pack size uses the verified torrent total and plus action has no visible text',async()=>{
 view=await mount(<MemoryRouter><StreamRow s={{...source,size:1024,torrent_size:8*1024**3,size_scope:'file',name:'Show.S01.HDR.Atmos.WEB-DL.REMUX'}} pack={{season:1,episodes:8}} index={0}/></MemoryRouter>);
 expect(view.container.querySelector('.source-size').textContent).toBe('8.00 GB · pack');
 expect([...view.container.querySelectorAll('.nothing-source-badge')].map(node=>node.textContent)).toEqual(expect.arrayContaining(['HDR','Atmos','WEB-DL','REMUX']));
 const action=view.container.querySelector('.source-add');expect(action.querySelector('span').className).toBe('sr-only');expect(action.getAttribute('aria-label')).toBe('Open existing TorBox torrent');
});
