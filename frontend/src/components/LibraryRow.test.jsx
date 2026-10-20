import React from 'react';
import {act,mount} from '../testUtils';
import LibraryRow from './LibraryRow';
const torrent={id:1,name:'Crystal.Lake.S01.2160p.WEB-DL',hash:'a'.repeat(40),size:1e9,download_finished:true,files:[{id:1}]};
let view;
afterEach(async()=>{if(view)await view.close();view=null;jest.useRealTimers();});
test('normal rows open files with cleaned name and no permanent checkbox',async()=>{
 const open=jest.fn(),toggle=jest.fn();view=await mount(<LibraryRow t={torrent} index={0} onOpen={open} onToggle={toggle}/>);
 expect(view.container.textContent).toContain('Crystal Lake');expect(view.container.textContent).toContain('Season 1');expect(view.container.textContent).not.toContain('2160p');
 expect(view.container.querySelector('[role="checkbox"]')).toBeNull();
 await act(async()=>view.container.querySelector('button').click());expect(open).toHaveBeenCalledTimes(1);expect(toggle).not.toHaveBeenCalled();
});
test('selection mode exposes labelled checkbox and row toggles instead of opening files',async()=>{
 const open=jest.fn(),toggle=jest.fn();view=await mount(<LibraryRow t={torrent} index={0} selecting selected onOpen={open} onToggle={toggle}/>);
 expect(view.container.querySelector('[role="checkbox"]').getAttribute('aria-label')).toContain('Crystal Lake');
 await act(async()=>view.container.querySelector('[data-testid="library-open-0"]').click());expect(toggle).toHaveBeenCalledTimes(1);expect(open).not.toHaveBeenCalled();
});
test('unknown progress stays indeterminate and does not render an invented zero',async()=>{
 view=await mount(<LibraryRow t={{...torrent,download_finished:false,progress:null}} index={0} onToggle={()=>{}} onOpen={()=>{}}/>);
 expect(view.container.querySelector('[role="progressbar"]').hasAttribute('aria-valuenow')).toBe(false);
 expect(view.container.textContent).toContain('Waiting');
});
test('long press is cancelled during scrolling gestures',async()=>{
 jest.useFakeTimers();const toggle=jest.fn();view=await mount(<LibraryRow t={torrent} index={0} onToggle={toggle} onOpen={()=>{}}/>);
 const button=view.container.querySelector('button');await act(async()=>{button.dispatchEvent(new MouseEvent('pointerdown',{button:0,clientX:5,clientY:5,bubbles:true}));button.dispatchEvent(new MouseEvent('pointermove',{clientX:5,clientY:40,bubbles:true}));jest.advanceTimersByTime(500);});expect(toggle).not.toHaveBeenCalled();
});
