// Torznab RSS parser. Avoid DOMParser because native HTTP can return XML as text.
const decode = (s = "") => String(s).replace(/<!\[CDATA\[([\s\S]*?)\]\]>/g, "$1")
  .replace(/&#(x[0-9a-f]+|\d+);/gi, (_, n) => {
    const v = n.toLowerCase().startsWith("x") ? parseInt(n.slice(1), 16) : Number(n);
    return v > 0 && v <= 0x10ffff ? String.fromCodePoint(v) : "";
  }).replace(/&quot;/g, '"').replace(/&apos;/g, "'").replace(/&lt;/g, "<")
  .replace(/&gt;/g, ">").replace(/&amp;/g, "&").trim();
const tag = (xml, name) => decode(xml.match(new RegExp(`<${name}\\b[^>]*>([\\s\\S]*?)<\\/${name}>`, "i"))?.[1] || "");
const attr = (xml, name) => decode(xml.match(new RegExp(`\\b${name}\\s*=\\s*["']([^"']*)["']`, "i"))?.[1] || "");
export const normalizeHash = (value) => {
  const hash = String(value || "").match(/(?:urn:btih:|^)([a-f0-9]{40})(?:$|[^a-f0-9])/i)?.[1];
  if (hash) return hash.toLowerCase();
  const base32 = String(value || "").match(/(?:urn:btih:|^)([a-z2-7]{32})(?:$|[^a-z2-7])/i)?.[1];
  if (!base32) return "";
  const alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
  let bits = 0, acc = 0, output = "";
  for (const ch of base32.toUpperCase()) {
    acc = (acc << 5) | alphabet.indexOf(ch); bits += 5;
    if (bits >= 8) { bits -= 8; output += ((acc >>> bits) & 255).toString(16).padStart(2, "0"); }
  }
  return output.length === 40 ? output : "";
};
export function parseTorznabDetailed(xml, source = "Torznab") {
  if (typeof xml !== "string") return { items: [], missingHashes: 0 };
  const found = [], seen = new Set();
  let missingHashes = 0;
  for (const match of xml.matchAll(/<item\b[^>]*>([\s\S]*?)<\/item>/gi)) {
    const item = match[1];
    const title = tag(item, "title");
    const enclosure = item.match(/<enclosure\b[^>]*\/?\s*>/i)?.[0] || "";
    const properties = [...item.matchAll(/<(?:torznab|newznab):attr\b[^>]*>/gi)].map(m => m[0]);
    const property = name => attr(properties.find(p => attr(p, "name").toLowerCase() === name) || "", "value");
    const magnet = property("magneturl") || [attr(enclosure, "url"), tag(item, "link"), tag(item, "guid")].find(v => v.startsWith("magnet:")) || attr(enclosure, "url") || tag(item, "link") || tag(item, "guid");
    const infohash = item.match(/<(?:torznab|newznab):attr\b[^>]*name=["'](?:infohash|hash)["'][^>]*\/?\s*>/i)?.[0] || "";
    const hash = normalizeHash(attr(infohash, "value")) || normalizeHash(magnet);
    if (!hash) { missingHashes++; continue; }
    if (seen.has(hash)) continue;
    seen.add(hash);
    const size = Number(attr(item.match(/<(?:torznab|newznab):attr\b[^>]*name=["']size["'][^>]*\/?\s*>/i)?.[0] || enclosure, "value") || attr(enclosure, "length")) || 0;
    found.push({ name: title, hash, magnet: magnet.startsWith("magnet:") ? magnet : `magnet:?xt=urn:btih:${hash}`, source, size, seeders: Math.max(0, Number(property("seeders")) || 0), peers: Math.max(0, Number(property("peers")) || 0) });
  }
  return { items: found, missingHashes };
}
export function parseTorznab(xml, source = "Torznab") { return parseTorznabDetailed(xml, source).items; }
