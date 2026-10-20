export const episodeKey = (season, episode) => `${Number(season)}:${Number(episode)}`;

export function episodesOf(videos = [], season) {
  const seen = new Set();
  return videos.filter((video) => {
    const s = Number(video.season), e = Number(video.episode);
    const key = episodeKey(s, e);
    if (!Number.isInteger(s) || s < 0 || !Number.isInteger(e) || e <= 0 || (season != null && s !== Number(season)) || seen.has(key)) return false;
    seen.add(key);
    return true;
  }).map((video) => ({ ...video, season: Number(video.season), episode: Number(video.episode) }))
    .sort((a, b) => a.season - b.season || a.episode - b.episode);
}

export function hasAired(video, now = Date.now()) {
  const released = Date.parse(video?.released || video?.air_date || "");
  return !Number.isFinite(released) || released <= now;
}

export function firstEpisode(videos, season) {
  return episodesOf(videos, season)[0] || null;
}

export function nextUnwatched(videos, watched = {}) {
  const episodes = episodesOf(videos).filter((video) => hasAired(video));
  return episodes.find((video) => !watched[episodeKey(video.season, video.episode)]?.played && watched[episodeKey(video.season, video.episode)]?.progress > 0)
    || episodes.find((video) => !watched[episodeKey(video.season, video.episode)]?.played) || null;
}
