import { useSyncExternalStore } from "react";

// Theme (dark / light / system) and accent colour. Stored in localStorage; applied on <html>.
const T_KEY = "dmm-theme";
const A_KEY = "dmm-accent";
const I_KEY = "dmm-interface-style";
export const ACCENTS = {
  red: { label: "Nothing Red", color: "#D71921", rgb: "215 25 33", fg: "#ffffff" },
  green: { label: "Green", color: "#10B981", rgb: "16 185 129", fg: "#04130c" },
  blue: { label: "Blue", color: "#3B82F6", rgb: "59 130 246", fg: "#ffffff" },
  white: { label: "Pure White", color: "#FFFFFF", rgb: "255 255 255", fg: "#000000" },
  graphite: { label: "Graphite", color: "#707070" },
  orange: { label: "Orange", color: "#F97316" },
  purple: { label: "Purple", color: "#A78BFA" },
};

const isAccent = value => Object.prototype.hasOwnProperty.call(ACCENTS, value) || /^#[0-9a-f]{6}$/i.test(String(value));
const channels = hex => hex.match(/[0-9a-f]{2}/gi).map(value => parseInt(value, 16));
const luminance = rgb => rgb.map(value => { const n=value/255; return n<=.04045?n/12.92:((n+.055)/1.055)**2.4; }).reduce((sum,n,i)=>sum+n*[.2126,.7152,.0722][i],0);
export const contrastRatio = (a,b) => { const x=luminance(a),y=luminance(b);return (Math.max(x,y)+.05)/(Math.min(x,y)+.05); };
const hsl = rgb => {
  const [r,g,b]=rgb.map(n=>n/255),max=Math.max(r,g,b),min=Math.min(r,g,b),d=max-min,l=(max+min)/2;
  const h=!d?0:60*(max===r?((g-b)/d+6)%6:max===g?(b-r)/d+2:(r-g)/d+4);
  return `${h.toFixed(2)} ${(!d?0:d/(1-Math.abs(2*l-1))*100).toFixed(2)}% ${(l*100).toFixed(2)}%`;
};
export function resolveAccent(value, mode='dark') {
  let color=ACCENTS[value]?.color || (/^#[0-9a-f]{6}$/i.test(String(value))?value:'#D71921');
  if(value==='white' && mode==='light')color='#111111';
  const rgb=channels(color),foreground=contrastRatio(rgb,[0,0,0])>=contrastRatio(rgb,[255,255,255])?'#000000':'#FFFFFF';
  const background=mode==='light'?[244,244,242]:[24,24,24],toward=mode==='light'?0:255;
  let ink=rgb;
  for(let amount=.05;contrastRatio(ink,background)<4.5 && amount<=1;amount+=.05)ink=rgb.map(n=>Math.round(n+(toward-n)*amount));
  return {color,rgb:rgb.join(' '),fg:foreground,ink:`rgb(${ink.join(' ')})`,hsl:hsl(rgb),ring:hsl(ink)};
}

const read = (k, fallback) => { try { return localStorage.getItem(k) || fallback; } catch { return fallback; } };
let theme = read(T_KEY, "dark");
let accent = read(A_KEY, "red");
const interfaceStyle = "nothing";
// Preserve the old style value for compatibility without applying it or touching other preferences.
try { const legacy = localStorage.getItem(I_KEY); if (legacy && legacy !== 'nothing' && !localStorage.getItem('dmm-interface-style-legacy')) localStorage.setItem('dmm-interface-style-legacy', legacy); localStorage.setItem(I_KEY, 'nothing'); } catch { /* A denied store cannot prevent the unified theme from loading. */ }
if (!["dark", "light", "system"].includes(theme)) theme = "dark";
if (!isAccent(accent)) accent = "red";

const subs = new Set();
const mq = typeof window !== "undefined" && window.matchMedia ? window.matchMedia("(prefers-color-scheme: light)") : null;
export const resolvedTheme = () => (theme === "system" ? (mq?.matches ? "light" : "dark") : theme);

export function applyTheme() {
  const root = document.documentElement;
  const mode = resolvedTheme();
  root.dataset.theme = mode;
  root.dataset.interface = interfaceStyle;
  root.classList.toggle("dark", mode === "dark");
  root.style.colorScheme = mode;
  const a = resolveAccent(accent,mode);
  root.dataset.accent=accent;
  root.style.setProperty("--n-accent", a.color);
  root.style.setProperty("--n-accent-rgb", a.rgb);
  root.style.setProperty("--n-accent-fg", a.fg);
  root.style.setProperty("--n-accent-ink", a.ink);
  root.style.setProperty("--primary", a.hsl);
  root.style.setProperty("--primary-foreground", a.fg==='#000000'?'0 0% 0%':'0 0% 100%');
  root.style.setProperty("--ring", a.ring);
  document.querySelector('meta[name="theme-color"]')?.setAttribute("content", mode === "light" ? "#f4f4f2" : "#080808");
}

const emit = () => { applyTheme(); subs.forEach((f) => f()); };
const persist = (key, value) => { try { localStorage.setItem(key, value); return true; } catch { window.dispatchEvent(new Event('dmm-storage-error')); return false; } };
export const getTheme = () => theme;
export const getAccent = () => accent;
export const getInterfaceStyle = () => interfaceStyle;
// Legacy callers/backups cannot reactivate the removed style selector.
export function setInterfaceStyle() { return false; }
export function setTheme(next) { if (!["dark", "light", "system"].includes(next) || !persist(T_KEY, next)) return; theme = next; emit(); }
export function setAccent(next) { if (!isAccent(next) || !persist(A_KEY, next)) return; accent = next; emit(); }
mq?.addEventListener?.("change", () => { if (theme === "system") emit(); });

const subscribe = (f) => { subs.add(f); return () => subs.delete(f); };
const snapshot = () => `${theme}|${accent}|${resolvedTheme()}|${interfaceStyle}`;
export function useTheme() {
  const snap = useSyncExternalStore(subscribe, snapshot);
  const [t, a, r, i] = snap.split("|");
  return { theme: t, accent: a, resolved: r, interfaceStyle: i };
}
