const provider = (ids, name) => Object.entries(ids || {}).find(([key]) => key.toLowerCase() === name)?.[1];
export function jellyfinIdentity(item, parent) {
  const episode = item?.Type === 'Episode';
  const title = episode ? parent : item;
  const ids = title?.ProviderIds || (episode ? item?.SeriesProviderIds : item?.ProviderIds);
  const imdb = String(provider(ids, 'imdb') || '');
  const tmdb = String(provider(ids, 'tmdb') || '');
  const id = /^tt\d+$/.test(imdb) ? imdb : /^\d+$/.test(tmdb) ? `tmdb:${tmdb}` : null;
  if (!id) return null;
  const type = episode || title?.Type === 'Series' ? 'series' : title?.Type === 'Movie' ? 'movie' : null;
  if (!type) return null;
  const season = Number(item?.ParentIndexNumber), number = Number(item?.IndexNumber);
  return { id, type, ...(episode && Number.isInteger(season) && season >= 0 && Number.isInteger(number) && number > 0 ? { season, episode: number } : {}) };
}
export function detailsHref(identity) {
  if (!identity?.id || !['movie', 'series'].includes(identity.type)) return null;
  const params = identity.episode ? `?season=${identity.season}&episode=${identity.episode}` : '';
  return `/title/${identity.type}/${identity.id}${params}`;
}
