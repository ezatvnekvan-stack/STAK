import { errMsg } from "../lib/api";
import { useQuery } from '@tanstack/react-query';
import { useApp } from '../context/AppContext';
import { accountRequest } from '../lib/accountQueries';
import { Link } from "react-router-dom";
import { ChevronRight } from "lucide-react";
import PosterCard from "./PosterCard";
import { RowSkeleton } from "./Skeletons";
import { useT } from "../lib/i18n";
import GestureRail from './design/GestureRail';

export const PosterRow = ({ items, testId }) => (
  <GestureRail testId={testId} label="Browse titles" className="home-poster-rail no-scrollbar flex gap-3 pb-1">
    {items.map((m, i) => (
      <div key={`${m.id}${i}`} className="home-poster-slot shrink-0 snap-start">
        <PosterCard item={m} index={i} badge={m.badge} />
      </div>
    ))}
  </GestureRail>
);

// Top-10 row: large outlined Doto numbers behind the posters.
export const NumberedRow = ({ items, testId }) => (
  <div data-testid={testId} className="no-scrollbar -mx-4 flex snap-x snap-proximity gap-1 overflow-x-auto scroll-px-4 px-4 pb-1">
    {items.slice(0, 10).map((m, i) => (
      <div key={`${m.id}${i}`} className="relative flex w-[172px] shrink-0 snap-start items-end sm:w-[200px]">
        <span aria-hidden="true" className="font-dot -mr-5 select-none text-[88px] leading-[0.8] text-transparent" style={{ WebkitTextStroke: "2px var(--fg-400)" }}>{i + 1}</span>
        <div className="relative z-10 w-[112px] sm:w-[130px]"><PosterCard item={m} index={i} badge={m.badge} /></div>
      </div>
    ))}
  </div>
);

export default function HomeRow({ id, label, load, seeAll, numbered = false, revision = '' }) {
  const t = useT();
  const { session } = useApp();
  const query = useQuery({ queryKey: ['account', session.scope, 'home-row', id, revision], queryFn: async ({ signal }) => { const list = await load(accountRequest(session, { signal })); return Array.isArray(list) ? list : []; }, staleTime: 600000, retry: false });
  const items = query.data;
  const error = query.error ? errMsg(query.error) : '';
  if (items && !items.length && !error) return null;
  return (
    <section data-testid={`home-row-${id}`} className="rise section-gap">
      <div className="mb-3 flex items-center justify-between">
        <h2 className="n-h2">{label}</h2>
        {seeAll && (
          <Link to={seeAll} data-testid={`home-row-${id}-see-all`} className="inline-flex min-h-[32px] items-center gap-0.5 font-nmono text-[10px] uppercase tracking-widest text-neutral-400 hover:text-white">
            {t("See all")} <ChevronRight size={12} />
          </Link>
        )}
      </div>
      {error && <p role="alert" className="text-xs text-neutral-400">{error} <button className="n-chip ml-2" disabled={query.isFetching} onClick={() => query.refetch()}>Retry</button></p>}
      {!items ? !error && <RowSkeleton /> : numbered ? <NumberedRow items={items} testId={`home-row-${id}-items`} /> : <PosterRow items={items} testId={`home-row-${id}-items`} />}
    </section>
  );
}
