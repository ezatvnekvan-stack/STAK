import test from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import { readFile } from 'node:fs/promises';
import { resolve, dirname } from 'node:path';
import { webcrypto } from 'node:crypto';
const root = resolve(import.meta.dirname, '../../frontend/src/lib');
async function fixture() {
  const prefs = new Map(), calls = [];
  let respond = async options => {
    const user = new URL(options.url).pathname.match(/^\/Users\/([^/]+)$/);
    return { status: 200, headers: {}, data: user ? { Id: decodeURIComponent(user[1]), Policy: { IsAdministrator: false } } : {} };
  };
  const memoryStorage = { getItem: k => prefs.get(`browser.${k}`) ?? null, setItem: (k,v) => prefs.set(`browser.${k}`, String(v)), removeItem: k => prefs.delete(`browser.${k}`) };
  const document = { documentElement: { dataset: {}, classList:{toggle(){}}, style:{ values:new Map(), setProperty(k,v){this.values.set(k,v);} } }, querySelector:()=>null };
  const context = vm.createContext({ document, localStorage:memoryStorage, URL, URLSearchParams, TextEncoder, Uint8Array, crypto: webcrypto, AbortController, setTimeout, clearTimeout, console, Blob, atob, window: { localStorage: memoryStorage, dispatchEvent(){}, addEventListener(){}, removeEventListener(){} }, Event });
  const modules = new Map(), linking=new Map();
  async function load(path) {
    if (linking.has(path)) return linking.get(path);
    const pending=(async()=>{
    if (modules.has(path)) return modules.get(path);
    let module;
    if (path === 'react') module = new vm.SyntheticModule(['useSyncExternalStore'], function(){this.setExport('useSyncExternalStore', (sub, snapshot) => snapshot());}, {context, identifier:path});
    else if (path === '@capacitor/core') module = new vm.SyntheticModule(['CapacitorHttp','Capacitor','registerPlugin'], function() {
      this.setExport('Capacitor', { isNativePlatform: () => false });
      this.setExport('registerPlugin', () => ({}));
      this.setExport('CapacitorHttp', { request: async options => { calls.push(options); return respond(options); } });
    }, { context, identifier: path });
    else if (path === '@capacitor/preferences') module = new vm.SyntheticModule(['Preferences'], function() {
      this.setExport('Preferences', { get: async ({key}) => ({ value: prefs.get(key) ?? null }), set: async ({key,value}) => { prefs.set(key,value); }, remove: async ({key}) => { prefs.delete(key); } });
    }, { context, identifier: path });
    else module = new vm.SourceTextModule(await readFile(path,'utf8'), { context, identifier: path });
    modules.set(path,module);
    await module.link((specifier, parent) => load(specifier.startsWith('.') ? resolve(dirname(parent.identifier), `${specifier}.js`) : specifier));
    return module;
    })();linking.set(path,pending);return pending;
  }
  const mod = await load(resolve(root, 'offlineAdapter.js')); await mod.evaluate();
  const validation = await load(resolve(root, 'validation.js')); if (validation.status !== 'evaluated') await validation.evaluate();
  const transport = await load(resolve(root, 'transport.js')); if (transport.status !== 'evaluated') await transport.evaluate();
  const parser = await load(resolve(root, 'torznabParser.js')); if (parser.status !== 'evaluated') await parser.evaluate();
  return { document, memoryStorage, loadModule: async filename => { const m=await load(resolve(root,filename)); if(m.status!=='evaluated')await m.evaluate(); return m.namespace; }, prefs, calls, validation: validation.namespace, transport: transport.namespace, parser: parser.namespace, respond: fn => { respond = fn; }, request: (url, opts = {}) => mod.namespace.offlineAdapter({ method: 'get', headers: {}, url, ...opts }) };
}
const plain = x => JSON.parse(JSON.stringify(x));
test('LAN HTTP and HTTPS accepted; public HTTP, userinfo and unsafe schemes rejected', async () => {
  const { validation: v } = await fixture();
  for (const url of ['http://192.168.0.5:8096','http://10.0.0.2:8096','http://172.16.0.4','http://jellyfin.local','https://media.example.org/base/']) assert.ok(v.validateProviderURL(url));
  for (const url of ['http://8.8.8.8','https://key:secret@example.org','javascript:alert(1)','file:///tmp/a','http://localhost:8085','http://172.32.0.1']) assert.throws(() => v.validateProviderURL(url));
});
test('invalid TorBox envelopes and malformed torrent lists reject without fake success', async () => {
  const f = await fixture();
  for (const data of [null, 'not json', { success: false, data: [] }, { success: true, data: {} }]) {
    f.respond(async () => ({ status: 200, headers: {}, data }));
    await assert.rejects(f.request('/torbox/torrents', { headers: { 'X-TorBox-Key': 'fixture-only' } }));
  }
});
test('normalizes missing torrent names/files and bounds progress', async () => {
  const f = await fixture(); f.respond(async () => ({ status: 200, data: { success: true, data: [null, { id: 0, name: null, progress: 3, size: '42', files: null }] } }));
  const r = await f.request('/torbox/torrents', { headers: { 'X-TorBox-Key': 'fixture-only' } });
  assert.equal(r.data[0].name, 'Unnamed torrent'); assert.equal(r.data[0].progress, 1); assert.deepEqual(plain(r.data[0].files), []);
});
test('key validation requires an object user response', async () => {
  const f = await fixture(); f.respond(async () => ({ status: 200, data: { success: true, data: [] } }));
  await assert.rejects(f.request('/torbox/me', { headers: { 'X-TorBox-Key': 'fixture-only' } }));
});
test('credential failures never include echoed provider secrets', async () => {
  const f = await fixture(); f.respond(async () => ({ status: 401, data: { detail: 'echo-secret' } }));
  await assert.rejects(f.request('/torbox/me', { headers: { 'X-TorBox-Key': 'fixture-only' } }), e => !e.message.includes('echo-secret') && e.response.status === 401);
});
test('POST errors are not automatically retried', async () => {
  const f = await fixture(); f.respond(async () => ({ status: 503, data: {} }));
  await assert.rejects(f.transport.externalRequest('https://example.org', { method:'POST', data:{} })); assert.equal(f.calls.length,1);
});
test('long Retry-After rate limits do not hammer providers', async () => {
  const f = await fixture(); f.respond(async () => ({ status:429, headers:{'retry-after':'60'}, data:{} }));
  await assert.rejects(f.transport.externalRequest('https://example.org'), e => e.response.status === 429); assert.equal(f.calls.length,1);
});
test('GET transient errors retry once then succeed', async () => {
  const f = await fixture(); let n = 0; f.respond(async () => ++n === 1 ? {status:503, data:{}} : {status:200, data:{ok:true}});
  assert.equal((await f.transport.externalRequest('https://example.org')).ok,true); assert.equal(n,2);
});
test('abort discards in-flight native response and cleans up without waiting', async () => {
  const f = await fixture(), controller = new AbortController(); let complete;
  f.respond(() => new Promise(r => { complete = r; }));
  const req = f.transport.externalRequest('https://example.org', { signal:controller.signal }); controller.abort();
  await assert.rejects(req, e => e.code === 'ERR_CANCELED'); complete({status:200, data:{}});
});
test('corrupt local list recovers; subsequent writes preserve usability', async () => {
  const f = await fixture(); f.prefs.set('dmm.local.v1.guest.watchlist', '{}');
  assert.deepEqual(plain((await f.request('/watchlist')).data), []);
  await f.request('/watchlist', { method:'post', data: {imdb_id:'tt0133093', name:'The Matrix'} });
  assert.equal((await f.request('/watchlist')).data.length,1);
  await f.request('/watchlist/tt0133093', {method:'delete'}); assert.equal((await f.request('/watchlist')).data.length,0);
});
test('account namespaces isolate local state without plaintext credential keys', async () => {
  const f = await fixture(); const config = {method:'post', headers:{'X-TorBox-Key':'fixture-account-a'},data:{imdb_id:'tt0133093'}};
  await f.request('/watchlist', config);
  assert.equal((await f.request('/watchlist', {headers:config.headers})).data.length,1);
  assert.equal((await f.request('/watchlist', {headers:{'X-TorBox-Key':'fixture-account-b'}})).data.length,0);
  assert.ok([...f.prefs.keys()].every(k => !k.includes('fixture-account-a')));
});
test('TMDb discovery IDs preserve numeric provider ID; TMDb scores are not IMDb', async () => {
  const f = await fixture(); f.respond(async () => ({status:200,data:{results:[{id:603,title:'The Matrix',vote_average:8,vote_count:100,poster_path:'/poster.jpg'}]}}));
  const m = (await f.request('/discover/tmdb',{headers:{'X-TMDb-Key':'fixture-only'}})).data.items[0];
  assert.equal(m.id,'tmdb:603'); assert.equal(m.tmdb_id,603); assert.equal(m.imdbRating,null); assert.equal(m.tmdbRating,8);
});
test('series discovery translates movie date filters and sorting to TV parameters', async () => {
  const f = await fixture(); f.respond(async () => ({status:200,data:{results:[]}}));
  await f.request('/discover/tmdb',{headers:{'X-TMDb-Key':'fixture-only'},params:{category:'popular',type:'series',year_from:'2020',sort:'primary_release_date.desc'}});
  const u = new URL(f.calls[0].url); assert.equal(u.searchParams.get('first_air_date.gte'),'2020-01-01'); assert.equal(u.searchParams.get('sort_by'),'first_air_date.desc'); assert.equal(u.searchParams.has('primary_release_date.gte'),false);
});
test('genres return usable values without metadata credentials', async () => {
  const f=await fixture(); const data=(await f.request('/meta/genres')).data; assert.ok(data.movie.includes('Action')); assert.equal(f.calls.length,0);
});
test('TMDb resolve strips legacy prefix before calling external_ids', async () => {
  const f=await fixture(); f.respond(async()=>({status:200,data:{imdb_id:'tt0133093'}}));
  const r=await f.request('/meta/resolve',{headers:{'X-TMDb-Key':'fixture-only'},params:{tmdb_id:'tmdb:603',type:'movie'}}); assert.equal(r.data.imdb_id,'tt0133093'); assert.ok(f.calls[0].url.includes('/movie/603/external_ids'));
});
test('hash validation/base32 normalization and Torznab XML metadata/deduplication', async () => {
  const f=await fixture(); const hash='a'.repeat(40); const xml=`<rss><channel><item><title><![CDATA[Film &amp; Test]]></title><torznab:attr name="infohash" value="${hash}"/><enclosure length="1024" url="magnet:?xt=urn:btih:${hash}"/></item><item><title>Duplicate</title><link>magnet:?xt=urn:btih:${hash}</link></item></channel></rss>`;
  const list=f.parser.parseTorznab(xml); assert.equal(list.length,1); assert.equal(list[0].size,1024); assert.equal(list[0].name,'Film & Test'); assert.equal(f.parser.normalizeHash('A'.repeat(32)),'0'.repeat(40));
});
test('bad magnets fail before a remote request; supported creation uses multipart', async () => {
  const f=await fixture(); const headers={'X-TorBox-Key':'fixture-only'};
  await assert.rejects(f.request('/torbox/add',{method:'post',headers,data:{magnet:'magnet:?xt=bad'}})); assert.equal(f.calls.length,0);
  f.respond(async()=>({status:200,data:{success:true,data:{torrent_id:7}}}));
  await f.request('/torbox/add',{method:'post',headers,data:{hash:'a'.repeat(40)}}); assert.match(f.calls[0].headers['Content-Type'],/^multipart\/form-data; boundary=/); assert.ok(f.calls[0].data.includes('name="magnet"'));
});
test('cache checks normalize hashes and parse supported list envelope', async()=>{
  const f=await fixture(), hash='a'.repeat(40); f.respond(async()=>({status:200,data:{success:true,data:[{hash}]}}));
  const r=await f.request('/torbox/cached',{method:'post',headers:{'X-TorBox-Key':'fixture-only'},data:{hashes:[hash.toUpperCase(),hash,'bad']}}); assert.deepEqual(plain(r.data.cached),[hash]); assert.equal(new URL(f.calls[0].url).searchParams.get('hash'),hash);
});
test('a failed indexer is isolated and other results remain available', async()=>{
  const f=await fixture(); f.prefs.set('dmm.local.v1.guest.indexers',JSON.stringify([{kind:'stremio',url:'https://good.example/manifest.json',name:'good'},{kind:'stremio',url:'https://bad.example/manifest.json',name:'bad'}]));
  f.respond(async o=>o.url.includes('bad.example')?{status:401,data:{}}:{status:200,data:{streams:[{infoHash:'a'.repeat(40),title:'Film'}]}});
  const r=await f.request('/indexer-search/movie/tt0133093'); assert.equal(r.data.streams.length,1); assert.equal(r.data.errors.length,1);
});
test('Jellyfin configuration returns no auth token and disconnect removes active credentials',async()=>{
  const f=await fixture(); await f.request('/jellyfin',{method:'put',data:{url:'http://192.168.0.5:8096',api_key:'fixture-only',user_id:'u1',user_name:'User'}});
  const r=await f.request('/jellyfin'); assert.equal(r.data.connected,true); assert.equal(JSON.stringify(r.data).includes('fixture-only'),false);
  await f.request('/jellyfin',{method:'delete'}); assert.equal((await f.request('/jellyfin')).data.connected,false);
});
test('Jellyfin login calls real AuthenticateByName with client authorization',async()=>{
  const f=await fixture(); f.respond(async()=>({status:200,data:{AccessToken:'fixture-token',User:{Id:'u1',Name:'User'}}}));
  const r=await f.request('/jellyfin/login',{method:'post',data:{url:'https://jellyfin.example',username:'User',password:'fixture-only'}});
  assert.equal(r.data.users[0].id,'u1'); assert.ok(f.calls[0].url.endsWith('/Users/AuthenticateByName')); assert.match(f.calls[0].headers.Authorization,/Client="DMM Studio"/); assert.match(f.calls[0].headers.Authorization,/Version="1\.0\.0"/);
});
test('Jellyfin resume playback sends actual saved position; unpause uses Unpause',async()=>{
  const f=await fixture(); await f.request('/jellyfin',{method:'put',data:{url:'https://jellyfin.example',api_key:'fixture-only',user_id:'u1'}});
  f.respond(async o=>({status:200,data:o.method==='GET'?{UserData:{PlaybackPositionTicks:123456}}:null}));
  await f.request('/jellyfin/play',{method:'post',data:{session_id:'s1',item_id:'i1',resume:true}});
  assert.equal(new URL(f.calls.at(-1).url).searchParams.get('startPositionTicks'),'123456');
  await f.request('/jellyfin/playback',{method:'post',data:{session_id:'s1',action:'play'}}); assert.ok(new URL(f.calls.at(-1).url).pathname.endsWith('/Playing/Unpause'));
});
test('Jellyfin library pagination requests only movies/series and preserves total',async()=>{
  const f=await fixture(); await f.request('/jellyfin',{method:'put',data:{url:'https://jellyfin.example',api_key:'fixture-only',user_id:'u1'}});
  f.respond(async()=>({status:200,data:{Items:[],TotalRecordCount:400}}));
  const r=await f.request('/jellyfin/library-items',{params:{parent_id:'lib1',start:150}}); const u=new URL(f.calls.at(-1).url); assert.equal(u.searchParams.get('StartIndex'),'150'); assert.equal(u.searchParams.get('IncludeItemTypes'),'Movie,Series'); assert.equal(r.data.total,400);
});
test('local-first tracking remains saved when Jellyfin synchronization fails',async()=>{
  const f=await fixture(); await f.request('/jellyfin',{method:'put',data:{url:'https://jellyfin.example',api_key:'fixture-only',user_id:'u1'}}); f.respond(async()=>({status:401,data:{}}));
  const r=await f.request('/tracking/mark',{method:'post',data:{imdb_id:'tt0133093',played:true}}); assert.equal(r.data.jellyfin_failed,1); assert.equal((await f.request('/tracking/state')).data[0].played,true);
});
test('calendar uses lightweight following summaries and exposes partial failures',async()=>{
 const f=await fixture();await f.request('/watchlist',{method:'post',data:{imdb_id:'tt0133093',type:'series',name:'Example'}});await f.request('/watchlist',{method:'post',data:{imdb_id:'tt9999999',type:'series',name:'Unavailable'}});
 const today=new Date().toISOString().slice(0,10);f.respond(async o=>{const p=new URL(o.url).pathname;return {status:200,data:p.includes('tt9999999')?{}:p.includes('/find/')?{tv_results:[{id:1}]}:p.endsWith('/external_ids')?{imdb_id:'tt0133093'}:p.includes('/season/')?{episodes:[{air_date:today,season_number:1,episode_number:2,name:'Episode'}]}:{name:'Example',next_episode_to_air:{air_date:today,season_number:1,episode_number:2,name:'Episode'}}};});
 const r=await f.request('/calendar',{headers:{'X-TMDb-Key':'fixture'},params:{scope:'following',days:7}});assert.equal(r.data.events.length,1);assert.equal(r.data.events[0].date,today);assert.equal(r.data.errors.length,1);assert.ok(f.calls.every(c=>!c.url.includes('append_to_response')));
});
test('unknown endpoint rejects rather than pretending success',async()=>{
  const f=await fixture(); await assert.rejects(f.request('/missing'),e=>e.code==='ERR_OFFLINE_UNIMPLEMENTED');
});

