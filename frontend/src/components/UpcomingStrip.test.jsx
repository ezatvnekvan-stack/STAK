import React,{useState} from 'react';
import {MemoryRouter} from 'react-router-dom';
import {act,mount,until} from '../testUtils';
import UpcomingStrip from './UpcomingStrip';
import {api} from '../lib/api';
jest.mock('../lib/api',()=>({api:{get:jest.fn()}}));
jest.mock('../lib/i18n',()=>({useT:()=>value=>value}));
test('New releases includes movie and episode links with their correct identities',async()=>{
 api.get.mockResolvedValue({data:{items:[{imdb_id:'tt1',type:'movie',name:'Movie',date:'2026-10-20'},{imdb_id:'tt2',type:'series',name:'Series',date:'2026-10-20',season:2,episode:3}]}});
 const view=await mount(<MemoryRouter><UpcomingStrip/></MemoryRouter>);
 try{await until(()=>view.container.querySelectorAll('a').length===2);expect(view.container.textContent).toContain('New releases');expect(view.container.querySelectorAll('a')[0].getAttribute('href')).toBe('/title/movie/tt1');expect(view.container.querySelectorAll('a')[1].getAttribute('href')).toBe('/title/series/tt2?season=2&episode=3');expect(view.container.querySelectorAll('a')[0].textContent).not.toContain('Sundefined');}finally{await view.close();}
});

let view;
afterEach(async()=>{if(view)await view.close();view=null;jest.clearAllMocks();});
test('existing Home refresh reloads upcoming entries and cards select the actual episode', async () => {
  let request = 0;
  api.get.mockImplementation(() => Promise.resolve({ data: { items: [{ imdb_id: 'tt0903747', season: 2, episode: ++request, date: '2026-10-15', name: `Episode ${request}` }] } }));
  function HomeRefresh() {
    const [version, refresh] = useState(0);
    return <><button onClick={() => refresh(value => value + 1)}>Refresh Home</button><UpcomingStrip refreshVersion={version} /></>;
  }
  view = await mount(<MemoryRouter><HomeRefresh /></MemoryRouter>);
  await until(() => view.container.querySelector('a'));
  expect(view.container.querySelector('a').getAttribute('href')).toBe('/title/series/tt0903747?season=2&episode=1');
  await act(async () => view.container.querySelector('button').click());
  await until(() => view.container.textContent.includes('Episode 2'));
  expect(view.container.querySelector('a').getAttribute('href')).toBe('/title/series/tt0903747?season=2&episode=2');
  expect(view.container.textContent).not.toContain('Episode 1');
});
