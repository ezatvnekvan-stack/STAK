import React from "react";
import { Capacitor } from "@capacitor/core";
import ReactDOM from "react-dom/client";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import "@/index.css";
import "@/styles/stak.css";
import "@/styles/ambient.css";
import "@/styles/discover.css";
import "@/styles/tracking.css";
import "@/styles/collections.css";
import "@/styles/details.css";
import "@/styles/refinement.css";
import "@/styles/design.css";
import { hydrateSecrets } from "@/lib/secureStorage";
import AppErrorBoundary from "@/components/AppErrorBoundary";
import { applyTheme } from "@/lib/theme";

applyTheme();
const launchStarted=performance.now();

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 60_000,
      refetchOnWindowFocus: false,
    },
  },
});

const root = ReactDOM.createRoot(document.getElementById("root"));
async function start() {
  await hydrateSecrets();
  const { default: App } = await import("@/App");
  root.render(
  <React.StrictMode>
    <QueryClientProvider client={queryClient}>
      <AppErrorBoundary><App /></AppErrorBoundary>
    </QueryClientProvider>
  </React.StrictMode>,
);
  requestAnimationFrame(() => requestAnimationFrame(dismissSplash));

}
start().catch(() => {
  dismissSplash();
  root.render(<main role="alert" style={{ padding: 32, background: '#101010', color: '#eee', minHeight: '100vh' }}><h1>STAK</h1><p>Encrypted storage could not be opened. Your saved data has not been cleared.</p><button className="n-btn-primary" onClick={() => window.location.reload()}>Retry startup</button></main>);
});

// Dismiss after the hydrated app can paint, rather than imposing a startup delay.
function dismissSplash() {
  const splash = document.getElementById("splash");
  if (!splash) return;
  const reduced=document.documentElement.dataset.reducedMotion==='true' || window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;
  const wait=reduced?0:Math.max(0,360-(performance.now()-launchStarted));
  window.setTimeout(()=>{splash.classList.add("hide");window.setTimeout(()=>splash.remove(),250);},wait);
}

// A cached web service worker can serve obsolete assets inside Capacitor.
if (process.env.NODE_ENV === "production" && process.env.REACT_APP_CAPACITOR_OFFLINE !== "true" && !Capacitor.isNativePlatform() && "serviceWorker" in navigator) {
  window.addEventListener("load", () => navigator.serviceWorker.register("/sw.js").catch(() => {}));
}

if ((process.env.REACT_APP_CAPACITOR_OFFLINE === "true" || Capacitor.isNativePlatform()) && "serviceWorker" in navigator) {
  navigator.serviceWorker.getRegistrations().then(regs => Promise.all(regs.map(r => r.unregister()))).catch(() => {});
}
