import { activityHref, boundedProgress, groupActivity, localDay } from './viewingActivity';
test('episode links retain actual coordinates and unsupported identifiers fall back to search',()=>{
  expect(activityHref({type:'episode',imdb_id:'tt123',season:0,episode:4})).toBe('/title/series/tt123?season=0&episode=4');
  expect(activityHref({type:'movie',imdb_id:'tmdb:123'})).toBe('/title/movie/tmdb:123');
  expect(activityHref({type:'episode',imdb_id:'jellyfin-private',series_name:'A & B'})).toBe('/search?q=A%20%26%20B');
});
test('dates group in descending local day order without dropping undated manual history',()=>{
  const items=[{last_played:'2026-10-01T12:00:00'},{last_played:'bad'},{last_played:'2026-10-03T12:00:00'}];
  expect(groupActivity(items).map(([key])=>key)).toEqual(['2026-10-03','2026-10-01','undated']);
  expect(localDay('2026-10-01')).toBe('2026-10-01');expect(localDay(null)).toBeNull();
});
test('invalid provider progress never overflows a card',()=>{expect(boundedProgress(120)).toBe(100);expect(boundedProgress(-12)).toBe(0);expect(boundedProgress('not a number')).toBe(0);});
