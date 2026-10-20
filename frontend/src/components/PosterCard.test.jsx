import React from 'react';
import {MemoryRouter} from 'react-router-dom';
import {mount} from '../testUtils';
import PosterCard from './PosterCard';
let mockState={played:true};
jest.mock('../context/AppContext',()=>({useApp:()=>({watchIds:new Set(['tt123']),tracking:new Map([['tt123',mockState]]),catalog:[]})}));
jest.mock('../lib/useMetadataSources',()=>({useMetadataSources:()=>({settings:{ratings:false,imdbOnLists:false}})}));
jest.mock('../lib/metadataQuery',()=>({useMetadataQuery:()=>({})}));
jest.mock('./MediaQuickActions',()=>({children})=>children);
test('poster cards announce watched and watchlist status without repeating artwork titles',async()=>{
 const view=await mount(<MemoryRouter><PosterCard item={{id:'tt123',name:'Example',type:'movie'}} index={0}/></MemoryRouter>);
 try{expect(view.container.querySelector('img').alt).toBe('');expect(view.container.querySelector('[aria-label="Watched"]')).not.toBeNull();expect(view.container.querySelector('[aria-label="In watchlist"]')).not.toBeNull();}finally{await view.close();}
});
test('partial progress is accessible as a bounded value',async()=>{
 mockState={played:false,progress:140};
 const view=await mount(<MemoryRouter><PosterCard item={{id:'tt123',name:'Example',type:'movie'}} index={0}/></MemoryRouter>);
 try{expect(view.container.querySelector('[role="progressbar"]').getAttribute('aria-valuenow')).toBe('100');expect(view.container.querySelector('[aria-label="Watched"]')).toBeNull();}finally{await view.close();mockState={played:true};}
});
