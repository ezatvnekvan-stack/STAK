import {useMetadataRevision} from '../lib/metadata/runtime';
import SearchPage from "../pages/SearchPage";
import { useSearchParams } from "react-router-dom";
import { useEffect, useState } from "react";
import { useQueryClient } from '@tanstack/react-query';
import HomeJellyfinExtras from "./HomeJellyfinExtras";
import MediaHero from "./MediaHero";
import DeferredSection from './DeferredSection';
import { api } from "../lib/api";
import { sectionInfo, useHomePrefs } from "../lib/home";
import { useApp } from "../context/AppContext";
import HomeRow from "./HomeRow";
import JellyfinHub from "./JellyfinHub";
import OnboardingCard from "./OnboardingCard";
import UpcomingStrip from "./UpcomingStrip";
import TonightCard from './TonightCard';
import JellyfinHomeLibraries from './JellyfinHomeLibraries';
import { REFRESH_EVENT } from "./AppShell";

const metahub = (id) => `https://images.metahub.space/poster/small/${id}/img`;
const sxe = (s, e) => `S${String(s).padStart(2, "0")}E${String(e).padStart(2, "0")}`;

const toQuery = (q) => `/search?${new URLSearchParams({ browse: "1", ...q }).toString()}`;
// Rows rendered by dedicated components (or not at all on Home).
// Rows shown as a Top-10 with big numbers.


function loader(id, ctx) {
  if (id === "continue") {
    return async () => (await api.get("/tracking/history")).data.continue.map((d) => ({
      id: d.imdb_id, type: d.type === "movie" ? "movie" : "series", name: d.series_name || d.name, poster: metahub(d.imdb_id),
      releaseInfo: d.type === "episode" ? sxe(d.season, d.episode) : `${Math.round(d.progress)}%`,
    }));
  }
  if (id === "watchlist") return async () => ctx.watchlist.slice(0, 20).map((w) => ({ ...w, id: w.imdb_id, releaseInfo: w.year }));
  const { q: original } = sectionInfo(id);
  const q = { ...original };
  if (["for_you", "genre_trending"].includes(id)) { const counts = new Map(); for (const item of ctx.watchlist) for (const genre of item.genres || []) counts.set(genre, (counts.get(genre) || 0) + 1); q.genre = [...counts].sort((a,b) => b[1] - a[1])[0]?.[0]; }
  return async (config) => (await api.get("/discover", { ...config, params: q })).data.items.slice(0, 20);
}

export default function HomeSections() {
  const ctx = useApp(),metadataRevision=useMetadataRevision();
  const [params]=useSearchParams(),searching=!!params.get("q")?.trim();
  const [home] = useHomePrefs();
  const client = useQueryClient();
  const [refreshVersion, setRefreshVersion] = useState(0);
  useEffect(() => {
    const refresh = () => { setRefreshVersion(value => value + 1); client.invalidateQueries({ queryKey: ['account', ctx.session.scope] }); };
    window.addEventListener(REFRESH_EVENT, refresh);
    return () => window.removeEventListener(REFRESH_EVENT, refresh);
  }, [client, ctx.session.scope]);
  const revision = JSON.stringify(ctx.watchlist.map(w => [w.imdb_id, w.genres]));
  return (
    <div data-testid="home-sections" className="stak-home">
      <SearchPage embedded hideFilters searchOnly/>
      {!searching && <div className="home-feed">
      <OnboardingCard />
      <MediaHero />
      {home.filter((s) => s.on).map(({ id }) => {
        if (id === 'continue') return <JellyfinHub key={id} />;
        if (id === 'torbox_recent') return null;
        if (id === 'upcoming') return <DeferredSection key={id} id={id}><UpcomingStrip refreshVersion={`${refreshVersion}:${metadataRevision}`} /></DeferredSection>;
        if (id === 'because') return null;
        if (id === 'surprise') return <DeferredSection key={id} id={id}><TonightCard refreshVersion={`${refreshVersion}:${metadataRevision}`} /></DeferredSection>;
        if (id === 'available') return <DeferredSection key={id} id={id}><JellyfinHomeLibraries /></DeferredSection>;
        if (['recent', 'new_seasons'].includes(id)) return <DeferredSection key={id} id={id}><HomeJellyfinExtras section={id} /></DeferredSection>;
        const info = sectionInfo(id);
        if (!info || (info.key && !ctx.key && id !== "watchlist")) return null;
        const seeAll = info.q ? toQuery(info.q) : "/watchlist";
        return <DeferredSection key={id} id={id}><HomeRow id={id.replace(/:/g, "-")} revision={`${metadataRevision}:${['watchlist', 'for_you', 'genre_trending'].includes(id) ? revision : ''}`} label={["for_you", "genre_trending"].includes(id) && !ctx.watchlist.some(x => x.genres?.length) ? "Popular picks · add titles to personalize" : info.label} load={loader(id, ctx)} seeAll={seeAll} /></DeferredSection>;
      })}
      </div>}
    </div>
  );
}
