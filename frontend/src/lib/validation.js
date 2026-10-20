export function validateExternalURL(value, { allowHTTP = true } = {}) {
  let url;
  try { url = new URL(String(value || '').trim()); } catch { throw new Error('Enter a complete HTTP or HTTPS URL.'); }
  if (!['https:', ...(allowHTTP ? ['http:'] : [])].includes(url.protocol) || url.username || url.password) throw new Error('Use HTTP or HTTPS without credentials in the URL.');
  if (!url.hostname || [...String(value)].some(character => character.charCodeAt(0) <= 31)) throw new Error('Invalid server address.');
  return url.toString();
}
export function objectResponse(value, provider) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) throw new Error(`${provider} returned an invalid response. Please retry.`);
  return value;
}
export function arrayResponse(value, provider) {
  if (!Array.isArray(value)) throw new Error(`${provider} returned an invalid list. Please retry.`);
  return value.filter(x => x && typeof x === 'object' && !Array.isArray(x));
}
export function parseJSONResponse(data) {
  if (typeof data !== 'string') return data;
  try { return JSON.parse(data); } catch { throw new Error('The provider returned unreadable data. Please retry.'); }
}
export function validateMagnet(value) {
  let u;
  try { u = new URL(value); } catch { throw new Error('Invalid magnet link.'); }
  if (u.protocol !== 'magnet:' || !u.searchParams.getAll('xt').some(x => /^urn:btih:(?:[a-f\d]{40}|[a-z2-7]{32})$/i.test(x))) throw new Error('A BitTorrent v1 info hash is required.');
  return u.toString();
}
export function torboxList(value) {
  return arrayResponse(value, 'TorBox').map(t => ({ ...t, name: typeof t.name === 'string' ? t.name : 'Unnamed torrent', hash: typeof t.hash === 'string' ? t.hash.toLowerCase() : '', size: Math.max(0, Number(t.size) || 0), progress: Math.max(0, Math.min(1, Number(t.progress) || 0)), files: Array.isArray(t.files) ? t.files.filter(f => f && typeof f === 'object').map(f => ({ ...f, name: String(f.name || f.short_name || 'File'), size: Number(f.size) || 0 })) : [] })).filter(t => Number.isInteger(t.id) && t.id >= 0);
}

// HTTP is permitted only for LAN services; remote provider APIs always use TLS.
export function validateProviderURL(value) {
  const safe = validateExternalURL(value), u = new URL(safe), host = u.hostname.toLowerCase();
  if (u.protocol === 'http:') {
    const octets = host.split('.').map(Number);
    const ipv4 = /^\d+\.\d+\.\d+\.\d+$/.test(host) && octets.every(x => x >= 0 && x <= 255);
    const private4 = ipv4 && (octets[0] === 10 || (octets[0] === 172 && octets[1] >= 16 && octets[1] <= 31) || (octets[0] === 192 && octets[1] === 168) || (octets[0] === 169 && octets[1] === 254));
    const private6 = /^\[(?:f[cd][a-f\d]{2}:|fe[89ab][a-f\d]:)/i.test(host);
    const lanName = (!host.includes('.') && !host.includes(':') && host !== 'localhost') || /\.(local|lan|home|internal)$/.test(host);
    if (!private4 && !private6 && !lanName) throw new Error('HTTP is allowed only for local network servers. Use HTTPS for public addresses.');
  }
  return safe;
}
