import { useEffect, useState } from "react";
import { api } from "../lib/api";
import { useT } from "../lib/i18n";
import { PosterRow } from "./HomeRow";
import { RowSkeleton } from "./Skeletons";

// "Because you watched X": same genre as your most recent watch, best rated first.
export default function BecauseRow({ refreshVersion = 0 }) {
  const t = useT();
  const [state, setState] = useState({ loading: true, name: "", items: [] });
  useEffect(() => {
    let live = true;
    (async () => {
      try {
        const { data: hist } = await api.get("/tracking/history");
        const last = (hist.recent || [])[0];
        if (!last?.imdb_id) throw new Error("no history");
        const type = last.type === "movie" ? "movie" : "series";
        const { data: meta } = await api.get(`/meta/${type}/${last.imdb_id}`);
        const genre = (meta.genres || meta.genre || [])[0];
        if (!genre) throw new Error("no genre");
        const { data } = await api.get("/discover", { params: { type, genre, sort: "rating" } });
        const items = (data.items || []).filter((x) => x.id !== last.imdb_id).slice(0, 16);
        if (live) setState({ loading: false, name: last.series_name || last.name || meta.name, items });
      } catch {
        if (live) setState({ loading: false, name: "", items: [] });
      }
    })();
    return () => { live = false; };
  }, [refreshVersion]);
  if (!state.loading && !state.items.length) return null;
  return (
    <section data-testid="home-row-because" className="rise section-gap">
      <h2 className="n-h2 mb-3 truncate">{t("Because you watched")} <span className="text-neutral-400">{state.name}</span></h2>
      {state.loading ? <RowSkeleton /> : <PosterRow items={state.items} testId="home-row-because-items" />}
    </section>
  );
}
