import React from 'react';
import {mount} from '../../testUtils';
import AnimatedNumber from './AnimatedNumber';
jest.mock('../../lib/appearance',()=>({useAppearance:()=>({reducedMotion:true})}));
test('statistics expose exact values immediately without announcing animation frames',async()=>{
 const view=await mount(<><AnimatedNumber value={12}/><AnimatedNumber value={1.57} decimals={1} suffix="h"/><AnimatedNumber value={NaN}/></>);
 try{expect([...view.container.querySelectorAll('[role="img"]')].map(el=>el.getAttribute('aria-label'))).toEqual(['12','1.6h','—']);expect(view.container.textContent).toBe('121.6h—');}finally{await view.close();}
});
