import React from 'react';
import {act,mount,until} from '../testUtils';
import MetadataSources from './MetadataSources';
import {api} from '../lib/api';
import {metadataDefaults} from '../lib/metadata/settings';
jest.mock('../lib/api',()=>({api:{get:jest.fn(),put:jest.fn(),post:jest.fn()},errMsg:e=>e.message,getKey:()=>''}));
jest.mock('./NothingSelect',()=>function Select({children,...props}){return <select {...props}>{children}</select>;});
let value,status,view;
beforeEach(()=>{value={...metadataDefaults};status={tmdb:'missing',omdb:'missing'};api.get.mockImplementation(async()=>({data:{settings:value,status}}));api.put.mockImplementation(async(path,body)=>{if(path==='/metadata/settings')value=body;return {data:{settings:value,status}};});api.post.mockResolvedValue({data:{valid:true}});});
afterEach(async()=>{if(view)await view.close();view=null;jest.clearAllMocks();});
test.each([
 ['search','tmdb'],['movie','cinemeta'],['movie','tmdb'],['series','tmdb'],['series','cinemeta'],
])('Metadata sources saves the %s option without changing other preferences',async(name,selected)=>{
 view=await mount(<MetadataSources/>);await until(()=>{const field=view.container.querySelector(`[data-testid="metadata-${name}"]`);return !field.disabled && [...field.options].some(option=>option.value===selected);});const input=view.container.querySelector(`[data-testid="metadata-${name}"]`);
 await act(async()=>{Object.getOwnPropertyDescriptor(HTMLSelectElement.prototype,'value').set.call(input,selected);input.dispatchEvent(new Event('change',{bubbles:true}));});await until(()=>api.put.mock.calls.length>0);expect(api.put).toHaveBeenCalledWith('/metadata/settings',{...metadataDefaults,[name]:selected});
});
test.each([['ratings',false],['imdbOnLists',true]])('Metadata sources persists the %s switch',async(name,checked)=>{
 view=await mount(<MetadataSources/>);const selector=`[data-testid="metadata-${name}"]`;await until(()=>!view.container.querySelector(selector).disabled);await act(async()=>view.container.querySelector(selector).click());expect(api.put).toHaveBeenCalledWith('/metadata/settings',{...metadataDefaults,[name]:checked});
});
test('missing keys show fallback warnings, all provider statuses and quota cost',async()=>{
 view=await mount(<MetadataSources/>);await until(()=>view.container.textContent.includes('TMDb key missing'));expect(view.container.textContent).toContain('daily OMDb quota');for(const provider of ['tmdb','omdb'])expect(view.container.querySelector(`[data-testid="metadata-status-${provider}"]`).textContent).toBe('missing');
});
test.each(['tmdb','omdb'])('Metadata sources tests configured %s credentials without exposing them',async provider=>{
 status[provider]='set';view=await mount(<MetadataSources/>);const selector=`[data-testid="metadata-test-${provider}"]`;await until(()=>!view.container.querySelector(selector).disabled);await act(async()=>view.container.querySelector(selector).click());expect(api.post).toHaveBeenCalledWith('/metadata/test',{provider});expect(view.container.querySelector(`[data-testid="metadata-key-${provider}"]`).type).toBe('password');
});
test('invalid key status and test failure preserve source choices',async()=>{
 status.tmdb='invalid';api.post.mockRejectedValue(new Error('Access denied'));view=await mount(<MetadataSources/>);await until(()=>view.container.querySelector('[data-testid="metadata-status-tmdb"]').textContent==='invalid');await act(async()=>view.container.querySelector('[data-testid="metadata-test-tmdb"]').click());await until(()=>view.container.textContent.includes('Access denied'));expect(value.series).toBe('tmdb');expect(api.put).not.toHaveBeenCalledWith('/metadata/settings',expect.anything());
});

test('Metadata sources has no TVDB provider, PIN or season-order controls',async()=>{
 view=await mount(<MetadataSources/>);await until(()=>!view.container.querySelector('[data-testid="metadata-series"]').disabled);expect(view.container.textContent).not.toMatch(/TVDB|subscriber|season order/i);expect([...view.container.querySelector('[data-testid="metadata-series"]').options].map(option=>option.value)).toEqual(['tmdb','cinemeta']);
});

test('Metadata sources has no language chooser and retains English metadata when saving sources',async()=>{
 view=await mount(<MetadataSources/>);await until(()=>!view.container.querySelector('[data-testid="metadata-search"]').disabled);expect(view.container.querySelector('[data-testid="metadata-language"]')).toBeNull();expect(view.container.textContent).not.toMatch(/Hungarian|Metadata language/);expect(value.language).toBe('en-US');
});

test('Save & verify does not replace a stored credential when validation fails',async()=>{
 status.tmdb='set';api.post.mockRejectedValue(new Error('Credential rejected'));
 view=await mount(<MetadataSources/>);await until(()=>!view.container.querySelector('[data-testid="metadata-key-tmdb"]').disabled);
 const input=view.container.querySelector('[data-testid="metadata-key-tmdb"]');
 await act(async()=>{Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set.call(input,'replacement-test-only');input.dispatchEvent(new Event('input',{bubbles:true}));});
 const section=input.closest('section');await act(async()=>[...section.querySelectorAll('button')].find(button=>button.textContent==='Save & verify').click());
 await until(()=>view.container.textContent.includes('Credential rejected'));
 expect(api.put).not.toHaveBeenCalledWith('/metadata/credentials',expect.anything());expect(input.value).toBe('replacement-test-only');
});
test('unified credentials retain masked input, reveal, API help, saving and removal controls',async()=>{
 view=await mount(<MetadataSources/>);await until(()=>!view.container.querySelector('[data-testid="metadata-key-tmdb"]').disabled);
 expect(view.container.querySelectorAll('input[type="password"]')).toHaveLength(2);
 expect(view.container.querySelectorAll('.settings-provider-link')).toHaveLength(2);
 await act(async()=>view.container.querySelector('[aria-label="Toggle TMDB key visibility"]').click());
 expect(view.container.querySelector('[data-testid="metadata-key-tmdb"]').type).toBe('text');
});
