import { Link, NavLink, Outlet, useLocation, useNavigationType } from "react-router-dom";
import { House, HardDrive, Settings2, Bookmark, Database, ChartNoAxesCombined, Compass } from "lucide-react";
import { useEffect, useLayoutEffect, useRef, useState } from "react";
import { useApp } from "../context/AppContext";
import { useT } from "../lib/i18n";
import AppErrorBoundary from "./AppErrorBoundary";
import { useNativeLifecycle } from "../lib/nativeLifecycle";
import { useQueryClient } from "@tanstack/react-query";
import { buzz } from "../lib/haptics";
import AmbientBackground from './AmbientBackground';
import {NAV_PREF_KEY,readNavPrefs} from '../lib/trackingNavigation';
import {AnimatedSelection,TransitionPanel} from './design/MotionSystem';
export {NAV_PREF_KEY,readNavPrefs};

// Separate media destinations; TorBox remains optional in Settings → Appearance.
export const NAV_ITEMS = [
  { to: "/", label: "Home", icon: House, id: "home", end: true },
  { to: "/discover", label: "Discover", icon: Compass, id: "discover" },
  { to: "/watchlist", label: "Watchlist", icon: Bookmark, id: "watchlist" },
  { to: "/library", label: "Library", icon: HardDrive, id: "library", match: ["/torbox"] },
  { to: "/account", label: "Settings", icon: Settings2, id: "account" },
  { to: "/tracking", label: "Tracking", icon: ChartNoAxesCombined, id: "tracking", match:["/history","/progress","/calendar"] },
  { to: "/torbox", label: "TorBox", icon: Database, id: "torbox" },
];
// Hide the bar on screens that bring their own bottom action bar.
const BARE = [/^\/title\//, /^\/share\//];
const isBare = (pathname) => BARE.some((re) => re.test(pathname));

function Header() {
  const t = useT();
  const {pathname}=useLocation();
  const home=pathname==='/';
  const { key, me, meError, meLoading } = useApp();
  const ok = key && me && !meError;
  if(!home)return null;
  return (
    <header data-testid="app-header" data-home={home} className={home?"stak-header stak-header-home":"stak-header stak-header-compact"}>
      <div className="mx-auto flex max-w-6xl items-center justify-between px-4 sm:px-6">
        <Link to="/" className="flex items-baseline gap-1" aria-label="STAK">
          <img src="/icon.svg" alt="" className="stak-brand-icon" width="40" height="40"/>
          <span className="stak-brand-name" aria-hidden="true">STAK</span>
        </Link>
        {home && <Link to="/account?section=account" className="home-connection" aria-label={ok?'TorBox connection verified':key && meLoading?'Checking TorBox connection':key?'TorBox connection needs attention':'Configure TorBox'}>
          <span className={`h-1.5 w-1.5 rounded-full ${ok ? "bg-[#10B981]" : "bg-[#D71921] blink"}`} />
          {ok ? 'TorBox' : key && meLoading ? t("Connecting…") : key ? t("Connection error") : t("No key")}
        </Link>}
      </div>
    </header>
  );
}

function BottomNav() {
  const t = useT();
  const { pathname } = useLocation();
  const [order, setOrder] = useState(readNavPrefs);
  useEffect(() => {
    const refresh = () => setOrder(readNavPrefs());
    window.addEventListener("dmm-nav-change", refresh);
    window.addEventListener("storage", refresh);
    return () => { window.removeEventListener("dmm-nav-change", refresh); window.removeEventListener("storage", refresh); };
  }, []);
  const items = order.map((id) => NAV_ITEMS.find((x) => x.id === id)).filter(Boolean);
  // One active tab: prefer an exact route match, then a tab that lists the route in `match`.
  const exact = items.find((x) => (x.end ? pathname === x.to : pathname === x.to || pathname.startsWith(`${x.to}/`)));
  const active = (exact || items.find((x) => x.match?.includes(pathname)))?.id;
  return (
    <nav data-discover={pathname==='/discover' || pathname==='/tracking'} aria-label="Main navigation" className="dmm-floating-nav fixed inset-x-3 bottom-[calc(env(safe-area-inset-bottom,0px)+12px)] z-50 mx-auto max-w-xl">
      <AnimatedSelection value={active} className="no-scrollbar flex overflow-x-auto px-1">
        {items.map(({ to, label, icon: Icon, id, end }) => {
          const on = id === active;
          return (
            <NavLink key={id} data-value={id} to={to} end={end} aria-label={t(label)} title={t(label)} onClick={() => buzz(6)} data-active={on} aria-current={on ? "page" : undefined}
              className="stak-nav-link">
              <span className="stak-nav-icon"><Icon size={19} aria-hidden="true" /></span>
              <span className="stak-nav-label">{t(label)}</span>
            </NavLink>
          );
        })}
      </AnimatedSelection>
    </nav>
  );
}


// Pull down at the top of the page: dot wave indicator, then a "dmm-refresh" event pages can listen to.
const PTR_ROUTES = ["/", "/torbox", "/progress", "/watchlist"];
export const REFRESH_EVENT = "dmm-refresh";
function PullToRefresh({ enabled }) {
  const ref = useRef(null);
  const state = useRef({ y: 0, x: 0, pull: 0, active: false, busy: false });
  useEffect(() => {
    if (!enabled) return undefined;
    const el = ref.current;
    const paint = (pull, spin) => {
      if (!el) return;
      const p = Math.min(1, pull / 80);
      el.style.opacity = spin ? "1" : String(p);
      el.style.transform = `translate(-50%, ${spin ? 0 : -30 + p * 30}px)`;
      el.dataset.spin = spin ? "1" : "0";
    };
    const start = (e) => {
      const st = state.current;
      if (st.busy || window.scrollY > 0 || e.target.closest?.('[role="dialog"],[data-no-ptr]')) { st.active = false; return; }
      st.y = e.touches[0].clientY; st.x = e.touches[0].clientX; st.pull = 0; st.active = true;
    };
    const move = (e) => {
      const st = state.current;
      if (!st.active) return;
      const dy = e.touches[0].clientY - st.y, dx = e.touches[0].clientX - st.x;
      if (dy <= 0 || Math.abs(dx) > dy) { st.pull = 0; paint(0, false); return; }
      st.pull = dy * 0.6;
      paint(st.pull, false);
    };
    const end = () => {
      const st = state.current;
      if (!st.active) return;
      st.active = false;
      if (st.pull >= 80) {
        st.busy = true;
        buzz(14);
        paint(80, true);
        window.dispatchEvent(new Event(REFRESH_EVENT));
        setTimeout(() => { st.busy = false; paint(0, false); }, 1100);
      } else paint(0, false);
    };
    window.addEventListener("touchstart", start, { passive: true });
    window.addEventListener("touchmove", move, { passive: true });
    window.addEventListener("touchend", end);
    window.addEventListener("touchcancel", end);
    return () => {
      window.removeEventListener("touchstart", start); window.removeEventListener("touchmove", move);
      window.removeEventListener("touchend", end); window.removeEventListener("touchcancel", end);
    };
  }, [enabled]);
  if (!enabled) return null;
  return (
    <div ref={ref} className="ptr" aria-hidden="true">
      <div className="grid grid-cols-5 gap-1 rounded-full border border-[color:var(--c-2a2a2a)] bg-[rgb(var(--c-111111-rgb)/0.9)] px-3 py-2 backdrop-blur-md">
        {Array.from({ length: 5 }, (_, i) => <span key={i} className="dot-wave h-1.5 w-1.5 rounded-full bg-[color:var(--n-accent)]" style={{ animationDelay: `${i * 0.12}s` }} />)}
      </div>
    </div>
  );
}

// Back/forward restores the previous scroll position; new navigations start at the top.
const POS = new Map();
function useScrollMemory() {
  const loc = useLocation();
  const navType = useNavigationType();
  const previousPath = useRef(loc.pathname);
  useLayoutEffect(() => {
    const k = loc.key;
    let raf = 0;
    const onScroll = () => {
      if (raf) return;
      raf = requestAnimationFrame(() => { raf = 0; POS.set(k, window.scrollY); if(POS.size>100)POS.delete(POS.keys().next().value); });
    };
    window.addEventListener("scroll", onScroll, { passive: true });
    return () => { window.removeEventListener("scroll", onScroll); if (raf) cancelAnimationFrame(raf); };
  }, [loc.key]);
  useEffect(() => {
    // Tracking owns independent Activity/Calendar positions. Query-only view
    // changes must not race its restoration with a second scroll-to-top.
    const trackingViewChange = previousPath.current === loc.pathname && loc.pathname === '/tracking';
    previousPath.current = loc.pathname;
    if (trackingViewChange) return undefined;
    if (navType === "POP" && POS.has(loc.key)) {
      const y = POS.get(loc.key);
      let tries = 0;
      let timer;
      const tick = () => {
        window.scrollTo(0, y);
        // content may still be loading: retry briefly until the page is tall enough
        if (Math.abs(window.scrollY - y) > 4 && tries++ < 30) timer = setTimeout(tick, 60);
      };
      tick();
      return () => clearTimeout(timer);
    }
    window.scrollTo(0, 0);
    return undefined;
  }, [loc.key, loc.pathname, navType]);
}

function NetworkNotice() {
  const client=useQueryClient(),[offline,setOffline]=useState(()=>navigator.onLine===false);
  useEffect(()=>{
    const changed=()=>{const down=navigator.onLine===false;setOffline(down);if(!down){client.invalidateQueries({queryKey:['account']});client.invalidateQueries({queryKey:['jellyfin-library']});}};
    window.addEventListener('online',changed);window.addEventListener('offline',changed);
    return()=>{window.removeEventListener('online',changed);window.removeEventListener('offline',changed);};
  },[client]);
  return offline?<p role="status" className="mx-4 mt-3 rounded-xl border border-[color:var(--c-282828)] px-3 py-2 text-xs text-neutral-400">Device reports offline · saved pages remain available; provider actions need a connection.</p>:null;
}

export default function AppShell() {
  const { pathname } = useLocation();
  useScrollMemory();
  useNativeLifecycle();
  const bare = isBare(pathname);
  return (
    <div data-app-shell className="stak-shell dot-field min-h-[100dvh]">
      <AmbientBackground />
      <Header />
      <NetworkNotice/>
      <PullToRefresh enabled={PTR_ROUTES.includes(pathname)} />
      <main data-screen={pathname==='/'?'home':pathname.split('/')[1]} className="stak-page page-pad mx-auto max-w-6xl px-4 pt-5 sm:px-6">
        <TransitionPanel panelKey={pathname}><AppErrorBoundary><Outlet /></AppErrorBoundary></TransitionPanel>
      </main>
      {!bare && <BottomNav />}
    </div>
  );
}
