export const normalizeTitle = value => String(value || '').normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLocaleLowerCase().replace(/[^\p{L}\p{N}]+/gu, ' ').trim().replace(/\s+/g, ' ');
function distance(a, b) {
  a = a.slice(0, 100); b = b.slice(0, 100);
  let row = Array.from({ length: b.length + 1 }, (_, index) => index);
  for (let i = 1; i <= a.length; i++) {
    const next = [i];
    for (let j = 1; j <= b.length; j++) next[j] = Math.min(next[j - 1] + 1, row[j] + 1, row[j - 1] + (a[i - 1] === b[j - 1] ? 0 : 1));
    row = next;
  }
  return row[b.length];
}
export function relevance(item, query) {
  const raw = String(query || '').trim(), year = raw.match(/\b((?:19|20)\d{2})\)?$/)?.[1];
  const term = normalizeTitle(year && raw.replace(/\(?\b(?:19|20)\d{2}\)?$/, '').trim() ? raw.replace(/\(?\b(?:19|20)\d{2}\)?$/, '') : raw);
  if (!term) return 0;
  if (normalizeTitle(item.imdb_id || item.id) === term) return -1;
  const names = [item.name, item.title, item.original_title, item.original_name,...(Array.isArray(item.aliases)?item.aliases:[]),...(Array.isArray(item.alternative_titles)?item.alternative_titles.map(x=>typeof x==='string'?x:x?.title || x?.name):[])].filter(Boolean).map(normalizeTitle);
  // Numeric title words (Blade Runner 2049, Class of 1984) must win as
  // exact titles before treating a trailing number as release-year metadata.
  if (names.includes(normalizeTitle(raw))) return -0.5;
  const tokens = term.split(' ');
  const score = name => name === term ? 0 : name.startsWith(term + ' ') ? 100 : tokens.every(token => name.split(' ').includes(token)) ? 200 + Math.abs(name.length - term.length) : name.includes(term) ? 300 + name.length - term.length : 400 + (1 - tokens.filter(token => name.split(' ').some(word => word.startsWith(token))).length / tokens.length) * 100 + distance(name, term) / Math.max(name.length, term.length, 1) * 100;
  const titleYear = String(item.year || item.releaseInfo || item.release_date || item.first_air_date || '').match(/\d{4}/)?.[0];
  return Math.min(...names.map(score), 1000) + (year && titleYear !== year ? 30 : 0);
}
export function rankTitles(items, query) {
  return items.map((item, index) => ({ item, index, score: relevance(item, query) })).sort((a, b) => a.score - b.score || a.index - b.index).map(entry => entry.item);
}

export function searchTerms(query){
  const raw=String(query || '').trim(),match=raw.match(/^(.*?)\s+\(?((?:19|20)\d{2})\)?$/);
  return match && match[1].trim()?{title:match[1].trim(),year:match[2]}:{title:raw,year:''};
}
