import React from 'react';
import { MemoryRouter, Link, useLocation } from 'react-router-dom';
import { act, deferred, mount, until } from '../testUtils';
import MediaQuickActions from './MediaQuickActions';
import {toast} from 'sonner';
jest.mock('sonner',()=>({toast:{success:jest.fn(),error:jest.fn()}}));
const mockToggle=jest.fn();
jest.mock('../context/AppContext',()=>({useApp:()=>({watchIds:new Set(),toggleWatch:mockToggle})}));
jest.mock('../lib/api',()=>({api:{get:jest.fn()},errMsg:e=>e.message}));
function Location(){const loc=useLocation();return <p data-testid="location">{loc.pathname}{loc.search}</p>;}
const item={id:'tt123',imdb_id:'tt123',type:'series',name:'A show'};
async function open(){return mount(<MemoryRouter><MediaQuickActions item={item}><Link to="/title/series/tt123">Poster</Link></MediaQuickActions><Location/></MemoryRouter>);}
afterEach(()=>{jest.useRealTimers();jest.clearAllMocks();});
test('context action opens real Add to Library intent and suppresses poster navigation',async()=>{
  const view=await open();
  try{await act(async()=>view.container.querySelector('a').dispatchEvent(new MouseEvent('contextmenu',{bubbles:true,cancelable:true})));expect(document.querySelector('[aria-label="Actions for A show"]')).not.toBeNull();await act(async()=>[...document.querySelectorAll('button')].find(b=>b.textContent==='Add to library').click());await until(()=>view.container.textContent.includes('?add=1'));expect(view.container.querySelector('[data-testid="location"]').textContent).toBe('/title/series/tt123?add=1');}finally{await view.close();}
});
test('watchlist action calls the existing mutation with real title identity',async()=>{
  mockToggle.mockResolvedValue(true);const view=await open();
  try{await act(async()=>view.container.querySelector('a').dispatchEvent(new MouseEvent('contextmenu',{bubbles:true,cancelable:true})));await act(async()=>[...document.querySelectorAll('button')].find(b=>b.textContent==='Add to watchlist').click());expect(mockToggle).toHaveBeenCalledWith(expect.objectContaining({imdb_id:'tt123',type:'series'}));}finally{await view.close();}
});

test('touch-style long press cancels during a swipe and blocks accidental navigation',async()=>{
  jest.useFakeTimers();const view=await open();
  try{
    const link=view.container.querySelector('a');
    await act(async()=>{link.dispatchEvent(new MouseEvent('pointerdown',{button:0,clientX:10,clientY:10,bubbles:true}));link.dispatchEvent(new MouseEvent('pointermove',{clientX:40,clientY:10,bubbles:true}));jest.advanceTimersByTime(600);});
    expect(document.querySelector('[aria-label="Actions for A show"]')).toBeNull();
    await act(async()=>{link.dispatchEvent(new MouseEvent('pointerdown',{button:0,clientX:10,clientY:10,bubbles:true}));jest.advanceTimersByTime(500);});
    expect(document.querySelector('[aria-label="Actions for A show"]')).not.toBeNull();
    await act(async()=>{link.dispatchEvent(new MouseEvent('pointerup',{bubbles:true}));link.click();});
    expect(view.container.querySelector('[data-testid="location"]').textContent).toBe('/');
  }finally{await view.close();}
});

// A removed Watchlist card can unmount before its mutation promise resumes.
test('successful watchlist action still offers Undo after the originating card unmounts',async()=>{
 const pending=deferred();mockToggle.mockReturnValue(pending.promise);const view=await open();
 await act(async()=>view.container.querySelector('a').dispatchEvent(new MouseEvent('contextmenu',{bubbles:true,cancelable:true})));
 await act(async()=>[...document.querySelectorAll('button')].find(button=>button.textContent==='Add to watchlist').click());
 await view.close();await act(async()=>pending.resolve(true));
 expect(toast.success).toHaveBeenCalledWith('Added to watchlist',expect.objectContaining({action:expect.objectContaining({label:'Undo'})}));
});

test('quick-action Undo retains the saved rating field in normalized watchlist metadata',async()=>{
 mockToggle.mockResolvedValue(false);const view=await mount(<MemoryRouter><MediaQuickActions item={{...item,rating:'8.7'}}><Link to="/title/series/tt123">Poster</Link></MediaQuickActions></MemoryRouter>);
 try{await act(async()=>view.container.querySelector('a').dispatchEvent(new MouseEvent('contextmenu',{bubbles:true,cancelable:true})));await act(async()=>[...document.querySelectorAll('button')].find(button=>button.textContent==='Add to watchlist').click());expect(mockToggle).toHaveBeenCalledWith(expect.objectContaining({imdbRating:'8.7'}));}finally{await view.close();}
});

test('posters have no overflow button; keyboard actions close back onto the poster link',async()=>{
 const view=await open();
 try{
   const link=view.container.querySelector('a');
   expect(view.container.querySelector('button')).toBeNull();
   expect(link.getAttribute('aria-keyshortcuts')).toBe('Shift+F10');
   expect(link.getAttribute('aria-expanded')).toBe('false');
   await act(async()=>{link.focus();link.dispatchEvent(new KeyboardEvent('keydown',{key:'F10',shiftKey:true,bubbles:true,cancelable:true}));});
   expect(link.getAttribute('aria-expanded')).toBe('true');
   const close=document.querySelector('[aria-label="Close title actions"]');expect(close).not.toBeNull();
   await act(async()=>{close.focus();close.dispatchEvent(new KeyboardEvent('keydown',{key:'Escape',bubbles:true,cancelable:true}));});
   await until(()=>!document.querySelector('.poster-action-menu'));
   await until(()=>document.activeElement===link);
   expect(link.getAttribute('aria-expanded')).toBe('false');
 }finally{await view.close();}
});
