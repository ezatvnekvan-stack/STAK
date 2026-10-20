import React from 'react';
import {act,mount} from '../testUtils';
import StreamFilters from './StreamFilters';
let view;
afterEach(async()=>{if(view)await view.close();view=null;});
test('advanced source controls are reachable in an accessible sheet and reset clears the badge filters',async()=>{
 const setF=jest.fn(),f={res:'1080p',text:'name',sort:'best',hdr:true,preferHun:true,maxSize:'10'};
 view=await mount(<StreamFilters f={f} setF={setF} counts={{'1080p':2}} total={2}/>);
 expect(view.container.textContent).toContain('2 cached releases');expect(document.querySelector('[data-testid="stream-filter-max-size"]')).toBeNull();
 await act(async()=>view.container.querySelector('[aria-label="Filters and sorting"]').click());
 expect(document.querySelector('[data-testid="source-filter-sheet"]')).not.toBeNull();expect(document.querySelector('[data-testid="stream-filter-hdr"]').getAttribute('aria-pressed')).toBe('true');
 await act(async()=>[...document.querySelectorAll('button')].find(b=>b.textContent==='Reset filters').click());
 expect(setF).toHaveBeenLastCalledWith(expect.objectContaining({res:'All',text:'',hdr:false,preferHun:false,maxSize:''}));
});

test('only 4K and 1080p resolution badges and the four requested quality filters are visible',async()=>{
 const setF=jest.fn();view=await mount(<StreamFilters f={{res:'4K'}} setF={setF} counts={{}} total={0}/>);
 expect(view.container.querySelector('input')).toBeNull();expect(view.container.querySelectorAll('[data-testid^="stream-filter-res-"]')).toHaveLength(2);
 for(const tag of ['hdr','atmos','webdl','remux'])expect(view.container.querySelector(`[data-testid="stream-filter-${tag}"]`)).not.toBeNull();
 await act(async()=>view.container.querySelector('[data-testid="stream-filter-res-4k"]').click());expect(setF).toHaveBeenCalledWith(expect.objectContaining({res:'All'}));
});
