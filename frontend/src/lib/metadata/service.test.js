import { searchTitles } from './service';
import { tmdbRequest } from '../providers/tmdb';
import { cinemetaRequest } from '../providers/cinemeta';
jest.mock('../providers/tmdb', () => ({ tmdbRequest: jest.fn(), tmdbType: type => type === 'series' ? 'tv' : 'movie' }));
jest.mock('../providers/cinemeta', () => ({ cinemetaRequest: jest.fn() }));
jest.mock('./resolveIdentity', () => ({ resolveIdentity: async input => ({ imdb_id: 'tt0133093', tmdb_id: 603, type: input.type }) }));
const context = { settings: { search: 'cinemeta' }, credentials: { tmdb: 'test-only-key' } };
afterEach(() => jest.resetAllMocks());
test('configured TMDb is the sole search provider and duplicate identities collapse', async () => {
  tmdbRequest.mockResolvedValue({ results: [1, 2].map(() => ({ id: 603, title: 'The Matrix', media_type: 'movie' })), total_pages: 1 });
  const result = await searchTitles({ q: 'matrix' }, context);
  expect(result.source).toBe('tmdb');
  expect(result.items).toHaveLength(1);
  expect(tmdbRequest).toHaveBeenCalledWith('/search/multi', context, expect.anything());
  expect(cinemetaRequest).not.toHaveBeenCalled();
});
test('missing TMDb key alone enables Cinemeta fallback with a visible notice', async () => {
  cinemetaRequest.mockResolvedValue({ metas: [{ id: 'tt0133093', name: 'The Matrix' }] });
  const result = await searchTitles({ q: 'matrix' }, { ...context, credentials: {} });
  expect(result.source).toBe('cinemeta');
  expect(result.notices.join(' ')).toContain('TMDb key missing');
  expect(tmdbRequest).not.toHaveBeenCalled();
});
test('a failing configured TMDb key does not silently switch to Cinemeta', async () => {
  tmdbRequest.mockRejectedValue(new Error('Access denied'));
  await expect(searchTitles({ q: 'matrix' }, context)).rejects.toThrow('Access denied');
  expect(cinemetaRequest).not.toHaveBeenCalled();
});
