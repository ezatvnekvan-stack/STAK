import React from 'react';
import { act, mount, until } from '../testUtils';
import TrailerDialog from './TrailerDialog';
import { openExternal } from '../lib/api';
jest.mock('../lib/api',()=>({openExternal:jest.fn(),errMsg:error=>error.message}));
let view;
afterEach(async()=>{if(view)await view.close();view=null;jest.clearAllMocks();});
const button=text=>[...document.querySelectorAll('button')].find(b=>b.textContent===text);
test('trailers embed only validated YouTube keys with fullscreen and a safe referrer',async()=>{
 view=await mount(<TrailerDialog videos={[{key:'dQw4w9WgXcQ'}]} onClose={jest.fn()}/>);
 const frame=document.querySelector('iframe');expect(frame.src).toMatch(/^https:\/\/www.youtube-nocookie.com\/embed\/dQw4w9WgXcQ\?/);expect(frame.allowFullscreen).toBe(true);expect(frame.getAttribute('referrerpolicy')).toBe('strict-origin-when-cross-origin');
});
test('only messages from the current trusted iframe can report embedding errors',async()=>{
 view=await mount(<TrailerDialog initialKey="dQw4w9WgXcQ" onClose={jest.fn()}/>);
 const frame=document.querySelector('iframe'),data=JSON.stringify({event:'onError',info:153});
 await act(async()=>window.dispatchEvent(new MessageEvent('message',{source:frame.contentWindow,origin:'https://untrusted.example',data})));
 expect(document.querySelector('[role="alert"]')).toBeNull();
 await act(async()=>window.dispatchEvent(new MessageEvent('message',{source:frame.contentWindow,origin:'https://www.youtube-nocookie.com',data})));
 expect(document.querySelector('[role="alert"]').textContent).toContain('WebView');
});
test('external fallback failure stays recoverable without closing the trailer',async()=>{
 const close=jest.fn();openExternal.mockRejectedValue(new Error('Browser unavailable'));
 view=await mount(<TrailerDialog initialKey="dQw4w9WgXcQ" onClose={close}/>);
 await act(async()=>button('Open YouTube').click());await until(()=>document.querySelector('[role="alert"]'));
 expect(openExternal).toHaveBeenCalledWith('https://www.youtube.com/watch?v=dQw4w9WgXcQ');expect(close).not.toHaveBeenCalled();
});
test('malformed lists and unsafe initial links never create a media frame',async()=>{
 view=await mount(<TrailerDialog videos={{unexpected:true}} initialKey="https://invalid.example/trailer" onClose={jest.fn()}/>);
 expect(document.querySelector('iframe,video')).toBeNull();expect(button('Open YouTube').disabled).toBe(true);
});
