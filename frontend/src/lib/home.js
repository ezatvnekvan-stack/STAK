import { useHomePreferences } from "./homePreferences";

const YEAR = new Date().getFullYear();
export const SECTIONS = {
  continue: { label: "Continue watching", key: true },
  upcoming: { label: "New releases", key: true },
  watchlist: { label: "Your watchlist", key: true },
  because: { label: "Because you watched", key: true },
  for_you: {label:"For You · watchlist genres",q:{type:"movie",sort:"rating",min_rating:7}},
  genre_trending: {label:"Trending · watchlist genres",q:{type:"series",sort:"popular"}},
  recent: {label:"Recently added to Jellyfin",key:true},
  available: {label:"Available to watch",key:true},
  new_seasons: {label:"Recently added series",key:true},
  surprise: {label:"Surprise me",key:true},
  trending_movie: { label: "Trending movies", q: { type: "movie" } },
  trending_series: { label: "Trending series", q: { type: "series" } },
  top_movie: { label: "Top rated movies", q: { type: "movie", sort: "rating" } },
  top_series: { label: "Top rated series", q: { type: "series", sort: "rating" } },
  new_movie: { label: `Released in ${YEAR} · movies`, q: { type: "movie", year_from: YEAR, year_to: YEAR, released_only:true } },
  new_series: { label: `Released in ${YEAR} · series`, q: { type: "series", year_from: YEAR, year_to: YEAR, released_only:true } },
};

const DEFAULT_ON = new Set(["continue", "upcoming", "watchlist", "because", "trending_movie", "trending_series", "top_movie", "new_movie"]);
export const DEFAULT_HOME = ["continue", "upcoming", "watchlist", "because", "trending_movie", "trending_series", "top_movie", "new_movie", "top_series", "new_series", "for_you", "genre_trending", "recent", "available", "new_seasons", "surprise"]
  .map((id) => ({ id, on: DEFAULT_ON.has(id) }));

export const sectionInfo = (id) => {
  if (id.startsWith("genre:")) {
    const [, type, genre] = id.split(":");
    return { label: `${genre} ${type === "movie" ? "movies" : "series"}`, q: { type, genre } };
  }
  return SECTIONS[id];
};

export function useHomePrefs() {
  return useHomePreferences(DEFAULT_HOME);
}
