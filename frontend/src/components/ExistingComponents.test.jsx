import React from 'react';
import { act, mount, until } from '../testUtils';
import RatingBadge from './RatingBadge';
import EpisodeRatingsGrid from './EpisodeRatingsGrid';
import { setAppearance } from '../lib/appearance';
let view;
afterEach(async () => { if (view) await view.close(); view = null; setAppearance({ ratings: { imdb: true, tmdb: true, rt: true, metacritic: true } }); });
test('existing rating visibility changes the actual rendered badge immediately', async () => {
  view = await mount(<RatingBadge source="imdb" value="8.1" />);
  expect(view.container.querySelector('[data-testid="rating-imdb"]')).not.toBeNull();
  await act(async () => setAppearance({ ratings: { imdb: false } }));
  expect(view.container.querySelector('[data-testid="rating-imdb"]')).toBeNull();
});
test('episode ratings selection returns normalized coordinates to the existing detail workflow', async () => {
  const select = jest.fn();
  view = await mount(<EpisodeRatingsGrid videos={[{ season: '1', episode: '2', name: 'Second', imdbRating: 8.2 }]} onSelect={select} />);
  await act(async () => view.container.querySelector('button').click());
  await until(() => document.querySelector('[role="dialog"]'));
  await act(async () => document.querySelector('button[aria-label^="Season 1 episode 2"]').click());
  await act(async () => [...document.querySelectorAll('[role="dialog"] button')].find(button => button.textContent === 'Select episode').click());
  expect(select).toHaveBeenCalledWith(1, 2);
});
