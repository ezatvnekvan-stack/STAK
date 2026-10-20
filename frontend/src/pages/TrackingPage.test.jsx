import React from 'react';
import {MemoryRouter,Routes,Route} from 'react-router-dom';
import {act,mount} from '../testUtils';
import TrackingPage,{TrackingRedirect} from './TrackingPage';
jest.mock('../context/AppContext',()=>({useApp:()=>({session:{scope:'tracking-route-test'}})}));
jest.mock('../lib/useExperience',()=>{const React=require('react');return {useExperience:()=>React.useState({view:'activity'})};});
jest.mock('../lib/useTrackingScroll',()=>({useTrackingScroll:()=>{}}));
jest.mock('./HistoryPage',()=>()=> <p>Activity content</p>);
jest.mock('./CalendarPage',()=>()=> <p>Calendar content</p>);
test('old Calendar links land on its panel, and Activity/Calendar are real keyboard-selectable views',async()=>{
 const view=await mount(<MemoryRouter initialEntries={['/calendar']}><Routes><Route path="/calendar" element={<TrackingRedirect view="calendar"/>}/><Route path="/tracking" element={<TrackingPage/>}/></Routes></MemoryRouter>);
 try{expect(view.container.textContent).toContain('Calendar content');expect(view.container.textContent).not.toContain('Activity content');
 await act(async()=>view.container.querySelector('#tracking-tab-activity').click());
 expect(view.container.textContent).toContain('Activity content');expect(view.container.querySelector('#tracking-tab-activity').getAttribute('aria-selected')).toBe('true');
 await act(async()=>view.container.querySelector('#tracking-tab-activity').dispatchEvent(new KeyboardEvent('keydown',{key:'ArrowRight',bubbles:true})));
 expect(view.container.textContent).toContain('Calendar content');
 }finally{await view.close();}
});
