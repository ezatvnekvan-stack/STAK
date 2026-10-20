import { act, mount, until } from "../testUtils";
import React from "react";
import { MemoryRouter, Routes, Route } from "react-router-dom";
import { AppProvider } from "../context/AppContext";
import TitlePage from "./TitlePage";
import { api, checkCached } from "../lib/api";
import { fetchIndexers, fetchStreams } from "../lib/torrentio";

jest.mock("../lib/api", () => ({
  KEY_STORAGE:"tb_api_key", getKey:()=>global.localStorage.getItem("tb_api_key")||"",
  api:{get:jest.fn(),post:jest.fn(),put:jest.fn(),delete:jest.fn()}, checkCached:jest.fn(),
  torrentioEnabled:()=>true, errMsg:error=>error.message, isCancelled:error=>error?.code==="ERR_CANCELED", openExternal:jest.fn(),
}));
jest.mock("../lib/torrentio", () => ({fetchStreams:jest.fn(),fetchIndexers:jest.fn(),mergeStreams:(...lists)=>lists.flat()}));
jest.mock("../components/RatingsBlock",()=>()=>null);
jest.mock("../components/DetailExtras",()=>()=>null);
jest.mock("../components/StreamRow",()=>({s})=><div>{s.name}</div>);

let view;
const videos=[{season:"1",episode:"1",name:"Pilot"},{season:1,episode:2,name:"Second"},{season:2,episode:3,name:"Return"}];
beforeEach(()=>{
  localStorage.clear(); localStorage.setItem("tb_api_key","test-account"); jest.clearAllMocks();
  api.get.mockImplementation(path=>Promise.resolve({data:path.startsWith("/jellyfin/title-availability?") ? {state:"disconnected",in_library:null} : {
    "/metadata/settings":{settings:{ratings:true,imdbOnLists:false},status:{}},"/prefs/experience":{},"/torbox/me":{},"/torbox/torrents":[],"/watchlist":[],"/tracking/state":[],"/jellyfin":{connected:false},
    "/meta/series/tt0903747":{id:"tt0903747",type:"series",name:"Test series",year:'2026',runtime:'50 min',poster:'https://example.invalid/poster.jpg',videos},
    "/tracking/title/tt0903747":{item:null,episodes:[{season:1,episode:1,played:true}]},
    "/meta/episode":{overview:"Full episode synopsis",imdbRating:8.5},"/meta/title-logo":{},"/meta/season-posters":{posters:{}},"/meta/trailer":{},
  }[path]}));
  api.put.mockResolvedValue({data:{}});
  api.post.mockResolvedValue({data:{jellyfin_updated:0}});
  checkCached.mockResolvedValue(new Set());
  fetchStreams.mockResolvedValue([]);fetchIndexers.mockResolvedValue({list:[],errors:[]});
});
afterEach(async()=>{if(view)await view.close();view=null;});
async function open(query=""){
  view=await mount(<AppProvider><MemoryRouter initialEntries={[`/title/series/tt0903747${query}`]}><Routes><Route path="/title/:type/:id" element={<TitlePage/>}/></Routes></MemoryRouter></AppProvider>);
  await until(()=>view.container.querySelector('[data-testid="tracking-panel"]'));
}

test("episode popup selects the actual episode and watched controls retain its coordinates",async()=>{
  await open();
  await act(async()=>view.container.querySelector('[data-testid="episode-chip-1-2"]').click());
  await until(()=>document.querySelector('[role="dialog"]'));
  expect(document.querySelector('[role="dialog"]').textContent).toContain('S1 E2');
  await act(async()=>document.querySelector('button[aria-label="Close dialog"]').click());
  expect(view.container.querySelector('[data-testid="next-up-button"]')).toBeNull();
  await act(async()=>view.container.querySelector('[data-testid="mark-watched-button"]').click());
  await act(async()=>document.querySelector('[data-testid="save-watched-date"]').click());
  expect(api.post.mock.calls[0][1].episodes).toEqual([[1,2]]);
});

test("episode deep links retain selection while a normal Add to Library starts with season packs",async()=>{
  await open("?season=1&episode=2");
  expect(view.container.querySelector('[data-testid="episode-chip-1-2"]').getAttribute("aria-pressed")).toBe("true");
  await act(async()=>document.querySelector('[data-testid="open-library-picker"]').click());await until(()=>fetchIndexers.mock.calls.length>0);
  expect(fetchIndexers.mock.calls[0].slice(3,5)).toEqual([1,0]);
  expect(document.querySelector('[data-testid="source-mode-pack"]').dataset.active).toBe('true');
  const header=document.querySelector('[data-testid="source-title-header"]');
  expect(header.querySelector('img').getAttribute('src')).toBe('https://example.invalid/poster.jpg');
  expect(header.textContent).toContain('Test series'); expect(header.textContent).toContain('2026 · Series · 50 min');
  expect(header.textContent).toContain('S1 · Season pack'); expect(header.textContent).not.toContain('Add to Library');
  expect(document.querySelector('.source-summary-bar')).toBeNull();
  expect(header.querySelector('[data-testid="streams-refresh-button"]')).not.toBeNull();
});

