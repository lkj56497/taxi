const C="taxi-v4";
const FILES=["./","index.html","manifest.json","icon-180.png","icon-192.png","icon-512.png"];
self.addEventListener("install",e=>{e.waitUntil(caches.open(C).then(c=>Promise.all(FILES.map(f=>fetch(f,{cache:"no-store"}).then(r=>r.ok&&c.put(f,r)).catch(()=>{})))));self.skipWaiting()});
self.addEventListener("activate",e=>{e.waitUntil(caches.keys().then(ks=>Promise.all(ks.filter(k=>k!==C).map(k=>caches.delete(k)))));self.clients.claim()});
self.addEventListener("fetch",e=>{if(e.request.method!=="GET")return;const u=new URL(e.request.url);if(u.origin!==location.origin||u.pathname.endsWith(".apk"))return;
 // 항상 인터넷에서 새로 받아오고(브라우저 임시 저장도 건너뜀), 인터넷이 없을 때만 저장해 둔 것을 씀
 e.respondWith(fetch(e.request,{cache:"no-store"}).then(res=>{if(res.ok){const cp=res.clone();caches.open(C).then(c=>c.put(e.request,cp))}return res})
  .catch(()=>caches.match(e.request,{ignoreSearch:true}).then(r=>r||caches.match("index.html"))))});
