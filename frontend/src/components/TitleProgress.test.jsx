import React from 'react';
import { act,mount,until } from '../testUtils';
import TitleProgress, { JellyfinAvailability } from './TitleProgress';
import { useAccountQuery } from '../lib/useAccountQuery';
import { api, openExternal } from '../lib/api';
import { mergedWatched } from '../lib/titleAvailability';
jest.mock('../lib/api',()=>({api:{get:jest.fn(),post:jest.fn()},errMsg:error=>error.message,openExternal:jest.fn()}));
jest.mock('../lib/useAccountQuery',()=>({useAccountQuery:jest.fn()}));
jest.mock('../context/AppContext',()=>{const session={scope:'test',key:'fixture',controller:new AbortController()};return {useApp:()=>({session,refreshTracking:()=>Promise.resolve()})};});
let view;
afterEach(async()=>{if(view)await view.close();view=null;});
test.each([
 [{data:{state:'available',web_url:'https://jellyfin.example/web/'}},'available','Available'],
 [{data:{state:'unavailable'}},'unavailable','Not in library'],
 [{data:{state:'disconnected'}},'disconnected','Disconnected'],
 [{error:new Error('Access denied')},'unknown','Availability unknown'],
 [{data:{state:'available'},isFetching:true},'unknown','Checking library'],
])('Jellyfin indicator distinguishes confirmed, disconnected and unknown states',async(query,state,label)=>{
 view=await mount(<JellyfinAvailability query={{refetch:jest.fn(),...query}}/>);
 expect(view.container.firstChild.dataset.state).toBe(state);
 expect(view.container.textContent).toContain(label);
 if(state==='unknown')expect(view.container.textContent).not.toContain('Not in library');
 expect(Boolean(view.container.querySelector('[aria-label="Open in Jellyfin"]'))).toBe(state==='available');
});
test('the compact Jellyfin icon preserves the original external URL action', async () => {
  const url='https://jellyfin.example/web/#!/details?id=123';
  view=await mount(<JellyfinAvailability query={{data:{state:'available',web_url:url}}}/>);
  const button=view.container.querySelector('[aria-label="Open in Jellyfin"]');
  expect(button.textContent).toBe(''); expect(button.querySelector('svg')).not.toBeNull();
  await act(async()=>button.click()); expect(openExternal).toHaveBeenCalledWith(url);
});
test('fetched episode completion preserves newer manual changes without mutating saved state',()=>{
 const local={item:{updated_at:'2026-10-15T12:00:00Z'},episodes:[{season:1,episode:1,played:false}]};
 const remote=[{season:1,episode:1,played:true,last_played:'2026-10-14T12:00:00Z'},{season:1,episode:2,played:true}];
 const merged=mergedWatched(local,remote);
 expect(merged['1:1'].played).toBe(false);expect(merged['1:2'].played).toBe(true);expect(local.episodes).toHaveLength(1);expect(remote[0].played).toBe(true);
});


test('started movies show real playback progress and remaining minutes without a tracking card',async()=>{
  useAccountQuery.mockImplementation(path=>({data:path.startsWith('/tracking')?{item:{played:false}}:{state:'available',playback:{played:false,progress:25,position_ticks:6000000000,runtime_ticks:24000000000}},refetch:jest.fn()}));
  view=await mount(<TitleProgress meta={{id:'tt123',type:'movie'}} ep={{s:1,e:1}}/>);
  expect(view.container.querySelector('[aria-label="Playback progress"]').getAttribute('aria-valuenow')).toBe('25');expect(view.container.textContent).toContain('30 min remaining');expect(view.container.querySelector('[data-testid="tracking-panel"]').classList.contains('n-card')).toBe(false);
});
test('marking the first season switches the season action to the next unwatched season',async()=>{
  api.post.mockClear();let episodes=[];const refetch=jest.fn();useAccountQuery.mockImplementation(path=>({data:path.startsWith('/tracking')?{item:{},episodes}:{state:'disconnected'},refetch}));
  api.post.mockImplementation(async(path,body)=>{episodes=body.episodes.map(([season,episode])=>({season,episode,played:true}));return {data:{}};});
  const meta={id:'tt123',type:'series',name:'Show',videos:[{season:1,episode:1},{season:1,episode:2},{season:2,episode:1}]};
  view=await mount(<TitleProgress meta={meta} ep={{s:1,e:1}}/>);expect(view.container.textContent).toContain('Mark S01 watched');
  await act(async()=>view.container.querySelector('[data-testid="mark-season-watched-button"]').click());
  expect(api.post).not.toHaveBeenCalled();
  await act(async()=>document.querySelector('[data-testid="confirm-season-watched"]').click());
  await act(async()=>document.querySelector('[data-testid="save-watched-date"]').click());
  await until(()=>view.container.textContent.includes('Mark S02 watched'));expect(api.post.mock.calls.at(-1)[1].episodes).toEqual([[1,1],[1,2]]);
});

