// Season-pack detection from a release name. Heuristic: based on naming only, not on torrent contents.
const SINGLE_EP = /\bS\d{1,2}[ ._-]?E\d{1,3}\b|\b\d{1,2}x\d{2,3}\b/i;
// "S01E01-E10", "S01E01-10", "E01-E10", "1x01-10", "E01 to 10": an episode RANGE is a pack, not one episode.
const EP_RANGE = /\bS?\d{0,2}[ ._-]?E\d{1,3}[ ._-]?(?:-|–|~|to)[ ._-]?E?\d{1,3}\b|\b\d{1,2}x\d{2,3}[ ._-]?(?:-|–|to)[ ._-]?\d{2,3}\b/i;
const SEASON_SPAN = /\bS(\d{1,2})(?:[ ._-]?(?:-|–|to|&|\+)[ ._-]?S?(\d{1,2}))?\b(?![ ._-]?E\d)/i;
const WORD_SEASON = /\b(?:seasons?|staffel|saison|temporadas?|stagione|[eé]vad)[ ._-]?(\d{1,2})(?:[ ._-]?(?:-|–|to|&|\+)[ ._-]?(\d{1,2}))?\b/i;
const COMPLETE = /\b(?:complete[ ._-]?(?:series|season|collection|edition)?|season[ ._-]?pack|full[ ._-]?(?:series|season)|batch|teljes)\b/i;

export function seasonRange(name) {
  const n = String(name || "");
  const episodeSeason = EP_RANGE.test(n) && n.match(/\b(?:S(\d{1,2})[ ._-]?E\d|([0-9]{1,2})x\d)/i);
  if (episodeSeason) {
    const season = Number(episodeSeason[1] || episodeSeason[2]);
    return [season, season];
  }
  const m = n.match(SEASON_SPAN) || n.match(WORD_SEASON);
  if (!m) return null;
  const a = Number(m[1]);
  const b = m[2] ? Number(m[2]) : a;
  return [Math.min(a, b), Math.max(a, b)];
}

export function isSeasonPack(stream, season) {
  const name = String(stream.name || "");
  const single = SINGLE_EP.test(name) && !EP_RANGE.test(name);
  if (single) return false;
  const range = seasonRange(name);
  const signal = Boolean(range) || EP_RANGE.test(name) || COMPLETE.test(name);
  if (!signal) return false;
  if (range && season && (season < range[0] || season > range[1])) return false; // pack of another season
  return true;
}
