import React from 'react';
import {act,mount} from '../testUtils';
import DeferredSection from './DeferredSection';
test('offscreen shelves do not mount their effects; approaching shelves mount exactly once',async()=>{
 const original=global.IntersectionObserver;let notify,disconnect=jest.fn(),mounted=jest.fn();
 global.IntersectionObserver=jest.fn(callback=>{notify=callback;return {observe:jest.fn(),disconnect};});
 function Shelf(){React.useEffect(()=>{mounted();},[]);return <p>Real shelf</p>;}
 const view=await mount(<DeferredSection><Shelf/></DeferredSection>);
 try{
  expect(mounted).not.toHaveBeenCalled();expect(view.container.textContent).not.toContain('Real shelf');
  await act(async()=>notify([{isIntersecting:false}]));expect(mounted).not.toHaveBeenCalled();
  await act(async()=>notify([{isIntersecting:true}]));expect(mounted).toHaveBeenCalledTimes(1);expect(disconnect).toHaveBeenCalled();
 }finally{await view.close();global.IntersectionObserver=original;}
});
