import React from 'react';
import {Simulate} from 'react-dom/test-utils';
import {act,mount} from '../testUtils';
import WatchedDateDialog from './WatchedDateDialog';
let view;afterEach(async()=>{if(view)await view.close();view=null;});
test('date selection and release-day are separate real actions',async()=>{
 const save=jest.fn();view=await mount(<WatchedDateDialog open onOpenChange={()=>{}} meta={{name:'Movie',release_date:'2020-03-01'}} onSave={save}/>);
 const input=document.querySelector('[aria-label="Watched on"]');await act(async()=>Simulate.change(input,{target:{value:'2021-04-05'}}));
 await act(async()=>document.querySelector('[data-testid="save-watched-date"]').click());expect(save).toHaveBeenLastCalledWith({watched_on:'2021-04-05'});
 await act(async()=>document.querySelector('[data-testid="watched-on-release-day"]').click());expect(save).toHaveBeenLastCalledWith({watched_on:'2020-03-01'});
});
test('missing release dates never invent a release-day success',async()=>{
 view=await mount(<WatchedDateDialog open onOpenChange={()=>{}} meta={{name:'Unknown'}} onSave={jest.fn()}/>);expect(document.querySelector('[data-testid="watched-on-release-day"]').disabled).toBe(true);
});
