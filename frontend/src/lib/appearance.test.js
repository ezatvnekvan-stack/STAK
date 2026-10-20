import { APPEARANCE_DEFAULTS, setAppearance } from './appearance';
afterEach(() => setAppearance(APPEARANCE_DEFAULTS));
test('ambient settings persist immediately and leave rating preferences intact', () => {
  setAppearance({ ratings: { imdb: false }, dotIntensity: 7, glowIntensity: 20, ambientAnimation: true, cinematicColors: false });
  const saved = JSON.parse(localStorage.getItem('dmm-appearance-v4'));
  expect(saved.dotIntensity).toBe(7); expect(saved.ratings.imdb).toBe(false);
  expect(document.documentElement.style.getPropertyValue('--stak-dot-opacity')).toBe('0.07');
  expect(document.documentElement.dataset.ambientAnimation).toBe('true'); expect(document.documentElement.dataset.cinematicColors).toBe('false');
  setAppearance({ dotIntensity: 1000, glowIntensity: -5 });
  expect(document.documentElement.style.getPropertyValue('--stak-dot-opacity')).toBe('0.1'); expect(document.documentElement.style.getPropertyValue('--stak-glow-opacity')).toBe('0');
});
test('legacy or malformed appearance data receives safe subtle defaults without losing existing choices', () => {
  localStorage.setItem('dmm-appearance-v4', JSON.stringify({ compact: true, ratings: { tmdb: false }, dotIntensity: 'loud', glowIntensity: null }));
  window.dispatchEvent(new StorageEvent('storage', { key: 'dmm-appearance-v4' }));
  expect(document.documentElement.dataset.compact).toBe('true'); expect(document.documentElement.dataset.ambientAnimation).toBe('false');
  expect(document.documentElement.style.getPropertyValue('--stak-dot-opacity')).toBe('0.05');
  setAppearance({ glowIntensity: 18 }); expect(JSON.parse(localStorage.getItem('dmm-appearance-v4')).ratings.tmdb).toBe(false);
});
