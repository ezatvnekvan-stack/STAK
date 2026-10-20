import { episodesOf, firstEpisode, nextUnwatched } from "./episodes";

const videos = [{ season: "1", episode: "2" }, { season: 1, episode: 1 }, { season: 1, episode: 2 }, { season: 2, episode: 3 }, { season: 2, episode: 4, released: "2099-01-01" }];

test("episode normalization accepts numeric strings, sorts and deduplicates without mutating metadata", () => {
  expect(episodesOf(videos).map(video => `${video.season}:${video.episode}`)).toEqual(["1:1", "1:2", "2:3", "2:4"]);
  expect(videos[0].season).toBe("1");
  expect(firstEpisode(videos, 2).episode).toBe(3);
});

test("next up starts at the first unwatched episode and does not skip gaps", () => {
  expect(nextUnwatched(videos, { "1:2": { played: true } }).episode).toBe(1);
  expect(nextUnwatched(videos, { "1:1": { played: true }, "1:2": { played: true } })).toMatchObject({ season: 2, episode: 3 });
});

test("next up prefers a resumable episode and excludes unaired episodes", () => {
  expect(nextUnwatched(videos, { "2:3": { progress: 20 } })).toMatchObject({ season: 2, episode: 3 });
  expect(nextUnwatched(videos, { "1:1": { played: true }, "1:2": { played: true }, "2:3": { played: true } })).toBeNull();
});
