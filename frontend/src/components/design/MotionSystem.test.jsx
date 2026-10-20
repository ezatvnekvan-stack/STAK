import React, {useState} from 'react';
import {act,mount} from '../../testUtils';
import {AnimatedSelection} from './MotionSystem';
let mockReduced=true;
jest.mock('../../lib/appearance',()=>({useAppearance:()=>({reducedMotion:mockReduced})}));
function Harness(){const [value,setValue]=useState('one');return <AnimatedSelection value={value} aria-label="Test selection">{['one','two'].map(id=><button key={id} data-value={id} aria-pressed={id===value} onClick={()=>setValue(id)}>{id}</button>)}<button data-value="disabled" disabled>Unavailable</button></AnimatedSelection>;}
test('animated selection preserves real button actions, disabled state and accessible selection',async()=>{
 const view=await mount(<Harness/>);
 try{
  expect(view.container.querySelectorAll('.selection-fill')).toHaveLength(1);
  await act(async()=>view.container.querySelector('[data-value="two"]').click());
  expect(view.container.querySelector('[data-value="two"]').getAttribute('aria-pressed')).toBe('true');
  expect(view.container.querySelector('[data-value="two"] .selection-fill')).not.toBeNull();
  expect(view.container.querySelector('[data-value="disabled"]').disabled).toBe(true);
  expect(view.container.textContent).toBe('onetwoUnavailable');
 }finally{await view.close();}
});
