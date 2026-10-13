// Service worker: de app opent ook zonder verbinding. __BUILD__ wordt bij het publiceren vervangen, zodat een nieuwe versie de oude cache opruimt.
const V = "brainpal-__BUILD__";
const SHELL = ["./", "index.html", "manifest.webmanifest", "icon-192.png", "icon-512.png", "apple-touch-icon.png"];

self.addEventListener("install", (e) => {
  e.waitUntil(caches.open(V).then((c) => c.addAll(SHELL)).then(() => self.skipWaiting()));
});

self.addEventListener("activate", (e) => {
  e.waitUntil(
    caches.keys().then((keys) => Promise.all(keys.filter((k) => k !== V).map((k) => caches.delete(k)))).then(() => self.clients.claim())
  );
});

self.addEventListener("fetch", (e) => {
  const req = e.request;
  const url = new URL(req.url);
  if (req.method !== "GET" || url.origin !== location.origin) return; // plaatjes en artikelen: gewoon het netwerk
  if (url.pathname.endsWith("/data/feed.json")) {
    // nieuws: eerst het netwerk, bij geen verbinding het laatste dat we hadden
    e.respondWith(
      fetch(req).then((r) => { const copy = r.clone(); caches.open(V).then((c) => c.put("data/feed.json", copy)); return r; })
        .catch(() => caches.match("data/feed.json"))
    );
    return;
  }
  // de app zelf: uit de cache, en op de achtergrond verversen
  e.respondWith(
    caches.match(req, { ignoreSearch: true }).then((hit) => {
      const net = fetch(req).then((r) => { if (r.ok) caches.open(V).then((c) => c.put(req, r.clone())); return r; }).catch(() => hit);
      return hit || net;
    })
  );
});
