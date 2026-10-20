import React from 'react';
import { MemoryRouter,useLocation } from 'react-router-dom';
import { act,mount } from '../testUtils';
import SearchPage from './SearchPage';
import { api } from '../lib/api';
jest.mock('../context/AppContext',()=>{const session={key:'',scope:'search-test',controller:new AbortController()};return {useApp:()=>({session,watchIds:new Set(),tracking:new Map(),toggleWatch:jest.fn()})};});
jest.mock('../lib/api',()=>({api:{get:jest.fn()},errMsg:error=>error.message,TMDB_STORAGE:'tmdb_api_key'}));
function Location(){const location=useLocation();return <p data-testid="location">{location.search}</p>;}
async function type(input,value){await act(async()=>{Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set.call(input,value);input.dispatchEvent(new Event('input',{bubbles:true}));});}
async function debounce(){await act(async()=>{await new Promise(resolve=>setTimeout(resolve,360));});}
afterEach(()=>jest.clearAllMocks());
test('typing spaces survives URL debounce and the final request keeps both title words',async()=>{
  api.get.mockResolvedValue({data:{items:[]}});const view=await mount(<MemoryRouter><SearchPage/><Location/></MemoryRouter>);
  try{const input=view.container.querySelector('[data-testid="media-search-input"]');await type(input,'mr');await debounce();await type(input,'mr ');await debounce();expect(input.value).toBe('mr ');await type(input,'mr robot');await debounce();expect(input.value).toBe('mr robot');expect(api.get.mock.calls.some(([path,config])=>path==='/metadata/search' && config?.params?.q==='mr robot')).toBe(true);}finally{await view.close();}
});
test('composition does not send half-composed text to search',async()=>{
  api.get.mockResolvedValue({data:{items:[]}});const view=await mount(<MemoryRouter><SearchPage/></MemoryRouter>);
  try{const input=view.container.querySelector('input');await act(async()=>input.dispatchEvent(new CompositionEvent('compositionstart',{bubbles:true})));await type(input,'mr robot');await debounce();expect(api.get).not.toHaveBeenCalledWith('/metadata/search',expect.anything());await act(async()=>input.dispatchEvent(new CompositionEvent('compositionend',{bubbles:true})));await debounce();expect(api.get.mock.calls.some(([path,config])=>path==='/metadata/search' && config.params.q==='mr robot')).toBe(true);}finally{await view.close();}
});
test('unified provider search sends region and filters to supported TMDb search rather than ignoring them',async()=>{
  api.get.mockResolvedValue({data:{items:[],page:1,total_pages:1}});
  const view=await mount(<MemoryRouter initialEntries={['/discover?q=mr%20robot']}><SearchPage embedded mediaType="series" discoveryFilters={{provider:'8|1796',region:'HU',genre:'18',imdb_rating:'8'}} onFiltersRequest={()=>{}}/></MemoryRouter>);
  try{await debounce();expect(api.get.mock.calls.some(([path,config])=>path==='/metadata/search' && config.params.provider==='8|1796' && config.params.region==='HU' && config.params.imdb_rating==='8')).toBe(true);expect(document.querySelector('[data-testid="discover-filters"]')).toBeNull();}finally{await view.close();}
});

test('queries both media types even from legacy movie search and Home hides filters',async()=>{
  api.get.mockResolvedValue({data:{items:[]}});
  const view=await mount(<MemoryRouter initialEntries={['/?q=robot&type=movie']}><SearchPage embedded hideFilters searchOnly/></MemoryRouter>);
  try{await debounce();expect(api.get.mock.calls.filter(([path])=>path==='/metadata/search')).toHaveLength(1);expect(api.get.mock.calls.find(([path])=>path==='/metadata/search')[1].params.type).toBeUndefined();expect(api.get.mock.calls.some(([path])=>path==='/discover' || path==='/discover/tmdb')).toBe(false);expect(view.container.querySelector('[data-testid="open-filters-button"]')).toBeNull();}finally{await view.close();}
});
test('unified search forwards independent provider cursors without merging sources in the screen',async()=>{
 api.get.mockImplementation(async(path,config)=>({data:{items:[{id:'tt123',name:'Result',type:'movie'}],source:'tmdb',next_cursor:config.params.pages?null:{movie:null,series:2}}}));
 const view=await mount(<MemoryRouter initialEntries={['/discover?q=robot']}><SearchPage embedded mediaType="movie" discoveryFilters={{provider:'8',region:'US'}} onFiltersRequest={()=>{}}/></MemoryRouter>);
 try{await debounce();expect(api.get.mock.calls.filter(([p])=>p==='/metadata/search')).toHaveLength(1);await act(async()=>view.container.querySelector('[data-testid="load-more-button"]').click());await debounce();expect(api.get.mock.calls.some(([p,c])=>p==='/metadata/search' && c.params.pages===JSON.stringify({movie:null,series:2}))).toBe(true);expect(api.get.mock.calls.some(([p])=>p==='/discover' || p==='/discover/tmdb')).toBe(false);}finally{await view.close();}
});
test('missing key fallback notice is visible beside results',async()=>{
 api.get.mockResolvedValue({data:{items:[],source:'cinemeta',notices:['TMDb key missing. Search is using Cinemeta. Configure TMDb in Metadata sources.']}});
 const view=await mount(<MemoryRouter initialEntries={['/?q=robot']}><SearchPage/></MemoryRouter>);try{await debounce();expect(view.container.textContent).toContain('TMDb key missing');}finally{await view.close();}
});
