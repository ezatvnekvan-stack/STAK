import React from 'react';
import { act, deferred, mount } from '../testUtils';
import TitleAmbient from './TitleAmbient';
import { getArtworkPalette, GRAPHITE } from '../lib/artworkPalette';
import { setAppearance } from '../lib/appearance';
jest.mock('../lib/artworkPalette', () => ({ getArtworkPalette: jest.fn(), GRAPHITE: '108 112 120' }));
let shell, view;
beforeEach(() => { shell = document.createElement('div'); shell.setAttribute('data-app-shell', ''); document.body.appendChild(shell); getArtworkPalette.mockReset(); setAppearance({ cinematicColors: true }); });
afterEach(async () => { if (view) await view.close(); view = null; shell.remove(); setAppearance({ cinematicColors: true }); });
test('artwork colours feed the shared shell without a second image layer', async () => {
  getArtworkPalette.mockResolvedValue('54 100 76');
  view = await mount(<TitleAmbient src="https://example.invalid/art.jpg"/>);
  expect(shell.dataset.cinematic).toBe('true'); expect(shell.style.getPropertyValue('--stak-artwork-rgb')).toBe('54 100 76');
  expect(shell.querySelector('img')).toBeNull(); expect(view.container.childElementCount).toBe(0);
  await act(async () => setAppearance({ cinematicColors: false }));
  expect(shell.style.getPropertyValue('--stak-artwork-rgb')).toBe(GRAPHITE);
  await view.close(); view = null; expect(shell.hasAttribute('data-cinematic')).toBe(false); expect(shell.style.getPropertyValue('--stak-artwork-rgb')).toBe('');
});
test('late palette responses cannot colour the next screen', async () => {
  const pending = deferred(); getArtworkPalette.mockReturnValue(pending.promise);
  view = await mount(<TitleAmbient src="https://example.invalid/late.jpg"/>);
  await view.close(); view = null;
  await act(async () => pending.resolve('120 40 50'));
  expect(shell.style.getPropertyValue('--stak-artwork-rgb')).toBe('');
});
test('disabled cinematic colours never request artwork', async () => {
  setAppearance({ cinematicColors: false });
  view = await mount(<TitleAmbient src="https://example.invalid/art.jpg"/>);
  expect(getArtworkPalette).not.toHaveBeenCalled(); expect(shell.style.getPropertyValue('--stak-artwork-rgb')).toBe(GRAPHITE);
});