test('concurrent local watchlist mutations cannot overwrite each other',async()=>{
  const f=await fixture(); await Promise.all(['tt0133093','tt0068646'].map(imdb_id=>f.request('/watchlist',{method:'post',data:{imdb_id}}))); assert.equal((await f.request('/watchlist')).data.length,2);
});
test('backups exclude credentials and merge missing records without deleting existing records',async()=>{
  const f=await fixture(); await f.request('/watchlist',{method:'post',data:{imdb_id:'tt0133093',name:'Matrix',api_key:'do-not-export'}});
  const doc=(await f.request('/backup')).data; assert.ok(!JSON.stringify(doc).includes('do-not-export'));
  doc.watchlist.push({imdb_id:'tt0068646',name:'The Godfather'}); const result=await f.request('/backup',{method:'post',data:doc}); assert.equal(result.data.added,1); assert.equal((await f.request('/watchlist')).data.length,2);
});
test('invalid backup schemas reject before state writes',async()=>{
  const f=await fixture(); await assert.rejects(f.request('/backup',{method:'post',data:{format:'other',version:1}})); assert.equal(f.prefs.size,0);
});
test('manual watched titles have useful local history without Jellyfin',async()=>{
  const f=await fixture(); await f.request('/tracking/mark',{method:'post',data:{imdb_id:'tt0133093',name:'The Matrix',played:true,type:'movie'}});
  const r=await f.request('/tracking/history'); assert.equal(r.data.recent.length,1); assert.equal(r.data.stats.movies,1); assert.equal(r.data.local_only,true);
});
test('theme and accent setters use the selected mode and CSS variables',async()=>{
  const f=await fixture(), t=await f.loadModule('theme.js'); t.setTheme('light');t.setAccent('blue'); assert.equal(f.document.documentElement.dataset.theme,'light');assert.equal(f.document.documentElement.style.values.get('--n-accent'),'#3B82F6');assert.equal(f.memoryStorage.getItem('dmm-theme'),'light');
  t.setTheme('bad');t.setAccent('__proto__');assert.equal(t.getTheme(),'light');assert.equal(t.getAccent(),'blue');
});
test('appearance settings normalize corrupt flags and preserve per-source ratings',async()=>{
  const f=await fixture(); f.memoryStorage.setItem('dmm-appearance-v4', JSON.stringify({ratings:{imdb:[],rt:false},reducedMotion:'yes',compact:true}));
  const a=await f.loadModule('appearance.js');const before=a.useAppearance();assert.equal(before.ratings.imdb,true);assert.equal(before.ratings.rt,false);assert.equal(before.reducedMotion,false);assert.equal(before.compact,true);
  a.setAppearance({reducedMotion:true,ratings:{tmdb:false}});assert.equal(f.document.documentElement.dataset.reducedMotion,'true');assert.equal(a.useAppearance().ratings.imdb,true);assert.equal(a.useAppearance().ratings.tmdb,false);
});
test('storage wrappers tolerate denied storage and malformed JSON',async()=>{
  const f=await fixture(), storage=await f.loadModule('storage.js');f.memoryStorage.setItem('bad','broken');assert.equal(storage.readJSON('bad','fallback'),'fallback');
  f.memoryStorage.getItem=()=>{throw Error('denied');};f.memoryStorage.setItem=()=>{throw Error('denied');};assert.equal(storage.readStored('bad','fallback'),'fallback');assert.equal(storage.writeStored('bad','value'),false);
});

