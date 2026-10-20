import React from 'react';
import { act, mount, until } from '../testUtils';
import AppearanceSettings from './AppearanceSettings';
import HomeSettings from './HomeSettings';
import { api } from '../lib/api';
import { setLang } from '../lib/i18n';
import { ACCENTS, setTheme, setAccent } from '../lib/theme';
import { APPEARANCE_DEFAULTS, setAppearance } from '../lib/appearance';
jest.mock('../lib/api', () => ({ api: { get: jest.fn() } }));
jest.mock('../lib/home', () => ({ DEFAULT_HOME: [], sectionInfo: () => ({ label: 'Test' }), useHomePrefs: () => [[], jest.fn(), { loading: false, saving: false, error: '' }] }));
let view;
afterEach(async () => { if (view) await view.close(); view = null; setTheme('dark'); setAccent('red'); setAppearance(APPEARANCE_DEFAULTS); });
test('TMDb object genres render without crashing the Appearance home settings', async () => {
  api.get.mockResolvedValue({ data: { movie: [{ id: 28, name: 'Action' }], series: [{ id: 18, name: 'Drama' }] } });
  view = await mount(<HomeSettings />);
  await act(async()=>view.container.querySelector('[data-testid="home-genre-select"]').click());
  await until(()=>document.querySelector('[role="option"]'));
  const option=[...document.querySelectorAll('[role="option"]')].find(el=>el.textContent==='Action');
  expect(option).toBeDefined();
});
test('theme and accent selections update CSS tokens and remain saved', async () => {
  localStorage.setItem('dmm-lang','hu');
  setLang('hu');
  view = await mount(<AppearanceSettings />);
  const buttons = [...view.container.querySelectorAll('button[role="radio"]')];
  await act(async () => buttons.find(b => b.textContent === 'Light').click());
  expect(view.container.querySelector('[aria-label="Language"]')).toBeNull();
  expect(localStorage.getItem('dmm-lang')).toBe('en');
  expect(document.documentElement.dataset.theme).toBe('light');
  expect(localStorage.getItem('dmm-theme')).toBe('light');
  await act(async () => setAccent('blue')); 
  expect(document.documentElement.style.getPropertyValue('--n-accent')).toBe('#3B82F6');
  expect(localStorage.getItem('dmm-accent')).toBe('blue');
  expect(view.container.querySelector('[aria-label="Reduce animations"]')).not.toBeNull();
  expect(view.container.querySelector('[aria-label="Show IMDb"]')).not.toBeNull();
});
test('all accent presets and the custom picker apply without hiding appearance functions',async()=>{
  view=await mount(<AppearanceSettings/>);
  for(const [key,value] of Object.entries(ACCENTS)){
    const button=view.container.querySelector(`[aria-label="${value.label}"]`);
    await act(async()=>button.click());expect(button.getAttribute('aria-checked')).toBe('true');expect(localStorage.getItem('dmm-accent')).toBe(key);
  }
  const picker=view.container.querySelector('[aria-label="Custom accent colour"]');
  await act(async()=>{Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set.call(picker,'#123456');picker.dispatchEvent(new Event('input',{bubbles:true}));});
  expect(localStorage.getItem('dmm-accent')).toBe('#123456');expect(document.documentElement.style.getPropertyValue('--n-accent')).toBe('#123456');
  expect(view.container.querySelector('[aria-label="Show IMDb"]')).not.toBeNull();expect(view.container.querySelector('[aria-label="Haptics"]')).not.toBeNull();
});
test('atmosphere controls update immediately and reset only appearance preferences', async () => {
  localStorage.setItem('watchlist-keep-test', 'preserve');
  view = await mount(<AppearanceSettings/>);
  const dots = view.container.querySelector('[aria-label="Dot Matrix intensity"]');
  await act(async () => { Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set.call(dots, '8'); dots.dispatchEvent(new Event('input', { bubbles: true })); });
  expect(document.documentElement.style.getPropertyValue('--stak-dot-opacity')).toBe('0.08');
  const glow = view.container.querySelector('[aria-label="Ambient Glow intensity"]');
  await act(async () => { Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set.call(glow, '22'); glow.dispatchEvent(new Event('input', { bubbles: true })); });
  expect(document.documentElement.style.getPropertyValue('--stak-glow-opacity')).toBe('0.22');
  await act(async () => view.container.querySelector('[aria-label="Ambient animation"]').click());
  expect(document.documentElement.dataset.ambientAnimation).toBe('true');
  await act(async () => view.container.querySelector('[aria-label="Cinematic adaptive colours"]').click());
  expect(document.documentElement.dataset.cinematicColors).toBe('false');
  await act(async () => [...view.container.querySelectorAll('button')].find(el => el.textContent === 'Reset appearance').click());
  expect(document.documentElement.style.getPropertyValue('--stak-dot-opacity')).toBe('0.05');
  expect(document.documentElement.dataset.ambientAnimation).toBe('false'); expect(document.documentElement.dataset.cinematicColors).toBe('true');
  expect(localStorage.getItem('watchlist-keep-test')).toBe('preserve');
});
