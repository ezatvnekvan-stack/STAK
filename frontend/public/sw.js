// STAK service worker: offline app shell + cached static assets and poster images.
// /api/* is never cached or intercepted.
const VERSION = "stak-1.0.0";
const STATIC = `${VERSION}-static`;
const IMAGES = `${VERSION}-img`;
const MAX_IMAGES = 300;
const OFFLINE_HTML =
  "<h1 style='font-family:monospace;color:#fff;background:#080808;padding:24px'>STAK 1.0.0 offline</h1>";

self.addEventListener("install", () => self.skipWaiting());

self.addEventListener("activate", (e) => {
  e.waitUntil(
    caches
      .keys()
      .then((names) => Promise.all(names.filter((n) => !n.startsWith(VERSION)).map((n) => caches.delete(n))))
      .then(() => self.clients.claim())
  );
});

async function trim(name, max) {
  const cache = await caches.open(name);
  const keys = await cache.keys();
  if (keys.length > max) await Promise.all(keys.slice(0, keys.length - max).map((k) => cache.delete(k)));
}

const cacheable = (res) => res && (res.ok || res.type === "opaque");

self.addEventListener("fetch", (e) => {
  const req = e.request;
  if (req.method !== "GET") return;
  const url = new URL(req.url);
  const sameOrigin = url.origin === self.location.origin;
  if (sameOrigin && url.pathname.startsWith("/api/")) return;
  if (sameOrigin && url.pathname === "/dmm.apk") return;

  // Pages: network first, fall back to the cached app shell.
  if (req.mode === "navigate") {
    e.respondWith(
      fetch(req)
        .then((res) => {
          if (res.ok) {
            const copy = res.clone();
            caches.open(STATIC).then((c) => c.put("/", copy));
          }
          return res;
        })
        .catch(async () => (await caches.match("/")) || new Response(OFFLINE_HTML, { headers: { "Content-Type": "text/html" } }))
    );
    return;
  }

  // Hashed build files: cache first.
  if (sameOrigin && url.pathname.startsWith("/static/")) {
    e.respondWith(
      caches.match(req).then(
        (hit) =>
          hit ||
          fetch(req).then((res) => {
            if (cacheable(res)) {
              const copy = res.clone();
              caches.open(STATIC).then((c) => c.put(req, copy));
            }
            return res;
          })
      )
    );
    return;
  }

  // Posters and other images (any origin): stale-while-revalidate.
  if (req.destination === "image") {
    e.respondWith(
      caches.open(IMAGES).then(async (cache) => {
        const hit = await cache.match(req);
        const net = fetch(req)
          .then((res) => {
            if (cacheable(res)) {
              cache.put(req, res.clone()).then(() => trim(IMAGES, MAX_IMAGES));
            }
            return res;
          })
          .catch(() => hit);
        return hit || net;
      })
    );
  }
});
