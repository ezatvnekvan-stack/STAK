import React from 'react';
import {MemoryRouter,useLocation} from 'react-router-dom';
import {act,mount,until} from '../testUtils';
import MediaHero from './MediaHero';
let mockSelected=0;
const mockListeners={};
const mockCarousel={selectedScrollSnap:()=>mockSelected,on:jest.fn((name,handler)=>{mockListeners[name]=handler;}),off:jest.fn(),scrollTo:jest.fn(index=>{mockSelected=index;mockListeners.select?.();}),scrollNext:jest.fn()};
const mockViewport=jest.fn();
jest.mock('embla-carousel-react',()=>()=>[mockViewport,mockCarousel]);
jest.mock('../lib/useMetadataSources',()=>({useMetadataSources:()=>({settings:{ratings:false}})}));
jest.mock('../lib/metadata/runtime',()=>({useMetadataRevision:()=>0}));
jest.mock('../lib/metadataQuery',()=>({metadataOptions:()=>({queryKey:['warm-test'],queryFn:()=>({})}),useMetadataQuery:()=>({data:{}})}));
jest.mock('../lib/appearance',()=>({useAppearance:()=>({reducedMotion:true})}));
jest.mock('../lib/api',()=>({api:{get:async(path,config)=>({data:{items:[{id:'tt1',imdb_id:'tt1',type:'movie',name:'First title',year:2024},{id:'tt2',imdb_id:'tt2',type:'series',name:'Second title',year:2025}].filter(item=>item.type===config.params.type)}})}}));
jest.mock('./AppShell',()=>({REFRESH_EVENT:'dmm-refresh'}));
jest.mock('./RatingBadge',()=>()=>null);
function Location(){return <p data-testid="location">{useLocation().pathname}</p>;}
beforeEach(()=>{
 mockCarousel.on.mockImplementation((name,handler)=>{mockListeners[name]=handler;});
 mockCarousel.scrollTo.mockImplementation(index=>{mockSelected=index;mockListeners.select?.();});
});
test('Embla selection changes the real title action and reduced motion jumps without autoplay',async()=>{
 mockSelected=0;const view=await mount(<MemoryRouter><MediaHero/><Location/></MemoryRouter>);
 try{
  await until(()=>view.container.querySelector('[aria-label="Open First title"]'));
  expect(view.container.querySelectorAll('.hero-open')).toHaveLength(1);
  const next=[...view.container.querySelectorAll('button')].find(button=>button.getAttribute('aria-label')==='Show Second title');
  await act(async()=>next.click());
  expect(mockCarousel.scrollTo).toHaveBeenCalledWith(1,true);
  expect(view.container.querySelector('[aria-label="Open Second title"]')).not.toBeNull();
  await act(async()=>view.container.querySelector('.hero-open').click());
  expect(view.container.querySelector('[data-testid="location"]').textContent).toBe('/title/series/tt2');
  expect(mockCarousel.scrollNext).not.toHaveBeenCalled();
 }finally{await view.close();}
});
