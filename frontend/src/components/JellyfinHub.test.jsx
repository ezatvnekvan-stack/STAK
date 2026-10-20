import React from 'react';
import { MemoryRouter } from 'react-router-dom';
import { mount } from '../testUtils';
import JellyfinHub from './JellyfinHub';

let mockHome;
jest.mock('../lib/useAccountQuery',()=>({useAccountQuery:path=>({data:path==='/jellyfin'?{connected:true}:mockHome})}));
jest.mock('../lib/i18n',()=>({useT:()=>text=>text}));
jest.mock('../lib/api',()=>({errMsg:error=>error.message}));
jest.mock('./JellyfinImage',()=>()=>null);
jest.mock('./JellyfinDetailsLink',()=>({item,children})=><div data-item-id={item.Id}>{children}</div>);
jest.mock('./ProgressIndicator',()=>({value})=><div role="progressbar" aria-valuenow={value}/>);

const episode={Id:'resume',SeriesId:'series-1',Type:'Episode',SeriesName:'A show',Name:'Episode filename.mkv',ParentIndexNumber:2,IndexNumber:3,RunTimeTicks:24e9,UserData:{PlaybackPositionTicks:6e9,PlayedPercentage:25}};
test('Continue watching keeps episode coordinates and real progress without episode titles or next-episode copy',async()=>{
  mockHome={continue_watching:[episode],next_up:[{...episode,Id:'next',SeriesId:'series-2',SeriesName:'Another show',ParentIndexNumber:1,IndexNumber:4}]};
  const view=await mount(<MemoryRouter><JellyfinHub/></MemoryRouter>);
  try{
    expect(view.container.querySelector('h2').textContent).toBe('CONTINUE WATCHING');
    expect([...view.container.querySelectorAll('.home-queue-meta')].map(el=>el.textContent)).toEqual(['S2 E3','S1 E4']);
    expect(view.container.textContent).not.toContain('Episode filename.mkv');
    expect(view.container.textContent).not.toContain('Next episode');
    expect(view.container.textContent).toContain('Another show');
    expect(view.container.querySelector('[role="progressbar"]').getAttribute('aria-valuenow')).toBe('25');
  }finally{await view.close();}
});

test('a resumed series still wins over its Next Up duplicate and movie remaining time is preserved',async()=>{
  mockHome={continue_watching:[episode,{...episode,Id:'movie',SeriesId:undefined,Type:'Movie',SeriesName:undefined,Name:'A movie'}],next_up:[{...episode,Id:'next'}]};
  const view=await mount(<MemoryRouter><JellyfinHub/></MemoryRouter>);
  try{
    expect(view.container.querySelectorAll('[data-item-id]')).toHaveLength(2);
    expect(view.container.querySelector('[data-item-id="next"]')).toBeNull();
    expect(view.container.querySelector('[data-item-id="movie"] .home-queue-meta').textContent).toBe('30 min left');
  }finally{await view.close();}
});
