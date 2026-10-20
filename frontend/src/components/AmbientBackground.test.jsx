import React from 'react';
import { act, mount } from '../testUtils';
import AmbientBackground from './AmbientBackground';
test('the single static dot layer pauses glow movement on native and browser backgrounding', async () => {
  const view = await mount(<AmbientBackground/>), previous = Object.getOwnPropertyDescriptor(document, 'hidden');
  try {
    const ambient = view.container.firstChild;
    expect(ambient.getAttribute('aria-hidden')).toBe('true'); expect(ambient.querySelectorAll('.stak-ambient-dots')).toHaveLength(1);
    await act(async () => window.dispatchEvent(new CustomEvent('stak-app-active', { detail: false })));
    expect(ambient.dataset.paused).toBe('true');
    Object.defineProperty(document, 'hidden', { configurable: true, value: false });
    await act(async () => window.dispatchEvent(new CustomEvent('stak-app-active', { detail: true })));
    expect(ambient.dataset.paused).toBe('false');
    Object.defineProperty(document, 'hidden', { configurable: true, value: true });
    await act(async () => document.dispatchEvent(new Event('visibilitychange')));
    expect(ambient.dataset.paused).toBe('true');
  } finally { if (previous) Object.defineProperty(document, 'hidden', previous); else delete document.hidden; await view.close(); }
});
