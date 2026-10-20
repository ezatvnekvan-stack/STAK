import React from 'react';
import {act,mount} from '../testUtils';
import {Button,Chip,SegmentedControl} from './StakUI';
import ProgressIndicator from './ProgressIndicator';
import {Dialog,DialogContent,DialogTitle,DialogDescription} from './ui/dialog';

test('button variants preserve actions, loading and disabled semantics',async()=>{
 const action=jest.fn(),view=await mount(<><Button onClick={action}>Save</Button><Button variant="destructive" disabled onClick={action}>Delete</Button><Button loading onClick={action}>Connecting</Button><Button variant="outlined" size="icon" aria-label="Refresh">↻</Button></>);
 try{const buttons=view.container.querySelectorAll('button');await act(async()=>buttons[0].click());await act(async()=>buttons[1].click());await act(async()=>buttons[2].click());expect(action).toHaveBeenCalledTimes(1);expect(buttons[0].type).toBe('button');expect(buttons[2].getAttribute('aria-busy')).toBe('true');expect(buttons[3].classList.contains('stak-button--icon')).toBe(true);}finally{await view.close();}
});
test('shared selectors keep real selected values and disabled choices',async()=>{
 const change=jest.fn(),view=await mount(<><Chip selected>Selected</Chip><SegmentedControl label="Type" value="movie" onChange={change} options={[{value:'movie',label:'Movies'},{value:'series',label:'Series'}]}/></>);
 try{const buttons=view.container.querySelectorAll('button');expect(buttons[1].getAttribute('aria-pressed')).toBe('true');await act(async()=>buttons[2].click());expect(change).toHaveBeenCalledWith('series');}finally{await view.close();}
});
test('shared progress exposes clamped values and genuine indeterminate state',async()=>{
 const view=await mount(<><ProgressIndicator value={150} label="Complete"/><ProgressIndicator value={-3} label="Empty"/><ProgressIndicator value={null} label="Scanning"/></>);
 try{const bars=view.container.querySelectorAll('[role="progressbar"]');expect(bars[0].getAttribute('aria-valuenow')).toBe('100');expect(bars[1].getAttribute('aria-valuenow')).toBe('0');expect(bars[2].hasAttribute('aria-valuenow')).toBe(false);expect(bars[2].dataset.indeterminate).toBe('true');expect(bars[0].querySelector('.stak-progress-fill').style.transform).toBe('scaleX(1)');}finally{await view.close();}
});

function EscapeDialog(){const [open,setOpen]=React.useState(false);return <><Button onClick={()=>setOpen(true)}>Open magnet</Button><Dialog open={open} onOpenChange={setOpen}><DialogContent><DialogTitle>Magnet</DialogTitle><DialogDescription>Test dialog dismissal</DialogDescription><textarea aria-label="Magnet field"/></DialogContent></Dialog></>;}
test('shared dialogs dismiss on Escape from a focused input without removing their actions',async()=>{
 const view=await mount(<EscapeDialog/>);
 try{await act(async()=>view.container.querySelector('button').click());const input=document.querySelector('textarea');expect(input).not.toBeNull();await act(async()=>{input.focus();input.dispatchEvent(new KeyboardEvent('keydown',{key:'Escape',bubbles:true}));});expect(document.querySelector('[role="dialog"]')).toBeNull();}finally{await view.close();}
});