test("explicit pack and episode modes preserve the existing season-pack indexer contract",async()=>{
  await open();
  await act(async()=>document.querySelector('[data-testid="open-library-picker"]').click());
  await until(()=>fetchIndexers.mock.calls.length>0);
  expect(fetchIndexers.mock.calls[0].slice(3,5)).toEqual([1,0]);
  await act(async()=>document.querySelector('[data-testid="source-mode-episode"]').click());
  await until(()=>fetchIndexers.mock.calls.some(call=>call[4]===1));
  expect(fetchStreams.mock.calls.some(call=>call[1]==="tt0903747:1:1")).toBe(true);
});

 test('poster Add to Library intent opens the real source picker without removing its controls',async()=>{
  await open('?add=1');
  await until(()=>document.querySelector('[role="dialog"]'));
  expect(document.querySelector('[data-testid="source-mode-pack"]')).not.toBeNull();
  expect(document.querySelector('[data-testid="source-mode-episode"]')).not.toBeNull();
  expect(document.querySelector('[data-testid="stream-filter-res-4k"]')).not.toBeNull();
  expect(document.querySelector('[data-testid="stream-filter-cached-only"]')).toBeNull();
  await act(async()=>document.querySelector('[aria-label="Filters and sorting"]').click());
  expect(document.querySelector('[data-testid="stream-filter-max-size"]')).not.toBeNull();
});

test('episode popup Add to Library opens sources for exactly that episode',async()=>{
  await open();
  await act(async()=>view.container.querySelector('[data-testid="episode-chip-1-2"]').click());await until(()=>document.querySelector('[data-testid="episode-add-to-library"]'));
  await act(async()=>document.querySelector('[data-testid="episode-add-to-library"]').click());await until(()=>fetchStreams.mock.calls.length>0);
  expect(fetchStreams.mock.calls[0][1]).toBe('tt0903747:1:2');expect(document.querySelector('[data-testid="source-episode-select"]').value).toBe('2');
  expect(document.querySelector('[data-testid="source-title-header"]').textContent).toContain('S1 · E2');
});

test('uncached releases stay hidden and cached releases remain usable',async()=>{
  const hash='a'.repeat(40);fetchStreams.mockResolvedValue([{hash,name:'Confirmed source fixture',resolution:'1080p',seeders:5,size:100}]);checkCached.mockResolvedValue(new Set());
  await open('?add=1&season=1&episode=2');await until(()=>document.querySelector('[role="dialog"]')?.textContent.includes('No cached releases'));
  expect(document.body.textContent).not.toContain('Confirmed source fixture');checkCached.mockResolvedValue(new Set([hash]));
  await act(async()=>document.querySelector('[data-testid="streams-refresh-button"]').click());await until(()=>document.body.textContent.includes('Confirmed source fixture'));
});

test('selected series metadata year and status take priority over TMDb logo enrichment',async()=>{
 const previous=api.get.getMockImplementation();api.get.mockImplementation(async(path,...args)=>{const response=await previous(path,...args);if(path==='/meta/series/tt0903747')return {data:{...response.data,year:'2008',status:'Ended',metadata_source:'cinemeta'}};if(path==='/meta/title-logo')return {data:{series_info:{first_air_date:'2010-01-01',status:'Returning Series'}}};return response;});
 await open();await until(()=>view.container.querySelector('.title-hero')?.textContent.includes('Ended'));const hero=view.container.querySelector('.title-hero').textContent;expect(hero).toContain('2008');expect(hero).not.toContain('2010');expect(hero).not.toContain('Returning Series');
});

test.each([120,600])('synopsis preserves access to all %i characters instead of truncating short copy without a control',async length=>{
 const original=api.get.getMockImplementation(),description='Readable synopsis. '.repeat(40).slice(0,length);
 api.get.mockImplementation(async(...args)=>{const response=await original(...args);return args[0]==='/meta/series/tt0903747'?{data:{...response.data,description}}:response;});
 await open();const copy=view.container.querySelector('#title-synopsis-copy');expect(copy.textContent).toBe(description);
 if(length<=220)expect(copy.classList.contains('synopsis-preview')).toBe(false);
 else {const toggle=view.container.querySelector('[aria-controls="title-synopsis-copy"]');expect(toggle.getAttribute('aria-expanded')).toBe('false');await act(async()=>toggle.click());expect(copy.classList.contains('synopsis-preview')).toBe(false);expect(toggle.getAttribute('aria-expanded')).toBe('true');}
});
