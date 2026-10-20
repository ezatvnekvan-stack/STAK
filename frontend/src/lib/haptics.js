// Short vibration on key actions. Off when the user disabled it or prefers reduced motion.
const KEY = "dmm-haptics";
export const hapticsOn = () => { try { return localStorage.getItem(KEY) !== "off"; } catch { return true; } };
export const setHaptics = (on) => { try { localStorage.setItem(KEY, on ? "on" : "off"); return true; } catch { window.dispatchEvent(new Event('dmm-storage-error')); return false; } };
const reduced = () => typeof window !== "undefined" && (window.matchMedia?.("(prefers-reduced-motion: reduce)").matches || document.documentElement.dataset.reducedMotion === 'true');
export function buzz(pattern = 10) {
  if (!hapticsOn() || reduced()) return;
  try { navigator.vibrate?.(pattern); } catch { /* unsupported */ }
}
