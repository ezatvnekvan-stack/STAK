import { CapacitorHttp } from '@capacitor/core';
import { validateProviderURL, parseJSONResponse } from './validation';

const metadataCache = new Map();
const metadataPending = new Map();
const metadataHosts = new Set(['api.themoviedb.org', 'www.omdbapi.com', 'v3-cinemeta.strem.io']);
const header = (headers, name) => Object.entries(headers || {}).find(([key]) => key.toLowerCase() === name.toLowerCase())?.[1];
const redirects = new Set([301, 302, 303, 307, 308]);

// Follow public catalog redirects explicitly. Never forward a token, cookie,
// query credential or mutation body to a different origin.
export function redirectTarget(current, location, headers = {}, method = 'GET') {
  if (!location) throw new Error('The provider redirected without a destination.');
  const from = new URL(current), target = new URL(validateProviderURL(new URL(location, from).toString()));
  if (from.protocol === 'https:' && target.protocol !== 'https:') throw new Error('An insecure provider redirect was blocked.');
  const protectedRequest = Object.keys(headers).some(key => /authorization|cookie|token|api.?key/i.test(key))
    || [...from.searchParams.keys(), ...target.searchParams.keys()].some(key => /^(api.?key|token|access_token|password|pw)$/i.test(key));
  if (target.origin !== from.origin && (protectedRequest || method.toUpperCase() !== 'GET')) {
    throw new Error('The server redirected to another address. Update the connection URL in Settings; credentials were not forwarded.');
  }
  target.hash = '';
  return target.toString();
}
const cancelled = () => { const e = new Error('Request cancelled'); e.code = 'ERR_CANCELED'; return e; };
function wait(ms, signal) {
  return new Promise((resolve, reject) => {
    if (signal?.aborted) return reject(cancelled());
    const abort = () => { clearTimeout(timer); reject(cancelled()); };
    const timer = setTimeout(() => { signal?.removeEventListener('abort', abort); resolve(); }, ms);
    signal?.addEventListener('abort', abort, { once: true });
  });
}
// Native requests cannot be physically cancelled; race the signal and discard obsolete results.
function withSignal(work, signal) {
  if (!signal) return work;
  if (signal.aborted) return Promise.reject(cancelled());
  return new Promise((resolve, reject) => {
    const abort = () => reject(cancelled());
    signal.addEventListener('abort', abort, { once: true });
    work.then(resolve, reject).finally(() => signal.removeEventListener('abort', abort));
  });
}
async function request(url, { method = 'GET', headers = {}, data, params, signal, raw = false, noRetry = false } = {}) {
  method = method.toUpperCase();
  const u = new URL(validateProviderURL(url));
  Object.entries(params || {}).forEach(([k, v]) => { if (v !== undefined && v !== null && v !== '') u.searchParams.set(k, String(v)); });
  const cacheKey = method === 'GET' && !raw && metadataHosts.has(u.hostname) ? JSON.stringify([u.toString(),headers]) : null;
  const cached = cacheKey && metadataCache.get(cacheKey);
  if (cached && cached.expires > Date.now() && !signal?.aborted) return cached.data;
  for (let attempt = 0; attempt < 2; attempt++) {
    if (signal?.aborted) throw cancelled();
    let response;
    try {
      let current = u.toString();
      const visited = new Set();
      for (let hop = 0; hop <= 5; hop++) {
        if (visited.has(current)) throw new Error('The provider has a redirect loop.');
        visited.add(current);
        const requestURL=current;
        response = await withSignal(networkPermit(()=>{if(signal?.aborted)throw cancelled();return CapacitorHttp.request({ url: requestURL, method, headers, data, responseType: 'text', connectTimeout: 15000, readTimeout: 30000, disableRedirects: true });}), signal);
        if (!redirects.has(response.status)) break;
        if (hop === 5) throw new Error('The provider returned too many redirects.');
        if (method !== 'GET' && ![307, 308].includes(response.status)) throw new Error('The provider redirected a write operation. Update its URL before retrying.');
        current = redirectTarget(current, header(response.headers, 'location'), headers, method);
      }
    } catch (e) {
      if (e.code === 'ERR_CANCELED') throw e;
      if (/redirect|credentials were not forwarded/i.test(e.message || '')) throw e;
      const error = new Error('Unable to reach the provider. Check the connection and server address.'); error.code = 'ERR_NETWORK'; throw error;
    }
    if ((response.status === 429 || response.status >= 500) && method === 'GET' && attempt === 0 && !noRetry && u.hostname!=='www.omdbapi.com') {
      const retryAfter = response.headers?.['Retry-After'] || response.headers?.['retry-after'];
      const seconds = /^\d+$/.test(retryAfter || '') ? Number(retryAfter) : retryAfter ? Math.max(0, (Date.parse(retryAfter) - Date.now()) / 1000) : 1;
      if (Number.isFinite(seconds) && seconds <= 5) { await wait(Math.max(1000, seconds * 1000), signal); continue; }
    }
    if (response.status < 200 || response.status >= 300) {
      const messages = { 401: 'Access denied. Check your provider credentials.', 403: 'Access denied. Check account permissions.', 429: 'Rate limit reached. Wait before retrying.' };
      const error = new Error(messages[response.status] || `Provider request failed (HTTP ${response.status}).`);
      // Omit remote bodies/URLs: some providers echo query credentials in errors.
      error.response = { status: response.status, data: { detail: error.message }, headers: response.headers };
      throw error;
    }
    if (response.status === 204 || response.data === '' || response.data == null) return null;
    const parsed = raw ? response.data : parseJSONResponse(response.data);
    if (parsed?.success === false) {
      const code = /^[A-Z_\d]+$/.test(parsed.error || '') ? ` (${parsed.error})` : '';
      const e = new Error(`Provider rejected the request${code}. Check credentials and account limits.`);
      e.response = { status: 400, data: { detail: e.message } }; throw e;
    }
    if (cacheKey) { metadataCache.set(cacheKey, { expires: Date.now() + (u.hostname === 'www.omdbapi.com' ? 86400000 : 600000), data: parsed }); if (metadataCache.size > 100) metadataCache.delete(metadataCache.keys().next().value); }
    return parsed;
  }
}

export function externalRequest(url, options = {}) {
  const host = new URL(validateProviderURL(url)).hostname;
  // Requests with independent abort signals keep independent cancellation.
  const key = !options.signal && !options.raw && (options.method || 'GET').toUpperCase() === 'GET' && metadataHosts.has(host)
    ? JSON.stringify([url, options.params || {}, options.headers || {}]) : null;
  if (!key) return request(url, options);
  if (metadataPending.has(key)) return metadataPending.get(key);
  const work = request(url, options);
  metadataPending.set(key, work);
  const clear = () => { if (metadataPending.get(key) === work) metadataPending.delete(key); };
  work.then(clear, clear);
  return work;
}

let running=0;const queue=[];
function releasePermit(){running--;drain();}
function drain(){while(running<4 && queue.length){const job=queue.shift();running++;let work;try{work=job.work();}catch(error){work=Promise.reject(error);}Promise.resolve(work).then(job.resolve,job.reject).finally(releasePermit);}}
function networkPermit(work){return new Promise((resolve,reject)=>{queue.push({work,resolve,reject});drain();});}