test('catalog HTTP 307 follows HTTPS redirects and parses the final JSON', async () => {
  const f = await fixture();
  f.respond(async options => options.url.includes('v3-cinemeta.strem.io')
    ? { status: 307, headers: { Location: 'https://catalog.example/catalog/movie/top.json' }, data: 'redirect' }
    : { status: 200, data: JSON.stringify({ metas: [{ id: 'tt0133093', name: 'The Matrix' }] }) });
  const result = await f.request('/discover');
  assert.equal(result.data.items[0].name, 'The Matrix');
  assert.equal(f.calls.length, 2);
  assert.equal(f.calls[1].disableRedirects, true);
  assert.equal(Object.keys(f.calls[1].headers).length, 0);
});
test('redirects do not forward authorization or query keys to another origin', async () => {
  const f = await fixture();
  f.respond(async () => ({ status: 307, headers: { location: 'https://unrelated.example/' }, data: '' }));
  for (const options of [{ headers: { Authorization: 'Bearer fixture-only' } }, { params: { api_key: 'fixture-only' } }, { headers: { 'X-Emby-Token': 'fixture-only' } }]) {
    f.calls.length = 0;
    await assert.rejects(f.transport.externalRequest('https://provider.example/', options), /credentials were not forwarded/);
    assert.equal(f.calls.length, 1);
  }
});
test('same-origin authenticated redirects retain headers; downgrade and loops are rejected', async () => {
  const f = await fixture();
  f.respond(async options => new URL(options.url).pathname === '/old'
    ? { status: 308, headers: { location: '/new' }, data: '' }
    : { status: 200, data: { ok: true } });
  assert.equal((await f.transport.externalRequest('https://provider.example/old', { headers: { Authorization: 'fixture-only' } })).ok, true);
  assert.equal(f.calls[1].headers.Authorization, 'fixture-only');
  assert.throws(() => f.transport.redirectTarget('https://192.168.0.5/old', 'http://192.168.0.5/new'), /insecure/);
  f.respond(async () => ({ status: 307, headers: { location: '/loop' }, data: '' }));
  await assert.rejects(f.transport.externalRequest('https://provider.example/loop'), /redirect loop/);
});
test('concurrent public metadata requests share a native request', async () => {
  const f = await fixture();
  let finish;
  f.respond(() => new Promise(resolve => { finish = resolve; }));
  const one = f.transport.externalRequest('https://v3-cinemeta.strem.io/meta/movie/tt0133093.json');
  const two = f.transport.externalRequest('https://v3-cinemeta.strem.io/meta/movie/tt0133093.json');
  assert.equal(f.calls.length, 1);
  finish({ status: 200, data: { meta: { id: 'tt0133093' } } });
  assert.deepEqual(plain(await one), plain(await two));
});
test('Jellyfin verifies token and selected user before replacing an existing connection', async () => {
  const f = await fixture();
  await f.request('/jellyfin', { method: 'put', data: { url: 'https://jellyfin.example', api_key: 'fixture-valid', user_id: 'u1' } });
  const before = [...f.prefs];
  f.respond(async () => ({ status: 401, data: 'untrusted echo' }));
  await assert.rejects(f.request('/jellyfin', { method: 'put', data: { url: 'https://jellyfin.example', api_key: 'fixture-invalid', user_id: 'u1' } }), /Reconnect/);
  assert.deepEqual([...f.prefs], before);
  assert.equal(f.calls.at(-1).headers['X-Emby-Token'], 'fixture-invalid');
  assert.match(f.calls.at(-1).headers.Authorization, /^MediaBrowser /);
  assert.equal((await f.request('/jellyfin')).data.connected, true);
});
test('Jellyfin connection survives a TorBox key change without exporting credentials', async () => {
  const f = await fixture();
  await f.request('/jellyfin', { method: 'put', headers: { 'X-TorBox-Key': 'fixture-first' }, data: { url: 'https://jellyfin.example', api_key: 'fixture-only', user_id: 'u1' } });
  const changed = await f.request('/jellyfin', { headers: { 'X-TorBox-Key': 'fixture-second' } });
  assert.equal(changed.data.connected, true);
  assert.equal(JSON.stringify(changed.data).includes('fixture-only'), false);
  await f.request('/jellyfin', { method: 'delete', headers: { 'X-TorBox-Key': 'fixture-second' } });
  assert.equal((await f.request('/jellyfin', { headers: { 'X-TorBox-Key': 'fixture-first' } })).data.connected, false);
});
test('Jellyfin Next Up failure is visible while Continue Watching remains available', async () => {
  const f = await fixture();
  await f.request('/jellyfin', { method: 'put', data: { url: 'https://jellyfin.example', api_key: 'fixture-only', user_id: 'u1' } });
  f.respond(async options => options.url.includes('/Shows/NextUp') ? { status: 403, data: {} } : { status: 200, data: { Items: [{ Id: 'i1' }] } });
  const result = await f.request('/jellyfin/home');
  assert.equal(result.data.continue_watching.length, 1);
  assert.equal(result.data.errors[0].section, 'Next Up');
});
test('TMDb-only movie and episode searches resolve IMDb before contacting addons', async () => {
  for (const [type, id, target] of [['movie', 'tmdb:603', 'tt0133093'], ['series', 'tmdb:603:2:3', 'tt0133093%3A2%3A3']]) {
    const f = await fixture();
    f.respond(async options => options.url.includes('themoviedb') ? { status: 200, data: { imdb_id: 'tt0133093' } } : { status: 200, data: { streams: [{ infoHash: 'a'.repeat(40) }] } });
    const result = await f.request(`/streams/${type}/${id}`, { headers: { 'X-TMDb-Key': 'fixture-only' } });
    assert.equal(result.data.length, 1);
    assert.ok(f.calls[1].url.includes(`/stream/${type}/${target}.json`));
    assert.equal(f.calls[1].headers['X-TMDb-Key'], undefined);
  }
});
test('missing IMDb mappings produce a useful search error rather than a fake empty list', async () => {
  const f = await fixture();
  f.respond(async () => ({ status: 200, data: { imdb_id: null } }));
  await assert.rejects(f.request('/streams/movie/tmdb:603', { headers: { 'X-TMDb-Key': 'fixture-only' } }), /no IMDb identifier/i);
  assert.equal(f.calls.length, 1);
});

test('search genre and date filters affect returned titles without erasing server-filtered catalog results', async () => {
  const f = await fixture();
  f.respond(async () => ({ status: 200, data: { metas: [
    { id: 'tt0000001', name: 'Action', genres: ['Action'], releaseInfo: '2020', imdbRating: '8.2' },
    { id: 'tt0000002', name: 'Drama', genres: ['Drama'], releaseInfo: '2020', imdbRating: '9' },
    { id: 'tt0000003', name: 'Undated', genres: ['Action'], imdbRating: '8' }
  ] } }));
  const filtered = (await f.request('/discover', { params: { q: 'test', genre: 'Action', year_from: 2019, year_to: 2021 } })).data.items;
  assert.deepEqual(plain(filtered.map(x => x.id)), ['tt0000001']);
  const remote = await fixture();
  remote.respond(async () => ({ status: 200, data: { metas: [{ id: 'tt0000001', name: 'Provider genre result' }] } }));
  assert.equal((await remote.request('/discover', { params: { genre: 'Action' } })).data.items.length, 1);
});
test('TV certification checks existing content ratings and reports page-limited scope', async () => {
  const f = await fixture();
  f.respond(async options => options.url.includes('/content_ratings') ? { status: 200, data: { results: [{ iso_3166_1: 'US', rating: options.url.includes('/tv/1/') ? 'TV-14' : 'TV-MA' }] } } : { status: 200, data: { results: [{ id: 1, name: 'First' }, { id: 2, name: 'Second' }] } });
  const result = await f.request('/discover/tmdb', { headers: { 'X-TMDb-Key': 'fixture-only' }, params: { type: 'series', certification: 'TV-14' } });
  assert.deepEqual(plain(result.data.items.map(x => x.tmdb_id)), [1]);
  assert.match(result.data.filter_scope, /returned provider page/);
});
test('collection selection retrieves actual collection parts and preserves TMDb identities', async () => {
  const f = await fixture();
  f.respond(async () => ({ status: 200, data: { name: 'Example collection', parts: [{ id: 603, title: 'The Matrix' }] } }));
  const result = await f.request('/discover/tmdb', { headers: { 'X-TMDb-Key': 'fixture-only' }, params: { category: 'collections', type: 'movie', collection_id: 2344 } });
  assert.match(f.calls[0].url, /collection\/2344/);
  assert.equal(result.data.items[0].id, 'tmdb:603');
});
test('Metacritic stays on its 100-point scale, not a percentage', async () => {
  const f = await fixture();
  f.respond(async () => ({ status: 200, data: { imdbRating: '8.7', Metascore: '73', Ratings: [] } }));
  const result = await f.request('/ratings/tt0133093', { headers: { 'X-OMDb-Key': 'fixture-only' } });
  assert.equal(result.data.metacritic, '73');
});
test('failed appearance and theme persistence leaves previous values active', async () => {
  const f = await fixture();
  const theme = await f.loadModule('theme.js'), appearance = await f.loadModule('appearance.js');
  const original = plain(appearance.useAppearance());
  f.memoryStorage.setItem = () => { throw new Error('quota'); };
  theme.setTheme('light'); theme.setAccent('blue'); theme.setInterfaceStyle('terminal');
  assert.equal(theme.getTheme(), 'dark'); assert.equal(theme.getAccent(), 'red'); assert.equal(theme.getInterfaceStyle(), 'nothing');
  assert.equal(appearance.setAppearance({ compact: true }), false);
  assert.deepEqual(plain(appearance.useAppearance()), original);
});
test('manual episode timestamps and watched flags survive backup export/import', async () => {
  const f = await fixture();
  await f.request('/tracking/mark', { method: 'post', data: { imdb_id: 'tt0903747', type: 'series', played: true, episodes: [[1, 2]] } });
  const backup = (await f.request('/backup')).data;
  assert.equal(backup.tracking[0].episodes[0].played, true);
  assert.match(backup.tracking[0].episodes[0].last_played, /^\d{4}-/);
  await f.request('/backup', { method: 'post', headers: { 'X-TorBox-Key': 'fixture-second' }, data: backup });
  const restored = (await f.request('/tracking/state', { headers: { 'X-TorBox-Key': 'fixture-second' } })).data;
  assert.equal(restored[0].episodes[0].last_played, backup.tracking[0].episodes[0].last_played);
});
test('Jellyfin episode details resolve the parent series instead of using an episode IMDb ID', async () => {
  const f = await fixture();
  await f.request('/jellyfin', { method: 'put', data: { url: 'https://jellyfin.example', api_key: 'fixture-only', user_id: 'u1' } });
  f.respond(async options => ({ status: 200, data: options.url.endsWith('/Items/series1') ? { ProviderIds: { Imdb: 'tt0903747' } } : { Type: 'Episode', Id: 'episode1', SeriesId: 'series1', ProviderIds: { Imdb: 'tt0000002' } } }));
  assert.equal((await f.request('/jellyfin/item/episode1')).data.title_imdb_id, 'tt0903747');
});

