import React from 'react';
import {act,mount} from '../../testUtils';
import ThemeMotionPreview from './ThemeMotionPreview';
const mockSetTheme=jest.fn();
jest.mock('../../lib/theme',()=>({useTheme:()=>({resolved:'dark'}),setTheme:(...args)=>mockSetTheme(...args)}));
jest.mock('../../lib/appearance',()=>({useAppearance:()=>({reducedMotion:true})}));
test('reduced-motion theme preview remains a real accessible control without mounting canvas',async()=>{
 const view=await mount(<ThemeMotionPreview/>);
 try{const button=view.container.querySelector('button');expect(button.getAttribute('aria-label')).toBe('Switch to light theme');expect(view.container.querySelector('canvas')).toBeNull();await act(async()=>button.click());expect(mockSetTheme).toHaveBeenCalledWith('light');}finally{await view.close();}
});
