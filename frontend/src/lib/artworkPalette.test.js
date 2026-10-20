import { getArtworkPalette, paletteFromPixels, GRAPHITE } from './artworkPalette';
import { cacheGet, cacheSet, DAY } from './metadata/cache';
jest.mock('./metadata/cache', () => ({ cacheGet: jest.fn(), cacheSet: jest.fn((key, value) => Promise.resolve(value)), DAY: 86400000 }));
beforeEach(() => { cacheGet.mockResolvedValue(null); cacheSet.mockImplementation((key, value) => Promise.resolve(value)); });
afterEach(() => jest.restoreAllMocks());
test('dominant artwork hues stay muted and bounded; absent/white/transparent artwork is graphite', () => {
  const rgb = paletteFromPixels(new Uint8ClampedArray([20, 180, 75, 255, 25, 175, 70, 255, 230, 30, 20, 255])).split(' ').map(Number);
  expect(rgb[1]).toBeGreaterThan(rgb[0]); expect(rgb[1]).toBeGreaterThan(rgb[2]); expect(Math.max(...rgb)).toBeLessThanOrEqual(145);
  expect(paletteFromPixels([])).toBe(GRAPHITE); expect(paletteFromPixels([255, 255, 255, 255, 50, 100, 20, 0])).toBe(GRAPHITE);
});
test('persistent palette hits avoid another image request', async () => {
  cacheGet.mockResolvedValueOnce('50 100 70'); const image = jest.spyOn(window, 'Image');
  expect(await getArtworkPalette('https://image.tmdb.org/t/p/w1280/cached.jpg')).toBe('50 100 70'); expect(image).not.toHaveBeenCalled();
});
test('sampling uses small artwork and stores only a colour with a seven-day TTL, never credentials', async () => {
  cacheGet.mockResolvedValueOnce(null);
  const sources = [];
  jest.spyOn(window, 'Image').mockImplementation(() => ({ set src(value) { sources.push(value); Promise.resolve().then(() => this.onload?.()); } }));
  jest.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue({ drawImage: jest.fn(), getImageData: () => ({ data: new Uint8ClampedArray([30, 140, 65, 255]) }) });
  const color = await getArtworkPalette('https://image.tmdb.org/t/p/w1280/fresh.jpg?api_key=test-private');
  expect(color).toMatch(/^\d+ \d+ \d+$/); expect(color).not.toBe(GRAPHITE); expect(sources[0]).toContain('/w300/fresh.jpg');
  const call = cacheSet.mock.calls.at(-1); expect(call[2]).toBe(7 * DAY); expect(JSON.stringify(call)).not.toMatch(/test-private|api_key|https:/);
});
test('unsafe URLs and failed/CORS-blocked sampling use graphite without rejecting', async () => {
  // An intentionally unsafe scheme for this rejection test, never executed.
  expect(await getArtworkPalette(['javascript', 'alert(1)'].join(':'))).toBe(GRAPHITE);
  expect(await getArtworkPalette('https://user:password@example.invalid/a.jpg')).toBe(GRAPHITE);
  cacheGet.mockResolvedValueOnce(null);
  jest.spyOn(window, 'Image').mockImplementation(() => ({ set src(value) { Promise.resolve().then(() => this.onerror?.()); } }));
  expect(await getArtworkPalette('https://example.invalid/blocked.jpg')).toBe(GRAPHITE);
});
test.each([[180, 35, 45], [30, 65, 190], [180, 110, 25], [125, 35, 160]])('artwork hue %p remains restrained', (r, g, b) => {
  const result=paletteFromPixels([r,g,b,255]).split(' ').map(Number), original=[r,g,b];
  expect(result.indexOf(Math.max(...result))).toBe(original.indexOf(Math.max(...original)));
  expect(Math.max(...result)).toBeLessThanOrEqual(145);
});
test('rapid navigation never samples more than four backdrops concurrently', async () => {
  cacheGet.mockResolvedValue(null);
  const images=[];
  jest.spyOn(window, 'Image').mockImplementation(() => { const image={set src(value) {}}; images.push(image); return image; });
  const work=Array.from({length:6},(_,i)=>getArtworkPalette(`https://example.invalid/queue-${i}.jpg`));
  for(let i=0;i<8;i++)await Promise.resolve();
  expect(images).toHaveLength(4);
  for(let i=0;i<6;i++){for(let j=0;j<8;j++)await Promise.resolve();images[i].onerror();}
  expect(await Promise.all(work)).toEqual(Array(6).fill(GRAPHITE));
});