test('cross-category title relevance prioritizes exact series, punctuation and matching year', async()=>{
  const f=await fixture(), {rankTitles}=await f.loadModule('searchRelevance.js');
  const items=[{id:'1',name:'The Bear Movie',type:'movie'},{id:'2',name:'The Bear',type:'series',year:2022},{id:'3',name:'The Bear',type:'movie',year:1988}];
  assert.equal(rankTitles(items,'The Bear 2022')[0].id,'2');
  assert.equal(rankTitles([{id:'original',name:'Blade Runner',year:1982},{id:'sequel',name:'Blade Runner 2049',year:2017}],'Blade Runner 2049')[0].id,'sequel');
  assert.equal(rankTitles([{id:'1',name:'Spider Man 2'},{id:'2',name:'Spider-Man'}],'Spider Man')[0].id,'2');
});
test('episode identity never uses the episode IMDb identifier as its series identifier',async()=>{
  const f=await fixture(),identity=await f.loadModule('titleIdentity.js');
  const episode={Type:'Episode',ParentIndexNumber:0,IndexNumber:1,ProviderIds:{Imdb:'tt1111111'}};
  assert.equal(identity.jellyfinIdentity(episode),null);
  assert.equal(identity.detailsHref(identity.jellyfinIdentity(episode,{Type:'Series',ProviderIds:{imdb:'tt2222222'}})),'/title/series/tt2222222?season=0&episode=1');
});
test('Jellyfin availability is neutral when disconnected or when a lookup is incomplete',async()=>{
  const f=await fixture(); assert.equal((await f.request('/jellyfin/title-availability')).data.in_library,null);
  await f.request('/jellyfin',{method:'put',data:{url:'https://jellyfin.example',api_key:'fixture-only',user_id:'u1'}});
  f.respond(async()=>({status:200,data:{Items:[]}}));
  assert.equal((await f.request('/jellyfin/title-availability',{params:{imdb_id:'tt0133093'}})).data.state,'unknown');
  f.respond(async()=>({status:200,data:{Items:[],TotalRecordCount:0}}));
  const missing=await f.request('/jellyfin/title-availability',{params:{imdb_id:'tt0133093'}});
  assert.equal(missing.data.in_library,false);assert.equal(missing.data.state,'unavailable');
});
test('Jellyfin availability reports selected episode playback, never series completion as playback',async()=>{
  const f=await fixture();await f.request('/jellyfin',{method:'put',data:{url:'https://jellyfin.example',api_key:'fixture-only',user_id:'u1'}});
  f.respond(async o=>({status:200,data:new URL(o.url).pathname.includes('/Episodes')?{Items:[{Id:'e2',ParentIndexNumber:1,IndexNumber:2,RunTimeTicks:12000000000,UserData:{PlaybackPositionTicks:3000000000}}],TotalRecordCount:1}:{Items:[{Id:'series',Type:'Series',ProviderIds:{Imdb:'tt0903747'},UserData:{PlayedPercentage:80}}],TotalRecordCount:1}}));
  const r=await f.request('/jellyfin/title-availability',{params:{imdb_id:'tt0903747',type:'series',season:1,episode:2}});
  assert.equal(r.data.playback.progress,25);assert.equal(r.data.resume_playback.episode,2);assert.equal(r.data.episodes[0].progress,25);assert.equal(r.data.episode_available,true);assert.match(r.data.web_url,/id=e2$/);
  const u=new URL(f.calls.at(-2).url);assert.equal(u.searchParams.get('AnyProviderIdEquals'),'imdb.tt0903747');
});
test('Jellyfin library counts require actual per-user totals and reject missing counts',async()=>{
  const f=await fixture();await f.request('/jellyfin',{method:'put',data:{url:'https://jellyfin.example',api_key:'fixture-only',user_id:'u1'}});
  f.respond(async o=>({status:200,data:{Items:[],TotalRecordCount:new URL(o.url).searchParams.get('IncludeItemTypes')==='Movie'?27:9}}));
  const r=await f.request('/jellyfin/counts');assert.equal(r.data.movies,27);assert.equal(r.data.series,9);
  f.respond(async()=>({status:200,data:{Items:[]}}));await assert.rejects(f.request('/jellyfin/counts'));
});
test('Jellyfin episode resolution reads the parent provider IDs without title guessing',async()=>{
  const f=await fixture();await f.request('/jellyfin',{method:'put',data:{url:'https://jellyfin.example',api_key:'fixture-only',user_id:'u1'}});
  f.respond(async o=>({status:200,data:new URL(o.url).pathname.endsWith('/ep')?{Id:'ep',Type:'Episode',SeriesId:'show',ParentIndexNumber:1,IndexNumber:3,ProviderIds:{Imdb:'tt1111111'}}:{Id:'show',Type:'Series',ProviderIds:{Tmdb:'1396'}}}));
  const r=await f.request('/jellyfin/resolve-title',{params:{item_id:'ep'}});assert.equal(r.data.identity.id,'tmdb:1396');assert.equal(r.data.identity.episode,3);
});
test('trending filters preserve the real trending endpoint and rank the returned page honestly',async()=>{
  const f=await fixture();f.respond(async()=>({status:200,data:{results:[{id:1,title:'Old',release_date:'2010-01-01',genre_ids:[18],vote_average:9},{id:2,title:'Recent',release_date:'2025-01-01',genre_ids:[18],vote_average:8}],total_pages:2}}));
  const r=await f.request('/discover/tmdb',{headers:{'X-TMDb-Key':'fixture-only'},params:{type:'movie',category:'trending',genre:18,year_from:2020,sort:'vote_average.desc'}});
  assert.match(f.calls[0].url,/trending\/movie\/week/);assert.equal(r.data.items[0].name,'Recent');assert.match(r.data.filter_scope,/returned provider page/);
});
test('regional streaming provider catalogue uses official TMDb watch endpoints',async()=>{
  const f=await fixture();f.respond(async o=>({status:200,data:{results:o.url.includes('/regions')?[{iso_3166_1:'HU',english_name:'Hungary'}]:[{provider_id:8,provider_name:'Netflix',logo_path:'/n.png'}]}}));
  const r=await f.request('/discover/providers',{headers:{'X-TMDb-Key':'fixture-only'},params:{type:'series',region:'HU'}});assert.equal(r.data.providers[0].id,8);assert.equal(r.data.regions[0].id,'HU');assert.ok(f.calls.some(o=>new URL(o.url).pathname.endsWith('/watch/providers/tv') && new URL(o.url).searchParams.get('watch_region') === 'HU'));
});
test('unified Nothing theme preserves obsolete style as a migration record',async()=>{
  const f=await fixture();f.memoryStorage.setItem('dmm-interface-style','terminal');f.memoryStorage.setItem('dmm-theme','light');const theme=await f.loadModule('theme.js');
  assert.equal(theme.getInterfaceStyle(),'nothing');assert.equal(theme.getTheme(),'light');assert.equal(f.memoryStorage.getItem('dmm-interface-style-legacy'),'terminal');
});

test('streaming choices include Prime Video and use actual regional provider IDs',async()=>{
 const f=await fixture(),{streamingServices}=await f.loadModule('streamingServices.js');
 const services=streamingServices([{id:8,name:'Netflix'},{id:1796,name:'Netflix Standard with Ads'},{id:119,name:'Prime Video'},{id:1899,name:'HBO Max'}]);
 assert.deepEqual(plain(services.map(s=>s.name)),['Apple TV','Netflix','HBO Max','Disney Plus','Hulu','Prime Video','Paramount Plus']);assert.equal(services.find(s=>s.id==='netflix').providerIds,'8|1796');assert.equal(services.find(s=>s.id==='apple').available,false);assert.equal(services.find(s=>s.id==='prime').providerIds,'119');
});
test('service catalog browsing uses unranked discover pagination and real regional provider OR filters',async()=>{
 const f=await fixture();f.respond(async()=>({status:200,data:{results:[],total_pages:7}}));
 const r=await f.request('/discover/tmdb',{headers:{'X-TMDb-Key':'fixture-only'},params:{type:'series',category:'catalog',provider:'8|1796',region:'HU',page:3}});
 const url=new URL(f.calls[0].url);assert.ok(url.pathname.endsWith('/discover/tv'));assert.equal(url.searchParams.get('with_watch_providers'),'8|1796');assert.equal(url.searchParams.get('watch_region'),'HU');assert.equal(url.searchParams.get('page'),'3');assert.equal(url.searchParams.has('vote_count.gte'),false);assert.equal(r.data.total_pages,7);
});
test('compact crew keeps at most four distinct people and prioritizes actual directors and creators',async()=>{
 const f=await fixture(),{compactCrew}=await f.loadModule('titlePresentation.js');
 const crew=compactCrew([{id:1,name:'Writer',job:'Writer'},{id:1,name:'Writer',job:'Story'},...Array.from({length:6},(_,i)=>({id:i+2,name:'Person '+i,job:'Writer'})),{id:99,name:'Director',job:'Director'}]);assert.equal(crew.length,4);assert.equal(crew[0].id,99);assert.equal(crew.find(p=>p.id===1).job,'Writer · Story');
});

test('trending service filters accept any actual plan ID of the selected brand',async()=>{
 const f=await fixture();f.respond(async o=>({status:200,data:new URL(o.url).pathname.endsWith('/watch/providers')?{results:{HU:{flatrate:[{provider_id:1796}]}}}:{results:[{id:603,title:'Included title',vote_count:20}],total_pages:1}}));
 const r=await f.request('/discover/tmdb',{headers:{'X-TMDb-Key':'fixture-only'},params:{type:'movie',category:'trending',provider:'8|1796',region:'HU'}});assert.equal(r.data.items.length,1);
});
test('Jellyfin library search, watch filter and sorting reach the real Items API',async()=>{
  const f=await fixture();await f.request('/jellyfin',{method:'put',data:{url:'https://jellyfin.example',api_key:'fixture-only',user_id:'u1'}});
  f.respond(async()=>({status:200,data:{Items:[],TotalRecordCount:0}}));
  await f.request('/jellyfin/library-items',{params:{parent_id:'films',search:'  The Matrix  ',filter:'unwatched',sort:'recent',limit:36}});
  const url=new URL(f.calls.at(-1).url);
  assert.equal(url.searchParams.get('SearchTerm'),'The Matrix');assert.equal(url.searchParams.get('IsPlayed'),'false');assert.equal(url.searchParams.get('SortBy'),'DateCreated');assert.equal(url.searchParams.get('SortOrder'),'Descending');assert.equal(url.searchParams.get('Limit'),'36');assert.equal(url.searchParams.get('EnableTotalRecordCount'),'true');
});
test('collection search expands MCU and uses actual TMDb collection and keyword endpoints',async()=>{
  const f=await fixture();f.respond(async o=>({status:200,data:{results:[{id:new URL(o.url).pathname.endsWith('keyword')?55:77,name:'Marvel Cinematic Universe'}],page:1,total_pages:2}}));
  const r=await f.request('/discover/collections',{headers:{'X-TMDb-Key':'fixture-only'},params:{q:'mcu'}});
  assert.equal(r.data.items.length,2);assert.deepEqual(plain(r.data.items.map(x=>x.kind)),['collection','keyword']);
  assert.ok(f.calls.every(x=>new URL(x.url).searchParams.get('query')==='Marvel Cinematic Universe'));
  assert.deepEqual(f.calls.map(x=>new URL(x.url).pathname).sort(),['/3/search/collection','/3/search/keyword']);
});
test('universe catalogs paginate real movie or TV keyword discovery without invented membership',async()=>{
  const f=await fixture();f.respond(async()=>({status:200,data:{results:[{id:1396,name:'A show'}],page:2,total_pages:3}}));
  const r=await f.request('/discover/collection-items',{headers:{'X-TMDb-Key':'fixture-only'},params:{id:55,kind:'keyword',type:'series',page:2}});
  const url=new URL(f.calls[0].url);assert.equal(url.pathname,'/3/discover/tv');assert.equal(url.searchParams.get('with_keywords'),'55');assert.equal(url.searchParams.get('page'),'2');assert.equal(r.data.items[0].type,'series');assert.equal(r.data.total_pages,3);
  await assert.rejects(f.request('/discover/collection-items',{params:{id:'../secret',kind:'keyword'}}));
});
test('Jellyfin storage deduplicates shared files and reports missing sizes honestly',async()=>{
  const f=await fixture();await f.request('/jellyfin',{method:'put',data:{url:'https://jellyfin.example',api_key:'fixture-only',user_id:'u1'}});
  f.respond(async()=>({status:200,data:{Items:[{Id:'a',MediaSources:[{Path:'/one',Size:1000000000},{Path:'/two',Size:2000000000}]},{Id:'b',MediaSources:[{Path:'/one',Size:1000000000}]},{Id:'c',MediaSources:[{Id:'c1'}]}],TotalRecordCount:3}}));
  const r=await f.request('/jellyfin/storage');assert.equal(r.data.bytes,3000000000);assert.equal(r.data.complete,false);assert.equal(r.data.missing_sources,1);assert.ok(!JSON.stringify(r.data).includes('/one'));
});
test('Jellyfin task progress uses RefreshLibrary key and never assumes old Idle results are a new completed scan',async()=>{
  const f=await fixture();const helper=await f.loadModule('jellyfinLibraryMetrics.js');
  const old={state:'Idle',supported:true,last_result:{end_time:'2026-10-01'}};
  assert.equal(helper.scanFinished(old,old),false);assert.equal(helper.scanFinished({...old,last_result:{end_time:'2026-10-02'}},old),true);
  assert.equal(helper.scanTask([{Key:'RefreshLibrary',State:'Running',CurrentProgressPercentage:32}]).progress,32);
  assert.equal(helper.scanTask([{Key:'Other',CurrentProgressPercentage:99}]).supported,false);
  assert.equal(helper.scanTask([{Key:'RefreshLibrary',State:'Running'}]).progress,null);
});

