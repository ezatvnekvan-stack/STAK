import { useSyncExternalStore } from "react";

// English-only source strings; keep the existing translation API compatible.
const KEY = "dmm-lang";
const lang = "en";
const subs = new Set();
export const getLang = () => lang;
export function setLang() {
  const value = "en";
  try { localStorage.setItem(KEY, value); } catch { window.dispatchEvent(new Event('dmm-storage-error')); return; }
  document.documentElement.lang = lang;
  subs.forEach((f) => f());
}
const subscribe = (f) => { subs.add(f); return () => subs.delete(f); };

export const tr = (s) => s;
export function useT() {
  useSyncExternalStore(subscribe, getLang);
  return tr;
}
export const useLang = () => useSyncExternalStore(subscribe, getLang);
