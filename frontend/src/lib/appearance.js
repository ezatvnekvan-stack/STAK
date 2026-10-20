import { useSyncExternalStore } from 'react';
import { readJSON, writeStored } from './storage';
export const RATING_SOURCES = { imdb: 'IMDb', tmdb: 'TMDB', rt: 'Rotten Tomatoes', metacritic: 'Metacritic' };
const KEY = 'dmm-appearance-v4';
export const APPEARANCE_DEFAULTS = { ratings: { imdb: true, tmdb: true, rt: true, metacritic: true }, reducedMotion: false, compact: false, amoled: false, dotIntensity: 5, glowIntensity: 16, ambientAnimation: false, cinematicColors: true };
const defaults = APPEARANCE_DEFAULTS;
const flags = ['reducedMotion', 'compact', 'amoled', 'ambientAnimation', 'cinematicColors'];
const levels = { dotIntensity: 10, glowIntensity: 30 };
const level = (key, value) => typeof value === 'number' && Number.isFinite(value) ? Math.max(0, Math.min(levels[key], value)) : defaults[key];
const read = () => {
  const v = readJSON(KEY, {}, x => x && typeof x === 'object' && !Array.isArray(x));
  return { ratings: Object.fromEntries(Object.keys(defaults.ratings).map(k => [k, typeof v.ratings?.[k] === 'boolean' ? v.ratings[k] : defaults.ratings[k]])), ...Object.fromEntries(flags.map(k => [k, typeof v[k] === 'boolean' ? v[k] : defaults[k]])), ...Object.fromEntries(Object.keys(levels).map(k => [k, level(k, v[k])])) };
};
let current = read();
const listeners = new Set();
const apply = () => {
  if (typeof document === 'undefined') return;
  const root = document.documentElement;
  for (const key of flags) root.dataset[key] = String(Boolean(current[key]));
  root.style.setProperty('--stak-dot-opacity', String(current.dotIntensity / 100));
  root.style.setProperty('--stak-glow-opacity', String(current.glowIntensity / 100));
};
apply();
export function setAppearance(patch) {
  if (!patch || typeof patch !== 'object') return false;
  const next = { ...current, ratings: { ...current.ratings } };
  for (const key of flags) if (typeof patch[key] === 'boolean') next[key] = patch[key];
  for (const key of Object.keys(levels)) if (typeof patch[key] === 'number' && Number.isFinite(patch[key])) next[key] = level(key, patch[key]);
  for (const key of Object.keys(defaults.ratings)) if (typeof patch.ratings?.[key] === 'boolean') next.ratings[key] = patch.ratings[key];
  if (!writeStored(KEY, JSON.stringify(next))) { window.dispatchEvent(new Event('dmm-storage-error')); return false; }
  current = next; apply(); listeners.forEach(f => f()); return true;
}
window.addEventListener('storage', e => { if (e.key === KEY || e.key === null) { current = read(); apply(); listeners.forEach(f => f()); } });
export function useAppearance() { return useSyncExternalStore(f => { listeners.add(f); return () => listeners.delete(f); }, () => current); }