test('v1 preferences are bounded, account isolated and do not alter legacy backup lists',async()=>{
  const f=await fixture();
  const saved=await f.request('/prefs/experience',{method:'put',headers:{'X-TorBox-Key':'account-a'},data:{section:'discovery',value:{type:'series',region:'HU',view:'services',filters:{genre:'18',provider:'netflix',token:'never-save'},hideWatched:true,api_key:'never-save'}}});
  assert.equal(saved.data.discovery.type,'series');assert.equal(saved.data.discovery.region,'HU');assert.equal(saved.data.discovery.filters.genre,'18');assert.ok(!JSON.stringify(saved.data).includes('never-save'));
  assert.deepEqual(plain((await f.request('/prefs/experience',{headers:{'X-TorBox-Key':'account-b'}})).data),{});
  await f.request('/prefs/experience',{method:'put',headers:{'X-TorBox-Key':'account-a'},data:{section:'sources',value:{res:'1080p',sort:'seeders',maxSize:'20',cachedOnly:true}}});
  const both=(await f.request('/prefs/experience',{headers:{'X-TorBox-Key':'account-a'}})).data;assert.equal(both.discovery.region,'HU');assert.equal(both.sources.sort,'seeders');
  assert.equal((await f.request('/backup',{headers:{'X-TorBox-Key':'account-a'}})).data.version,1);
  await assert.rejects(f.request('/prefs/experience',{method:'put',data:{section:'credentials',value:{}}}),/Unknown/);
});
test('saved Jellyfin pages survive an outage, omit private paths and cannot cross server users',async()=>{
  const f=await fixture();await f.request('/jellyfin',{method:'put',data:{url:'https://jellyfin.example',api_key:'fixture-only',user_id:'u1'}});
  f.respond(async()=>({status:200,data:{Items:[{Id:'a',Name:'Saved movie',Type:'Movie',Path:'/private/file',MediaSources:[{Path:'/secret'}],ProviderIds:{Imdb:'tt123'},UserData:{Played:true}}],TotalRecordCount:1}}));
  const live=await f.request('/jellyfin/library-items',{params:{parent_id:'films',limit:36}});assert.equal(live.data.offline,false);
  f.respond(async()=>({status:401,data:{detail:'must-not-echo-secret'}}));
  const saved=await f.request('/jellyfin/library-items',{params:{parent_id:'films',limit:36}});assert.equal(saved.data.offline,true);assert.equal(saved.data.items[0].Name,'Saved movie');assert.ok(saved.data.cached_at);assert.ok(!JSON.stringify(saved.data).includes('/private'));assert.ok(!JSON.stringify(saved.data).includes('must-not-echo-secret'));assert.ok(saved.data.error);
  await assert.rejects(f.request('/jellyfin/library-items',{params:{parent_id:'other',limit:36}}));
  f.respond(async()=>({status:200,data:{Id:'u2'}}));await f.request('/jellyfin',{method:'put',data:{url:'https://jellyfin.example',api_key:'fixture-other',user_id:'u2'}});
  f.respond(async()=>({status:401,data:{}}));await assert.rejects(f.request('/jellyfin/library-items',{params:{parent_id:'films',limit:36}}));
});
test('manual episode matching is local, uses the parent series and supports reverse availability',async()=>{
  const f=await fixture();await f.request('/jellyfin',{method:'put',data:{url:'https://jellyfin.example',api_key:'fixture-only',user_id:'u1'}});
  f.respond(async o=>({status:200,data:o.url.includes('/Items/episode1')?{Id:'episode1',Type:'Episode',SeriesId:'series1',ParentIndexNumber:2,IndexNumber:3}:o.url.includes('/Items/series1')?{Id:'series1',Type:'Series',Name:'Unmatched show'}:{Items:[],TotalRecordCount:0}}));
  await f.request('/jellyfin/match',{method:'put',data:{item_id:'episode1',identity:{id:'tt0903747',type:'series'}}});
  const resolved=await f.request('/jellyfin/resolve-title',{params:{item_id:'episode1'}});assert.equal(resolved.data.identity.id,'tt0903747');assert.equal(resolved.data.identity.season,2);assert.equal(resolved.data.identity.episode,3);
  const available=await f.request('/jellyfin/title-availability',{params:{imdb_id:'tt0903747',type:'series'}});assert.equal(available.data.state,'available');assert.equal(available.data.item_id,'series1');
  await assert.rejects(f.request('/jellyfin/match',{method:'put',data:{item_id:'episode1',identity:{id:'tt123',type:'movie'}}}),/matching/);
  assert.ok(f.calls.every(call=>call.method==='GET'));
});
test('bounded catalog preserves both provider IDs and does not claim incomplete negative matches',async()=>{
  const f=await fixture();await f.request('/jellyfin',{method:'put',data:{url:'https://jellyfin.example',api_key:'fixture-only',user_id:'u1'}});
  f.respond(async()=>({status:200,data:{Items:[{Id:'a',Type:'Movie',ProviderIds:{Imdb:'tt123',Tmdb:'603'}}],TotalRecordCount:999}}));
  const catalog=(await f.request('/jellyfin/catalog')).data;assert.equal(catalog.complete,false);assert.ok(catalog.items[0].ids.includes('tt123'));assert.ok(catalog.items[0].ids.includes('tmdb:603'));
  const {filterKnownTitles}=await f.loadModule('experience.js');
  const visible=filterKnownTitles([{id:'tt123',type:'movie'},{id:'tt999',type:'movie'}],new Map(),catalog,{hideLibrary:true});assert.equal(visible.length,1);assert.equal(visible[0].id,'tt999');
});
test('diagnostics report each actual failure independently without exposing provider bodies',async()=>{
  const f=await fixture();await f.request('/jellyfin',{method:'put',data:{url:'http://192.168.0.5:8096',api_key:'fixture-only',user_id:'u1'}});
  f.respond(async o=>new URL(o.url).pathname.endsWith('/Views')?{status:403,data:{detail:'secret-body'}}:{status:200,data:{}});
  const data=(await f.request('/jellyfin/diagnostics')).data;assert.equal(data.checks.length,3);assert.equal(data.checks[0].ok,true);assert.equal(data.checks[2].ok,false);assert.ok(data.transport.includes('unencrypted'));assert.ok(!JSON.stringify(data).includes('secret-body'));
});
test('scan observations include episodes without changing the existing recently-added contract',async()=>{
  const f=await fixture();await f.request('/jellyfin',{method:'put',data:{url:'https://jellyfin.example',api_key:'fixture-only',user_id:'u1'}});f.respond(async()=>({status:200,data:{Items:[]}}));
  await f.request('/jellyfin/scan-recent');assert.equal(new URL(f.calls.at(-1).url).searchParams.get('IncludeItemTypes'),'Movie,Series,Episode');
  await f.request('/jellyfin/recent');assert.equal(new URL(f.calls.at(-1).url).searchParams.get('IncludeItemTypes'),'Movie,Series');
});
test('alias matching, release order and watched filters use actual title records',async()=>{
  const f=await fixture(),{rankTitles}=await f.loadModule('searchRelevance.js'),{releaseOrder,filterKnownTitles,experienceSection}=await f.loadModule('experience.js');
  assert.equal(rankTitles([{id:'tt1',name:'Other'},{id:'tt2',name:'Localized',alternative_titles:[{title:'Exact original'}]}],'Exact original')[0].id,'tt2');
  assert.deepEqual(plain(releaseOrder([{id:'b',year:'2020'},{id:'a',year:'1999'}]).map(x=>x.id)),['a','b']);
  assert.equal(filterKnownTitles([{id:'a'},{id:'b'}],new Map([['a',{played:true}]]),null,{hideWatched:true})[0].id,'b');
  assert.equal(experienceSection('sources',{maxSize:'-1',res:'invalid',text:'private query'}).maxSize,'');assert.equal(experienceSection('sources',{}).text,undefined);
});

test('Jellyfin refresh preserves newer manual watched choices instead of overwriting with old server history',async()=>{
  const f=await fixture();await f.request('/jellyfin',{method:'put',data:{url:'https://jellyfin.example',api_key:'fixture-only',user_id:'u1'}});
  f.prefs.set('dmm.local.v1.guest.tracking',JSON.stringify([{imdb_id:'tt123',type:'movie',name:'Local choice',played:false,updated_at:'2026-10-15T15:00:00Z',episodes:[]}]));
  f.respond(async o=>({status:200,data:{Items:o.url.includes('/Resume')?[]:[{Id:'film1',Type:'Movie',Name:'Remote old choice',ProviderIds:{Imdb:'tt123'},UserData:{Played:true,LastPlayedDate:'2026-10-14T10:00:00Z'}}],TotalRecordCount:1}}));
  await f.request('/jellyfin/sync',{method:'post'});
  const state=(await f.request('/tracking/state')).data;assert.equal(state[0].played,false);assert.equal(state[0].name,'Local choice');
});

test('v2 IMDb minimum uses a verified IMDb rating and never substitutes a TMDb score',async()=>{
 const f=await fixture();f.respond(async options=>{const url=new URL(options.url);return {status:200,data:url.pathname.endsWith('/external_ids')?{imdb_id:url.pathname.includes('/2/')?'tt222':'tt111'}:url.hostname==='www.omdbapi.com'?{Response:'True',imdbRating:url.searchParams.get('i')==='tt222'?'6.0':'8.7'}:{results:[{id:1,title:'Verified',vote_average:2},{id:2,title:'Not enough IMDb',vote_average:10}],page:1,total_pages:3}};});
 const result=await f.request('/discover/tmdb',{headers:{'X-TMDb-Key':'test-only','X-OMDb-Key':'ratings-fixture'},params:{type:'movie',category:'catalog',imdb_rating:'8'}});assert.equal(result.data.items.length,1);assert.equal(result.data.items[0].name,'Verified');assert.equal(result.data.items[0].imdbRating,8.7);assert.equal(result.data.items[0].tmdbRating,null);assert.equal(result.data.total_pages,3);
});
test('v2 missing IMDb identities are excluded without fake scores',async()=>{
 const f=await fixture();f.respond(async options=>({status:200,data:new URL(options.url).pathname.endsWith('/external_ids')?{}:{results:[{id:1,title:'Unknown score',vote_average:10}]}}));const result=await f.request('/discover/tmdb',{headers:{'X-TMDb-Key':'test-only','X-OMDb-Key':'ratings-fixture'},params:{type:'movie',imdb_rating:'8'}});assert.equal(result.data.items.length,0);assert.equal(result.data.imdb_filter,true);
});
test('v2 episode metadata uses real season/episode endpoints and keeps IMDb separate from TMDb votes',async()=>{
 const f=await fixture();f.respond(async options=>({status:200,data:new URL(options.url).hostname==='www.omdbapi.com'?{Response:'True',imdbRating:'8.8'}:new URL(options.url).pathname.includes('/find/')?{tv_results:[{id:1396}]}:{name:'Second episode',overview:'Complete provider synopsis',vote_average:9.9}}));
 const result=await f.request('/meta/episode',{headers:{'X-TMDb-Key':'test-only','X-OMDb-Key':'ratings-fixture'},params:{imdb_id:'tt0903747',season:1,episode:2}});assert.equal(result.data.imdbRating,'8.8');assert.equal(result.data.overview,'Complete provider synopsis');assert.ok(f.calls.some(call=>new URL(call.url).pathname==='/3/tv/1396/season/1/episode/2'));await assert.rejects(f.request('/meta/episode',{params:{imdb_id:'tt123',season:0,episode:1}}));
});
test('v2 Jellyfin version grouping joins verified aliases but preserves remakes and unknown identities',async()=>{
 const f=await fixture(),{uniqueJellyfinTitles}=await f.loadModule('jellyfinVersions.js');
 const rows=[{Id:'a',Type:'Movie',Name:'Same',ProviderIds:{Imdb:'tt123',Tmdb:'603'}},{Id:'b',Type:'Movie',Name:'Same 4K',ProviderIds:{Tmdb:'603'},UserData:{PlaybackPositionTicks:100}},{Id:'c',Type:'Movie',Name:'Same',ProviderIds:{Imdb:'tt456'}},{Id:'d',Type:'Movie',Name:'Unknown'},{Id:'e',Type:'Movie',Name:'Unknown'}];
 const unique=uniqueJellyfinTitles(rows);assert.equal(unique.length,4);assert.equal(unique[0].Id,'b');assert.equal(unique[0].VersionCount,2);assert.equal(rows.length,5);
});


