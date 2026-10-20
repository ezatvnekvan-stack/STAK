import { useEffect, useState } from "react";
import { Drawer, DrawerContent, DrawerHeader, DrawerTitle, DrawerDescription } from "./ui/drawer";
import { api } from "../lib/api";

const SORTS = [["popular", "Popular"], ["rating", "Rating"], ["newest", "Newest"], ["oldest", "Oldest"], ["name", "A–Z"]];
const RATINGS = [0, 6, 7, 8, 9];
const Y = new Date().getFullYear();
const DECADES = [["This year", Y, Y], ["2020s", 2020, Y], ["2010s", 2010, 2019], ["2000s", 2000, 2009], ["90s", 1990, 1999], ["Older", 1900, 1989]];

const Group = ({ label, children }) => (
  <div className="mb-5">
    <div className="n-label mb-2">{label}</div>
    <div className="flex flex-wrap gap-2">{children}</div>
  </div>
);

export default function DiscoverFilters({ open, onOpenChange, value, onApply }) {
  const [f, setF] = useState(value);
  const [genres, setGenres] = useState({});
  const valueKey = JSON.stringify(value);
  useEffect(() => { if (open) setF(JSON.parse(valueKey)); }, [open, valueKey]);
  useEffect(() => { api.get("/meta/genres").then(({ data }) => setGenres(data)).catch(() => {}); }, []);
  const set = (k, v) => setF((p) => ({ ...p, [k]: v }));
  const chip = (tid, active, onClick, label) => <button key={tid} data-testid={tid} data-active={active} onClick={onClick} className="n-chip">{label}</button>;

  return (
    <Drawer open={open} onOpenChange={onOpenChange}>
      <DrawerContent data-testid="discover-filters" className="max-h-[90dvh] border-[color:var(--c-2a2a2a)] bg-[color:var(--c-0d0d0d)]">
        <div className="mx-auto w-full max-w-2xl overflow-y-auto px-4 pb-6">
          <DrawerHeader className="px-0 text-left">
            <DrawerTitle className="font-dot text-2xl text-white">Filters</DrawerTitle>
            <DrawerDescription className="text-neutral-500">Search movies and series together.</DrawerDescription>
          </DrawerHeader>

          <Group label="Sort">{SORTS.map(([v, l]) => chip(`filter-sort-${v}`, f.sort === v, () => set("sort", v), l))}</Group>
          <Group label="Genre">
            {chip("filter-genre-any", !f.genre, () => set("genre", ""), "Any")}
            {[...new Set((f.type ? genres[f.type] || [] : [...(genres.movie || []), ...(genres.series || [])]).map(g => typeof g === "string" ? g : g?.name).filter(g => typeof g === "string"))].map((g) => chip(`filter-genre-${g.toLowerCase()}`, f.genre === g, () => set("genre", g), g))}
          </Group>
          <Group label="Year">
            {DECADES.map(([l, a, b]) => chip(`filter-decade-${l.toLowerCase().replace(/\s/g, "-")}`, +f.year_from === a && +f.year_to === b, () => setF((p) => ({ ...p, year_from: a, year_to: b })), l))}
            <div className="flex w-full items-center gap-2 pt-1">
              <input data-testid="filter-year-from" type="number" inputMode="numeric" value={f.year_from || ""} onChange={(e) => set("year_from", e.target.value)} placeholder="From" className="n-input !h-11 flex-1" />
              <span className="text-neutral-600">–</span>
              <input data-testid="filter-year-to" type="number" inputMode="numeric" value={f.year_to || ""} onChange={(e) => set("year_to", e.target.value)} placeholder="To" className="n-input !h-11 flex-1" />
            </div>
          </Group>
          <Group label="Min IMDb rating">{RATINGS.map((r) => chip(`filter-rating-${r}`, +(f.min_rating || 0) === r, () => set("min_rating", r), r ? `${r}+` : "Any"))}</Group>
          <div className="flex gap-2">
            <button data-testid="filters-reset-button" onClick={() => setF({ type: f.type, sort: "popular", genre: "", year_from: "", year_to: "", min_rating: 0 })} className="n-btn-ghost flex-1">Reset</button>
            <button data-testid="filters-apply-button" onClick={() => onApply(f)} className="n-btn-red flex-1">Show results</button>
          </div>
        </div>
      </DrawerContent>
    </Drawer>
  );
}