test('episode completion counts update after marking and undo without counting partial playback or Specials',async()=>{
 let episodes=[{season:1,episode:1,played:true},{season:1,episode:2,played:false,progress:80}];
 useAccountQuery.mockImplementation(path=>({data:path.startsWith('/tracking')?{item:{},episodes}:{state:'disconnected'},refetch:jest.fn()}));
 api.post.mockImplementation(async(path,body)=>{for(const [season,episode] of body.episodes){episodes=episodes.filter(item=>item.season!==season || item.episode!==episode);episodes.push({season,episode,played:body.played});}return {data:{}};});
 view=await mount(<TitleProgress meta={{id:'tt123',type:'series',videos:[{season:0,episode:1},{season:1,episode:1},{season:1,episode:2},{season:1,episode:2}]}} ep={{s:1,e:2}}/>);
 expect(view.container.querySelector('[aria-label="Series completion"]').getAttribute('aria-valuenow')).toBe('50');expect(view.container.textContent).toContain('1 remaining');
 await act(async()=>view.container.querySelector('[data-testid="mark-watched-button"]').click());
 await act(async()=>document.querySelector('[data-testid="save-watched-date"]').click());await until(()=>view.container.querySelector('[data-testid="mark-unwatched-button"]'));
 expect(view.container.querySelector('[aria-label="Series completion"]').getAttribute('aria-valuenow')).toBe('100');
 await act(async()=>view.container.querySelector('[data-testid="mark-unwatched-button"]').click());await until(()=>view.container.querySelector('[data-testid="mark-watched-button"]'));
 expect(view.container.querySelector('[aria-label="Series completion"]').getAttribute('aria-valuenow')).toBe('50');
});
test('compact episode controls never show another episode resume position',async()=>{
 useAccountQuery.mockImplementation(path=>({data:path.startsWith('/tracking')?{item:{},episodes:[]}:{state:'available',episode_available:true,resume_playback:{season:9,episode:8,progress:90,position_ticks:9000000},web_url:'https://example.invalid/episode'},refetch:jest.fn()}));
 view=await mount(<TitleProgress meta={{id:'tt123',type:'series',videos:[{season:1,episode:2}]}} ep={{s:1,e:2}} episodeOnly/>);
 expect(view.container.querySelector('[aria-label="Series completion"]')).toBeNull();expect(view.container.querySelector('[data-testid="playback-progress"]')).toBeNull();expect(view.container.querySelector('[data-testid="episode-mark-watched-button"]')).not.toBeNull();
});

test('a movie watched in Jellyfin is shown watched even without runtime or a local played flag',async()=>{
 useAccountQuery.mockImplementation(path=>({data:path.startsWith('/tracking')?{item:{played:false},episodes:[]}:{state:'available',title_state:{played:true,last_played:null},playback:{played:true,progress:null}},refetch:jest.fn()}));
 view=await mount(<TitleProgress meta={{id:'tt123',type:'movie'}} ep={{s:1,e:0}}/>);
 expect(view.container.querySelector('[data-testid="mark-unwatched-button"]')).not.toBeNull();expect(view.container.textContent).not.toContain('Watched in Jellyfin');
});
test('series-level Jellyfin watched state completes the app even when episode lookup fails',async()=>{
 useAccountQuery.mockImplementation(path=>({data:path.startsWith('/tracking')?{item:null,episodes:[]}:{state:'available',title_state:{played:true},episodes:[],episodes_complete:false,episodes_error:'Unavailable'},refetch:jest.fn()}));
 view=await mount(<TitleProgress meta={{id:'tt123',type:'series',videos:[{season:1,episode:1}]}} ep={{s:1,e:1}}/>);
 expect(view.container.textContent).not.toContain('Series marked watched in Jellyfin');expect(view.container.textContent).not.toContain('Checking watched state');expect(view.container.querySelector('[aria-label="Series completion"]').getAttribute('aria-valuenow')).toBe('100');expect(view.container.querySelector('[data-testid="mark-watched-button"]')).toBeNull();expect(view.container.querySelector('[data-testid="mark-season-watched-button"]')).toBeNull();expect(view.container.querySelector('[data-testid="mark-unwatched-button"]')).not.toBeNull();
});

test('a confirmed title outside Jellyfin immediately uses local counts and always-visible details',async()=>{
 useAccountQuery.mockImplementation(path=>({data:path.startsWith('/tracking')?{item:null,episodes:[]}:{state:'unavailable',in_library:false},refetch:jest.fn()}));
 view=await mount(<TitleProgress meta={{id:'tt123',type:'series',videos:[{season:1,episode:1},{season:1,episode:2}]}} ep={{s:1,e:1}}/>);
 expect(view.container.textContent).not.toContain('Checking watched state');expect(view.container.textContent).toContain('0 of 2 watched');expect(view.container.querySelector('[data-testid="tracking-details"]').textContent).toBe('0% complete · 2 remaining');expect(view.container.querySelector('summary')).toBeNull();
});
test('a watched parent with an empty successful episode response has no duplicate watched actions',async()=>{
 useAccountQuery.mockImplementation(path=>({data:path.startsWith('/tracking')?{item:null,episodes:[]}:{state:'available',title_state:{played:true},episodes:[],episodes_complete:true},refetch:jest.fn()}));
 view=await mount(<TitleProgress meta={{id:'tt123',type:'series',videos:[{season:1,episode:1},{season:1,episode:2}]}} ep={{s:1,e:1}}/>);
 expect(view.container.textContent).toContain('2 of 2 watched');expect(view.container.querySelector('[data-testid="mark-watched-button"]')).toBeNull();expect(view.container.querySelector('[data-testid="mark-season-watched-button"]')).toBeNull();expect(view.container.querySelector('[data-testid="tracking-details"]').textContent).toBe('100% complete · 0 remaining');
});

test('a stale legacy movie flag cannot offer Mark watched again for a Jellyfin-watched movie',async()=>{
 useAccountQuery.mockImplementation(path=>({data:path.startsWith('/tracking')?{item:{played:false,updated_at:'2026-10-19'},episodes:[]}:{state:'available',title_state:{played:true,last_played:null}},refetch:jest.fn()}));
 view=await mount(<TitleProgress meta={{id:'tt123',type:'movie'}} ep={{s:1,e:0}}/>);
 expect(view.container.querySelector('[data-testid="mark-watched-button"]')).toBeNull();expect(view.container.querySelector('[data-testid="mark-unwatched-button"]')).not.toBeNull();
});