test('unified discovery preserves legacy channel selections and resolves only real providers',async()=>{
 const f=await fixture(),m=await f.loadModule('discovery.js');
 assert.equal(m.discoveryCategory('catalog'),'popular');assert.equal(m.discoveryCategory('top'),'top');
 assert.equal(m.selectedProvider('provider:119',[],[{id:119,name:'Prime Video'}]).providerIds,'119');assert.equal(m.selectedProvider('provider:999',[],[]),null);
 const chips=m.discoveryFilterChips({filters:{...m.emptyDiscoveryFilters,genre:'18',imdb_rating:'8',year_from:'2010'},hideWatched:true},[{id:18,name:'Drama'}]);assert.ok(chips.some(c=>c[1]==='Drama'));assert.ok(chips.some(c=>c[1]==='IMDb 8+'));
});
test('completion deduplicates episode coordinates, excludes Specials and separates partial playback',async()=>{
 const f=await fixture(),m=await f.loadModule('progress.js');
 const result=m.seriesCompletion([{season:0,episode:1},{season:1,episode:1},{season:1,episode:1},{season:1,episode:2},{season:2,episode:1}],{'1:1':{played:true},'1:2':{played:false,progress:99}});
 assert.equal(result.total,3);assert.equal(result.completed,1);assert.equal(result.remaining,2);assert.equal(Math.round(result.percentage),33);assert.equal(m.seriesCompletion([],{}).percentage,0);assert.equal(m.clampProgress(Infinity),0);assert.equal(m.clampProgress(120),100);
});
test('provider-filtered IMDb-ID search resolves the real TMDb identity before regional availability',async()=>{
 const f=await fixture();f.respond(async options=>{const u=new URL(options.url);return {status:200,data:u.pathname.includes('/find/')?{movie_results:[{id:603,title:'The Matrix',genre_ids:[18]}]}:{results:{HU:{flatrate:[{provider_id:119}]}}}};});
 const response=await f.request('/discover/tmdb',{headers:{'X-TMDb-Key':'test-only'},params:{category:'search',query:'tt0133093',type:'movie',provider:'119',region:'HU'}});
 assert.equal(response.data.items.length,1);assert.ok(f.calls.some(call=>new URL(call.url).pathname.endsWith('/find/tt0133093')));assert.ok(f.calls.some(call=>new URL(call.url).pathname.endsWith('/movie/603/watch/providers')));
});

test('title/year search falls back without corrupting titles that contain a year',async()=>{
 const f=await fixture(),headers={'X-TMDb-Key':'test-only'};
 f.respond(async options=>({status:200,data:{results:new URL(options.url).searchParams.get('query')==='Blade Runner 2049'?[{id:1,title:'Blade Runner 2049',release_date:'2017-01-01'}]:new URL(options.url).searchParams.get('query')==='Dune'?[{id:2,title:'Dune',release_date:'2021-01-01'},{id:3,title:'Dune',release_date:'1984-01-01'}]:[],total_pages:1}}));
 const blade=await f.request('/discover/tmdb',{headers,params:{type:'movie',category:'search',query:'Blade Runner 2049'}});assert.equal(blade.data.items.length,1);
 const dune=await f.request('/discover/tmdb',{headers,params:{type:'movie',category:'search',query:'Dune 2021'}});assert.equal(dune.data.items.length,1);assert.equal(dune.data.items[0].name,'Dune');assert.ok(f.calls.some(call=>new URL(call.url).searchParams.get('query')==='Dune'));
});

// Metadata v2.4 provider contracts. All responses below are explicit test fixtures,
// never production fallbacks or evidence of a live provider connection.
const metadataHeaders={'X-TMDb-Key':'metadata-test-key'};
function metadataResponse(options){const u=new URL(options.url),p=u.pathname;return {status:200,data:p.includes('/find/')?{tv_results:[{id:1396}]}:p.endsWith('/external_ids')?{id:1396,imdb_id:'tt0903747',tvdb_id:81189}:p.endsWith('/season/1')?{episodes:[{id:1,season_number:1,episode_number:1,name:'TMDb episode',overview:'TMDb episode overview',air_date:'2026-01-01',still_path:'/still.jpg'}]}:{id:1396,name:'TMDb show',overview:'TMDb overview',first_air_date:'2008-01-20',episode_run_time:[50],seasons:[{season_number:1}],poster_path:'/poster.jpg',backdrop_path:'/back.jpg',genres:[{name:'Drama'}],vote_average:8,vote_count:100,external_ids:{imdb_id:'tt0903747',tvdb_id:81189}}};}
test('identity resolves IMDb, typed TMDb and TVDB to the same canonical IMDb ID, caches 30 days',async()=>{
 const f=await fixture();f.respond(async o=>metadataResponse(o));const resolver=await f.loadModule('metadata/resolveIdentity.js'),context={scope:'guest',credentials:{tmdb:'identity-fixture'},settings:{language:'hu-HU'}};
 for(const id of ['tt0903747','tmdb:1396','tvdb:81189']){const value=await resolver.resolveIdentity({id,type:'series'},context);assert.deepEqual(plain(value),{imdb_id:'tt0903747',tmdb_id:1396,tvdb_id:81189,type:'series'});}
 const count=f.calls.length;await resolver.resolveIdentity({id:'tvdb:81189',type:'series'},context);assert.equal(f.calls.length,count);const cache=await f.loadModule('metadata/cache.js');assert.equal((await cache.cacheGet('identity:series:tvdb:81189')).imdb_id,'tt0903747');
});
test('untyped TMDb identity resolves only an unambiguous media type',async()=>{
 const f=await fixture();f.respond(async o=>new URL(o.url).pathname.includes('/movie/')?{status:404,data:{}}:{status:200,data:{id:1396,imdb_id:'tt0903747',tvdb_id:81189}});
 const r=(await f.request('/metadata/identity',{headers:metadataHeaders,params:{id:'tmdb:1396'}})).data;assert.equal(r.type,'series');assert.equal(r.imdb_id,'tt0903747');
});
test('legacy Hungarian metadata preferences normalize to English without resetting source or rating choices',async()=>{
 const f=await fixture(),settings={search:'cinemeta',movie:'cinemeta',series:'cinemeta',ratings:false,imdbOnLists:true,language:'hu-HU'};
 f.prefs.set('dmm.local.v1.guest.metadata',JSON.stringify(settings));const data=(await f.request('/metadata/settings')).data;
 assert.deepEqual(JSON.parse(JSON.stringify(data.settings)),{...settings,search:'tmdb',language:'en-US'});assert.equal(f.calls.length,0);
 const saved=(await f.request('/metadata/settings',{method:'put',data:{...settings,language:'fr-FR'}})).data;
 assert.equal(saved.settings.language,'en-US');assert.equal(JSON.parse(f.prefs.get('dmm.local.v1.guest.metadata')).language,'en-US');assert.equal(saved.settings.movie,'cinemeta');
});
test('TMDb requests force English even with a legacy context and caller language override',async()=>{
 const f=await fixture();f.respond(async()=>({status:200,data:{title:'English title'}}));const provider=await f.loadModule('providers/tmdb.js');
 await provider.tmdbRequest('/movie/603',{credentials:{tmdb:'test-only-english-key'},settings:{language:'hu-HU'}},{params:{language:'hu-HU',with_original_language:'ko'}});
 const url=new URL(f.calls[0].url);assert.equal(url.searchParams.get('language'),'en-US');assert.equal(url.searchParams.get('with_original_language'),'ko');assert.equal(f.calls.length,1);
});
test('title logos prefer English then neutral artwork and never select a foreign-language logo',async()=>{
 const f=await fixture();const logos=[{iso_639_1:'hu',file_path:'/hungarian.png'},{iso_639_1:null,file_path:'/neutral.png'},{iso_639_1:'en',file_path:'/english.png'}];
 f.respond(async o=>o.url.includes('/images')?{status:200,data:{logos}}:metadataResponse(o));const request=()=>f.request('/meta/title-logo',{headers:{'X-TMDb-Key':'test-only-logo-key'},params:{imdb_id:'tt0903747',type:'series'}});
 assert.ok((await request()).data.url.endsWith('/english.png'));assert.equal(new URL(f.calls.find(c=>c.url.includes('/images')).url).searchParams.get('include_image_language'),'en,null');
 const g=await fixture();g.respond(async o=>o.url.includes('/images')?{status:200,data:{logos:logos.slice(0,2)}}:metadataResponse(o));assert.ok((await g.request('/meta/title-logo',{headers:{'X-TMDb-Key':'test-only-logo-key'},params:{imdb_id:'tt0903747',type:'series'}})).data.url.endsWith('/neutral.png'));
 const h=await fixture();h.respond(async o=>o.url.includes('/images')?{status:200,data:{logos:logos.slice(0,1)}}:metadataResponse(o));assert.equal((await h.request('/meta/title-logo',{headers:{'X-TMDb-Key':'test-only-logo-key'},params:{imdb_id:'tt0903747',type:'series'}})).data.url,null);
});
test('TMDb-only unified search dedupes canonical identities, excludes people, uses English language and Bearer auth',async()=>{
 const f=await fixture();f.respond(async o=>{const u=new URL(o.url);return {status:200,data:u.pathname.endsWith('/external_ids')?{imdb_id:'tt0133093'}:{results:[{id:603,media_type:'movie',title:'The Matrix',overview:'English synopsis',vote_count:100,vote_average:8},{id:603,media_type:'movie',title:'The Matrix',overview:'English synopsis'},{id:12,media_type:'person',name:'Actor'}],total_pages:1}};});
 const data=(await f.request('/metadata/search',{headers:{'X-TMDb-Key':'header.payload.signature'},params:{q:'matrix'}})).data;assert.equal(data.source,'tmdb');assert.equal(data.items.length,1);assert.equal(data.items[0].name,'The Matrix');assert.equal(data.items[0].overview,'English synopsis');assert.equal(data.items[0].id,'tt0133093');assert.equal(data.items[0].tmdb_id,603);assert.equal(data.items[0].tmdbRating,8);assert.ok(f.calls.every(c=>new URL(c.url).hostname==='api.themoviedb.org'));for(const c of f.calls){assert.equal(new URL(c.url).searchParams.has('api_key'),false);assert.equal(c.headers.Authorization,'Bearer header.payload.signature');}assert.equal(new URL(f.calls[0].url).pathname,'/3/search/multi');assert.equal(new URL(f.calls[0].url).searchParams.get('language'),'en-US');
});
test('search falls back to Cinemeta only without a TMDb key and ignores legacy source preferences',async()=>{
 const f=await fixture();f.respond(async o=>({status:200,data:new URL(o.url).hostname.includes('cinemeta')?{metas:[{id:'tt0133093',name:'Matrix',year:'1999'}]}:{}}));
 let data=(await f.request('/metadata/search',{params:{q:'matrix'}})).data;assert.equal(data.source,'cinemeta');assert.ok(data.notices.some(n=>n.includes('TMDb key missing')));
 await f.request('/metadata/settings',{method:'put',data:{search:'cinemeta'}});f.calls.length=0;data=(await f.request('/metadata/search',{headers:metadataHeaders,params:{q:'matrix'}})).data;assert.equal(data.source,'tmdb');assert.ok(f.calls.every(c=>new URL(c.url).hostname==='api.themoviedb.org'));
});
test('movie and series sources are independent, TMDb fallback is visible, no IMDb title stays visible',async()=>{
 const f=await fixture();f.respond(async o=>metadataResponse(o));const show=(await f.request('/meta/series/tt0903747',{headers:metadataHeaders})).data;assert.equal(show.metadata_source,'tmdb');assert.equal(show.notices.length,0);assert.equal(show.id,'tt0903747');
 await f.request('/metadata/settings',{method:'put',data:{movie:'cinemeta',series:'tmdb'}});f.respond(async o=>new URL(o.url).hostname.includes('cinemeta')?{status:200,data:{meta:{id:'tt0133093',name:'Cinemeta movie'}}}:metadataResponse(o));assert.equal((await f.request('/meta/movie/tt0133093',{headers:metadataHeaders})).data.metadata_source,'cinemeta');
 const other=await fixture();other.respond(async o=>({status:200,data:new URL(o.url).pathname.endsWith('/external_ids')?{}:{id:44,title:'Unmapped movie',overview:'Visible title'}}));const unknown=(await other.request('/meta/movie/tmdb:44',{headers:metadataHeaders})).data;assert.equal(unknown.name,'Unmapped movie');assert.equal(unknown.imdb_id,null);assert.equal(unknown.stream_lookup_available,false);await assert.rejects(other.request('/streams/movie/tmdb:44',{headers:metadataHeaders}),/IMDb/);
});
test('OMDb quota errors are persistent cooldowns, not immediate retries; valid ratings cache for 24 hours',async()=>{
 const f=await fixture();f.respond(async()=>({status:200,data:{Response:'False',Error:'Request limit reached!'}}));const headers={'X-OMDb-Key':'quota-test'};await assert.rejects(f.request('/ratings/tt0133093',{headers}),e=>e.response.status===429);await assert.rejects(f.request('/ratings/tt0903747',{headers}),e=>e.response.status===429);assert.equal(f.calls.length,1);
 const good=await fixture();good.respond(async()=>({status:200,data:{Response:'True',imdbRating:'8.7',Metascore:'73',Ratings:[{Source:'Rotten Tomatoes',Value:'83%'}]}}));assert.equal((await good.request('/ratings/tt0133093',{headers})).data.imdb,'8.7');await good.request('/ratings/tt0133093',{headers});assert.equal(good.calls.length,1);
});
test('disabled OMDb makes no rating request and IMDb filter requires explicit enabled credentials',async()=>{
 const f=await fixture();await f.request('/metadata/settings',{method:'put',data:{ratings:false}});assert.equal((await f.request('/ratings/tt0133093',{headers:{'X-OMDb-Key':'unused'}})).data.imdb,null);assert.equal(f.calls.length,0);
 f.respond(async()=>({status:200,data:{results:[]}}));await assert.rejects(f.request('/discover/tmdb',{headers:metadataHeaders,params:{imdb_rating:8}}),/Enable OMDb/);
});
test('metadata preferences and secure credentials remain account scoped; malformed preferences recover',async()=>{
 const f=await fixture(),a={'X-TorBox-Key':'account-A'},b={'X-TorBox-Key':'account-B'};await f.request('/metadata/settings',{method:'put',headers:a,data:{language:'en-US',search:'cinemeta',ratings:false,imdbOnLists:true}});await f.request('/metadata/credentials',{method:'put',headers:a,data:{provider:'omdb',value:'private-test-key'}});const first=(await f.request('/metadata/settings',{headers:a})).data,second=(await f.request('/metadata/settings',{headers:b})).data;assert.equal(first.settings.search,'tmdb');assert.equal(first.status.omdb,'set');assert.equal(second.settings.search,'tmdb');assert.equal(second.status.omdb,'missing');assert.equal(second.settings.language,'en-US');assert.equal(second.settings.imdbOnLists,false);assert.ok([...f.prefs.values()].every(v=>!v.includes('private-test-key')));f.prefs.set('dmm.local.v1.guest.metadata','{bad');assert.equal((await f.request('/metadata/settings')).data.settings.movie,'tmdb');
});
test('metadata cache TTL expiry and four-request transport concurrency are enforced',async()=>{
 const f=await fixture(),cache=await f.loadModule('metadata/cache.js');await cache.cacheSet('expired-fixture',{id:1},-1);assert.equal(await cache.cacheGet('expired-fixture'),null);let active=0,maximum=0;f.respond(async()=>{active++;maximum=Math.max(maximum,active);await new Promise(r=>setTimeout(r,2));active--;return {status:200,data:{ok:true}};});await Promise.all(Array.from({length:12},(_,i)=>f.transport.externalRequest(`https://concurrency.example/${i}`)));assert.equal(maximum,4);
});

