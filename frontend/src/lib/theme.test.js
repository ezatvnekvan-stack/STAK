import { ACCENTS, applyTheme, contrastRatio, getAccent, resolveAccent, setAccent, setTheme } from './theme';
const rgb = value => value.match(/[a-f0-9]{2}/gi).map(n=>parseInt(n,16));
afterEach(()=>{setTheme('dark');setAccent('red');});
test('accent foregrounds and interactive text have readable contrast in dark and light modes',()=>{
  expect(contrastRatio([0,0,0],[255,255,255])).toBe(21);
  for(const mode of ['dark','light'])for(const key of [...Object.keys(ACCENTS),'#222222','#FDFDFD','#38684A']){
    const a=resolveAccent(key,mode);
    expect(contrastRatio(rgb(a.color),rgb(a.fg))).toBeGreaterThanOrEqual(4.5);
    expect(contrastRatio(a.ink.match(/\d+/g).map(Number),mode==='light'?[244,244,242]:[24,24,24])).toBeGreaterThanOrEqual(4.5);
  }
});
test('custom accent persists and updates shared fill, text, focus and primary tokens immediately',()=>{
  setAccent('#123456');applyTheme();
  expect(getAccent()).toBe('#123456');expect(localStorage.getItem('dmm-accent')).toBe('#123456');
  for(const key of ['--n-accent','--n-accent-ink','--n-accent-fg','--primary','--ring'])expect(document.documentElement.style.getPropertyValue(key)).toBeTruthy();
  expect(document.documentElement.style.getPropertyValue('--n-accent')).toBe('#123456');
  setAccent('url(https://example.invalid)');expect(getAccent()).toBe('#123456');
});
test('a saved custom accent survives module initialization without resetting other preferences',()=>{
  localStorage.setItem('dmm-accent','#AABBCC');localStorage.setItem('unrelated-setting','preserve');
  jest.isolateModules(()=>{const theme=require('./theme');expect(theme.getAccent()).toBe('#AABBCC');theme.applyTheme();});
  expect(localStorage.getItem('unrelated-setting')).toBe('preserve');
});
