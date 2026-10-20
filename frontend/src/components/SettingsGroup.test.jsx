import React from 'react';
import {MemoryRouter,useNavigate} from 'react-router-dom';
import {act,mount} from '../testUtils';
import SettingsGroup from './SettingsGroup';
beforeEach(()=>{window.scrollTo=jest.fn();});
function Harness(){const navigate=useNavigate();return <><button onClick={()=>navigate(-1)}>Browser back</button><SettingsGroup id="metadata" title="Metadata & ratings" hint="TMDb · OMDb"><input aria-label="Unified TMDb credential"/></SettingsGroup><SettingsGroup id="appearance" title="Appearance" hint="Dark · Light"><input aria-label="Appearance control"/></SettingsGroup></>;}
test('settings rows expose summaries, open one focused page and support navigation back',async()=>{
 const view=await mount(<MemoryRouter><Harness/></MemoryRouter>);
 try{
  expect(view.container.textContent).toContain('TMDb · OMDb');
  expect(view.container.querySelector('input')).toBeNull();
  await act(async()=>view.container.querySelector('[data-testid="settings-group-metadata"] button').click());
  expect(view.container.querySelector('[aria-label="Unified TMDb credential"]')).not.toBeNull();
  expect(view.container.querySelector('[data-testid="settings-group-appearance"]')).toBeNull();
  await act(async()=>view.container.querySelector('button').click());
  expect(view.container.querySelectorAll('.settings-row')).toHaveLength(2);
  expect(view.container.querySelector('input')).toBeNull();
 }finally{await view.close();}
});
test('settings deep links restore a focused page and unknown sections retain the index',async()=>{
 for(const [section,count] of [['appearance',1],['obsolete',2]]){
  const view=await mount(<MemoryRouter initialEntries={[`/account?section=${section}`]}><Harness/></MemoryRouter>);
  try{expect(view.container.querySelectorAll('[data-testid^="settings-group-"]')).toHaveLength(count);expect(Boolean(view.container.querySelector('[aria-label="Appearance control"]'))).toBe(section==='appearance');}finally{await view.close();}
 }
});