test('Cinemeta browsing keeps the IMDb filter verified through OMDb and reports missing credentials',async()=>{
 const f=await fixture();await f.request('/metadata/settings',{method:'put',data:{search:'cinemeta'}});f.respond(async o=>({status:200,data:new URL(o.url).hostname==='www.omdbapi.com'?{Response:'True',imdbRating:'8.5'}:{metas:[{id:'tt0133093',name:'Matrix',releaseInfo:'1999',imdbRating:'2.0'}]}}));
 await assert.rejects(f.request('/metadata/search',{params:{imdb_rating:8}}),/Enable OMDb/);const data=(await f.request('/metadata/search',{headers:{'X-OMDb-Key':'browse-ratings-fixture'},params:{imdb_rating:8}})).data;assert.equal(data.items.length,1);assert.equal(data.items[0].imdbRating,8.5);assert.equal(data.source,'cinemeta');
});

test('withdrawn provider settings safely use TMDb without contacting or accepting TVDB credentials',async()=>{
 const f=await fixture();f.prefs.set('dmm.local.v1.guest.metadata',JSON.stringify({series:'tvdb',seasonOrder:'dvd',language:'en-US',ratings:false}));const data=(await f.request('/metadata/settings')).data;assert.equal(data.settings.series,'tmdb');assert.equal(data.settings.language,'en-US');assert.equal(data.settings.ratings,false);assert.equal('seasonOrder' in data.settings,false);assert.equal('tvdb' in data.status,false);await assert.rejects(f.request('/metadata/credentials',{method:'put',data:{provider:'tvdb',value:'test-only-rejected'}}),/Unknown/);assert.equal(f.calls.length,0);
});

test('month calendar fetches the historical matching season only and preserves separate same-day episodes',async()=>{
 const f=await fixture();await f.request('/watchlist',{method:'post',data:{imdb_id:'tt0133093',type:'series',name:'Historical series'}});
 f.respond(async options=>{const path=new URL(options.url).pathname;return {status:200,data:path.includes('/find/')?{tv_results:[{id:123}]}:path.endsWith('/external_ids')?{imdb_id:'tt0133093'}:path.includes('/season/')?{episodes:[{air_date:'2005-10-19',season_number:1,episode_number:10,name:'Ten'},{air_date:'2005-10-19',season_number:1,episode_number:9,name:'Nine'}]}:{name:'Historical series',seasons:[{season_number:1,air_date:'2005-10-01'},{season_number:20,air_date:'2026-10-01'}],next_episode_to_air:{air_date:'2026-10-19',season_number:20,episode_number:1}}};});
 const response=await f.request('/calendar',{headers:{'X-TMDb-Key':'fixture'},params:{scope:'following',start:'2005-10-01',end:'2005-11-11'}});
 assert.equal(response.data.range_start,'2005-10-01');assert.equal(response.data.range_end,'2005-11-11');assert.deepEqual(Array.from(response.data.events,e=>e.episode),[9,10]);
 const seasons=f.calls.filter(call=>new URL(call.url).pathname.includes('/season/'));assert.equal(seasons.length,1);assert.ok(seasons[0].url.includes('/season/1'));
});
test('calendar rejects invalid or oversized month requests before any metadata requests',async()=>{
 const f=await fixture();for(const params of [{start:'2026-02-31',end:'2026-03-03'},{start:'2026-01-01',end:'2026-12-31'},{start:'2026-03-01',end:'2026-02-01'}])await assert.rejects(f.request('/calendar',{params}),/Calendar range/);
 assert.equal(f.calls.length,0);
});
test('Tracking preferences remain isolated between accounts and old backup format is unchanged',async()=>{
 const f=await fixture();await f.request('/prefs/experience',{method:'put',headers:{'X-TorBox-Key':'account-a'},data:{section:'tracking',value:{view:'calendar',date:'2026-10-19',calendarView:'agenda',weekStart:1}}});
 assert.equal((await f.request('/prefs/experience',{headers:{'X-TorBox-Key':'account-a'}})).data.tracking.view,'calendar');
 assert.equal((await f.request('/prefs/experience',{headers:{'X-TorBox-Key':'account-b'}})).data.tracking,undefined);
 assert.equal((await f.request('/backup',{headers:{'X-TorBox-Key':'account-a'}})).data.version,1);
});

test('collection view preferences are bounded, account scoped and retain v1 backup compatibility',async()=>{
 const f=await fixture();
 for(const [section,value] of [['watchlist',{layout:'list',sort:'name',hideWatched:true,search:'do-not-persist',api_key:'do-not-persist'}],['torbox',{tab:'error',sort:'size',selected:[1]}]]){
  const result=await f.request('/prefs/experience',{method:'put',headers:{'X-TorBox-Key':'collection-a'},data:{section,value}});
  assert.ok(!JSON.stringify(result.data).includes('do-not-persist'));assert.equal(result.data[section].selected,undefined);
 }
 assert.equal((await f.request('/prefs/experience',{headers:{'X-TorBox-Key':'collection-a'}})).data.watchlist.layout,'list');
 assert.deepEqual(plain((await f.request('/prefs/experience',{headers:{'X-TorBox-Key':'collection-b'}})).data),{});
 assert.equal((await f.request('/backup',{headers:{'X-TorBox-Key':'collection-a'}})).data.version,1);
});

test('Jellyfin episode pagination includes watched items beyond the first page without writing local state',async()=>{
 const f=await fixture();await f.request('/jellyfin',{method:'put',data:{url:'https://jellyfin.example',api_key:'fixture-only',user_id:'u1'}});
 f.respond(async options=>{const u=new URL(options.url);return {status:200,data:u.pathname.includes('/Episodes')?{Items:[{Id:u.searchParams.get('StartIndex')==='0'?'ep1':'ep2',ParentIndexNumber:1,IndexNumber:u.searchParams.get('StartIndex')==='0'?1:2,UserData:{Played:true}}],TotalRecordCount:2}:{Items:[{Id:'show',Type:'Series',ProviderIds:{Imdb:'tt123'},UserData:{Played:true}}],TotalRecordCount:1}};});
 const before=(await f.request('/tracking/title/tt123')).data;
 const response=(await f.request('/jellyfin/title-availability',{params:{imdb_id:'tt123',type:'series',season:1,episode:2}})).data;
 assert.equal(response.episodes.length,2);assert.equal(response.episodes_complete,true);assert.equal(response.playback.played,true);assert.equal(response.title_state.played,true);
 assert.deepEqual(plain((await f.request('/tracking/title/tt123')).data),plain(before));
});
test('Jellyfin movie played state does not require RunTimeTicks and episode failure retains series-level state',async()=>{
 const f=await fixture();await f.request('/jellyfin',{method:'put',data:{url:'https://jellyfin.example',api_key:'fixture-only',user_id:'u1'}});
 f.respond(async()=>({status:200,data:{Items:[{Id:'movie',Type:'Movie',ProviderIds:{Imdb:'tt123'},UserData:{Played:true}}],TotalRecordCount:1}}));
 assert.equal((await f.request('/jellyfin/title-availability',{params:{imdb_id:'tt123',type:'movie'}})).data.playback.played,true);
 f.respond(async options=>new URL(options.url).pathname.includes('/Episodes')?{status:403,data:{}}:{status:200,data:{Items:[{Id:'show',Type:'Series',ProviderIds:{Imdb:'tt123'},UserData:{Played:true}}],TotalRecordCount:1}});
 const response=(await f.request('/jellyfin/title-availability',{params:{imdb_id:'tt123',type:'series',season:1,episode:1}})).data;
 assert.equal(response.state,'available');assert.equal(response.title_state.played,true);assert.equal(response.episodes_complete,false);assert.ok(response.episodes_error);assert.equal(response.episode_available,null);
});
test('source recommendation rejects a cached prologue and preserves matching movie results',async()=>{
 const f=await fixture(),match=await f.loadModule('releaseMatch.js'),score=await f.loadModule('score.js');
 const context={meta:{name:'The Odyssey',year:'2026',type:'movie'}},clip={hash:'clip',name:'The Odyssey Prologue 2025 DCP ProRes 4K',resolution:'4K'},film={hash:'film',name:'The Odyssey 2026 1080p WEB-DL',resolution:'1080p'};
 assert.equal(match.releaseMatch(clip,context).status,'conflict');assert.equal(score.bestHash([clip,film],new Set(['clip','film']),false,context),'film');
});

test('manual Jellyfin identity matches also target real episode watched writes and preserve per-episode sync state',async()=>{
 const f=await fixture();await f.request('/jellyfin',{method:'put',data:{url:'https://jellyfin.example',api_key:'fixture-only',user_id:'u1'}});
 f.respond(async options=>{const path=new URL(options.url).pathname;return {status:200,data:path.endsWith('/Items/show')?{Id:'show',Type:'Series',Name:'Unmatched'}:path.endsWith('/Episodes')?{Items:[{Id:'ep',ParentIndexNumber:1,IndexNumber:1}],TotalRecordCount:1}:{}};});
 await f.request('/jellyfin/match',{method:'put',data:{item_id:'show',identity:{id:'tt123',type:'series'}}});
 const response=await f.request('/tracking/mark',{method:'post',data:{imdb_id:'tt123',type:'series',played:true,episodes:[[1,1]]}});
 assert.equal(response.data.jellyfin_updated,1);assert.ok(f.calls.some(call=>call.method==='POST' && new URL(call.url).pathname.endsWith('/PlayedItems/ep')));
 const episode=(await f.request('/tracking/title/tt123')).data.episodes[0];assert.ok(episode.updated_at);assert.equal(episode.sync_pending,false);assert.ok(episode.synced_at);
 const backup=(await f.request('/backup')).data;assert.equal(backup.version,1);assert.equal(backup.tracking[0].episodes[0].source,'manual');
});

test('Jellyfin catalog carries real watched flags and server-scoped offline snapshots without overwriting manual tracking',async()=>{
 const f=await fixture();await f.request('/jellyfin',{method:'put',data:{url:'https://jellyfin.example',api_key:'fixture-only',user_id:'u1'}});
 f.respond(async()=>({status:200,data:{Items:[{Id:'show',Type:'Series',ProviderIds:{Imdb:'tt123'},UserData:{Played:true}}],TotalRecordCount:1}}));
 const before=plain((await f.request('/tracking/title/tt123')).data),catalog=(await f.request('/jellyfin/catalog')).data;
 assert.equal(catalog.items[0].played,true);assert.ok(f.calls.some(o=>new URL(o.url).searchParams.get('EnableUserData')==='true'));
 f.respond(async()=>({status:503,data:{}}));const cached=(await f.request('/jellyfin/catalog')).data;assert.equal(cached.offline,true);assert.equal(cached.items[0].played,true);
 assert.deepEqual(plain((await f.request('/tracking/title/tt123')).data),before);
 const {effectiveEpisodes,effectiveTracking}=await f.loadModule('watchedState.js');const meta={videos:[{season:1,episode:1},{season:1,episode:2}]};
 assert.equal(Object.values(effectiveEpisodes({item:null,episodes:[]},{state:'available',title_state:{played:true},episodes:[]},meta)).filter(e=>e.played).length,2);
 assert.equal(effectiveTracking([],cached).get('tt123').played,true);
});

test('movie watched reconciliation preserves explicit unwatch and pending edits while rejecting stale legacy false flags',async()=>{
 const f=await fixture(),{effectiveTitleWatched}=await f.loadModule('watchedState.js'),remote={played:true};
 assert.equal(effectiveTitleWatched({played:false,updated_at:'2026-10-19'},remote).played,true);
 assert.equal(effectiveTitleWatched({played:false,source:'manual',updated_at:'2026-10-19',synced_at:'2026-10-19'},remote).played,false);
 assert.equal(effectiveTitleWatched({played:false,sync_pending:true},remote).played,false);
});

test('top-rated catalogs exclude future, undated and low-vote titles and request eligible releases',async()=>{
 const f=await fixture();f.respond(async()=>({status:200,data:{results:[{id:1,title:'Released',release_date:'2000-01-01',vote_count:201,vote_average:8},{id:2,title:'Future',release_date:'2099-01-01',vote_count:1000,vote_average:10},{id:3,title:'Few votes',release_date:'2000-01-01',vote_count:2,vote_average:10},{id:4,title:'Undated',vote_count:1000,vote_average:10}],page:1,total_pages:1}}));
 const response=await f.request('/discover/tmdb',{headers:{'X-TMDb-Key':'fixture-only'},params:{type:'movie',category:'top'}});
 assert.equal(response.data.items.length,1);assert.equal(response.data.items[0].name,'Released');
 const url=new URL(f.calls[0].url);assert.equal(url.pathname,'/3/discover/movie');assert.equal(url.searchParams.get('vote_count.gte'),'200');assert.equal(url.searchParams.get('primary_release_date.lte'),new Date().toISOString().slice(0,10));
});
test('calendar artwork comes from already-fetched metadata without extra artwork requests',async()=>{
 const f=await fixture(),today=new Date().toISOString().slice(0,10);await f.request('/watchlist',{method:'post',data:{imdb_id:'tt0133093',type:'movie',name:'Artwork fixture'}});
 f.respond(async options=>({status:200,data:new URL(options.url).pathname.includes('/find/')?{movie_results:[{id:603}]}:{title:'Artwork fixture',release_date:today,poster_path:'/poster.jpg',backdrop_path:'/backdrop.jpg'}}));
 const response=await f.request('/calendar',{headers:{'X-TMDb-Key':'fixture-only'},params:{scope:'following',days:7}});
 assert.equal(response.data.events.length,1);assert.equal(response.data.events[0].background,'https://image.tmdb.org/t/p/w500/backdrop.jpg');assert.equal(f.calls.some(call=>call.url.includes('/images')),false);
});

test('manual watched dates, per-episode release dates and backup restore preserve chosen history days',async()=>{
 const f=await fixture();
 await f.request('/tracking/mark',{method:'post',data:{imdb_id:'tt123',type:'movie',name:'Dated movie',played:true,watched_on:'2020-02-03'}});
 await f.request('/tracking/mark',{method:'post',data:{imdb_id:'tt456',type:'series',name:'Dated show',played:true,episodes:[[1,1],[1,2]],watched_dates:[{season:1,episode:1,date:'2020-03-01'},{season:1,episode:2,date:'2020-03-08'}]}});
 const backup=(await f.request('/backup')).data;
 assert.match(backup.tracking.find(row=>row.imdb_id==='tt123').last_played,/^2020-02-03/);
 assert.deepEqual(plain(backup.tracking.find(row=>row.imdb_id==='tt456').episodes.map(row=>row.last_played.slice(0,10))),['2020-03-01','2020-03-08']);
 await f.request('/backup',{method:'post',headers:{'X-TorBox-Key':'date-restore'},data:backup});
 const restored=(await f.request('/tracking/history',{headers:{'X-TorBox-Key':'date-restore'}})).data;
 assert.equal(restored.recent.length,3);assert.ok(restored.recent.every(row=>row.last_played.startsWith('2020-')));
 const before=JSON.stringify((await f.request('/tracking/state')).data);
 await assert.rejects(f.request('/tracking/mark',{method:'post',data:{imdb_id:'tt123',played:true,watched_on:'2026-02-31'}}));
 await assert.rejects(f.request('/tracking/mark',{method:'post',data:{imdb_id:'tt123',played:true,watched_on:'2099-01-01'}}));
 assert.equal(JSON.stringify((await f.request('/tracking/state')).data),before);
});
test('chosen watched date is sent to Jellyfin DatePlayed without losing the local date',async()=>{
 const f=await fixture();await f.request('/jellyfin',{method:'put',data:{url:'https://jellyfin.example',api_key:'fixture-only',user_id:'u1'}});
 f.respond(async options=>({status:200,data:new URL(options.url).pathname.endsWith('/Items')?{Items:[{Id:'film',Type:'Movie',ProviderIds:{Imdb:'tt123'}}],TotalRecordCount:1}:{}}));
 await f.request('/tracking/mark',{method:'post',data:{imdb_id:'tt123',type:'movie',played:true,watched_on:'2020-01-02'}});
 const call=f.calls.find(call=>call.method==='POST' && call.url.includes('/PlayedItems/'));
 assert.match(new URL(call.url).searchParams.get('DatePlayed'),/^2020-01-02/);assert.match((await f.request('/tracking/state')).data[0].last_played,/^2020-01-02/);
});
test('Jellyfin history merges manual titles without duplicates and falls back on failure',async()=>{
 const f=await fixture();await f.request('/tracking/mark',{method:'post',data:{imdb_id:'tt1',type:'movie',name:'Local',played:true,watched_on:'2020-01-01'}});
 await f.request('/jellyfin',{method:'put',data:{url:'https://jellyfin.example',api_key:'fixture-only',user_id:'u1'}});
 f.respond(async options=>({status:200,data:{Items:options.url.includes('/Resume')?[]:[{Type:'Movie',Name:'Remote',ProviderIds:{Imdb:'tt2'},UserData:{Played:true,LastPlayedDate:'2020-02-01'}}]}}));
 const merged=(await f.request('/tracking/history')).data;assert.equal(merged.recent.length,2);
 f.respond(async()=>({status:401,data:{}}));const fallback=(await f.request('/tracking/history')).data;assert.equal(fallback.local_only,true);assert.equal(fallback.recent[0].name,'Local');assert.ok(fallback.sync_error);
});
test('TorBox cache metadata retains verified total sizes instead of episode estimates',async()=>{
 const f=await fixture(),hash='a'.repeat(40);f.respond(async()=>({status:200,data:{success:true,data:[{hash,size:9999,files:[{size:123}]}]}}));
 const response=await f.request('/torbox/cached',{method:'post',headers:{'X-TorBox-Key':'fixture-only'},data:{hashes:[hash],include_details:true}});
 assert.equal(response.data.torrent_sizes[hash],9999);assert.equal(new URL(f.calls[0].url).searchParams.get('list_files'),'true');
});
test('series completion and known totals survive tracking reads and backup, and undo clears completion',async()=>{
 const f=await fixture();const mark=data=>f.request('/tracking/mark',{method:'post',data:{imdb_id:'tt123',type:'series',name:'Known show',total_episodes:2,...data}});
 await mark({played:true,episodes:[[1,1],[1,2]],watched_on:'2020-01-01'});
 let state=(await f.request('/tracking/state')).data[0];assert.equal(state.played,true);assert.equal(state.total_episodes,2);
 assert.equal((await f.request('/backup')).data.tracking[0].total_episodes,2);
 await mark({played:false,episodes:[[1,2]]});state=(await f.request('/tracking/state')).data[0];assert.equal(state.played,false);
});
