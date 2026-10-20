import {useMetadataSources} from '../lib/useMetadataSources';
import {useMetadataQuery} from '../lib/metadataQuery';
import RatingBadge from './RatingBadge';
import { catalogMatch } from '../lib/experience';
import MediaQuickActions from "./MediaQuickActions";
import { useState } from "react";
import { Link } from "react-router-dom";
import { Bookmark, Check } from "lucide-react";
import { useApp } from "../context/AppContext";
import { Highlight } from "./Bits";
import JellyfinLogo from './JellyfinLogo';

const FALLBACK = "/icon.svg";

export default function PosterCard({ item, index, badge, term, layout, hideWatchlistMarker=false }) {
  const { watchIds, tracking, catalog } = useApp();
  const {settings}=useMetadataSources(),id=item.imdb_id || item.id;
  const ratings=useMetadataQuery(`/ratings/${id}`,{}, {enabled:settings.ratings && settings.imdbOnLists && /^tt\d+$/.test(id || '')});
  const [loaded, setLoaded] = useState(false);
  const t = tracking.get(item.imdb_id || item.id);
  return (
    <MediaQuickActions item={item}><Link
      to={`/title/${item.type}/${item.imdb_id || item.id}`}
      viewTransition
      onClick={(e) => { const img = e.currentTarget.querySelector("img"); if (img) img.style.viewTransitionName = "poster-hero"; }}
      data-testid={`poster-card-${index}`}
      className={`media-poster group block ${layout==='list'?'collection-media-row':''}`}
      style={{ animationDelay: `${Math.min(index || 0, 12) * 30}ms` }}
    >
      {/* fixed aspect ratio => no layout jump while images load */}
      <div data-theme="dark" className="poster-art dot-bg relative aspect-[2/3] overflow-hidden rounded-2xl border border-[color:var(--c-222222)] bg-[color:var(--c-111111)]">
        <img
          src={item.poster || FALLBACK}
          onError={(e) => { if (e.currentTarget.dataset.fallback !== 'true') { e.currentTarget.dataset.fallback = 'true'; e.currentTarget.src = FALLBACK; } else e.currentTarget.style.visibility = "hidden"; }}
          onLoad={() => setLoaded(true)}
          alt=""
          loading="lazy"
          decoding="async"
          className={`h-full w-full object-cover group-hover:scale-105 ${loaded ? "img-sharp" : "img-blur"}`}
          style={{ opacity: loaded ? 1 : 0.6 }}
        />
        <div className="absolute inset-x-0 bottom-0 h-1/2 bg-gradient-to-t from-black/90 to-transparent" />
        {badge && <div className="absolute bottom-2 left-2">{badge}</div>}
        {t?.played && (
          <span role="img" aria-label="Watched" data-testid={`poster-watched-${index}`} className="glyph absolute left-2 top-2 !border-[#10B981]/60 !px-1.5 !py-1 !text-[#10B981]">
            <Check size={11} strokeWidth={3} />
          </span>
        )}
        {!t?.played && t?.progress > 0 && (
          <div role="progressbar" aria-label={`${item.name} watched progress`} aria-valuemin={0} aria-valuemax={100} aria-valuenow={Math.round(Math.min(100,Math.max(0,t.progress)))} data-testid={`poster-progress-${index}`} className="absolute inset-x-0 bottom-0 h-1 bg-white/10">
            <div className="h-full bg-[color:var(--n-accent)]" style={{ width: `${Math.min(100,Math.max(0,t.progress))}%` }} />
          </div>
        )}
        {!hideWatchlistMarker && watchIds.has(item.imdb_id || item.id) && (
          <span role="img" aria-label="In watchlist" data-testid={`poster-watchlist-marker-${index}`} className="glyph absolute right-2 top-2 !px-1.5 !py-1">
            <Bookmark size={11} className="fill-[color:var(--n-accent)] text-[color:var(--n-accent)]" />
          </span>
        )}
      </div>
      <div className="poster-info mt-2 px-1">
        <div className="poster-ratings"><RatingBadge source="tmdb" value={item.tmdbRating} compact/>{settings.imdbOnLists && settings.ratings && <RatingBadge source="imdb" value={ratings.data?.imdb} compact/>}</div>

        <div className="poster-title line-clamp-2 text-sm font-semibold text-white"><Highlight text={item.name} term={term} /></div>
        <div className="poster-meta"><div className="poster-year font-nmono text-[11px] text-neutral-500">{item.releaseInfo || item.year || "—"}{layout && item.type && ` · ${item.type==='series'?'Series':'Movie'}`}</div>{catalogMatch(item,catalog) && <span className="poster-availability" aria-label="Available in Jellyfin" title="Available in Jellyfin"><JellyfinLogo size={12}/><span className="sr-only">Jellyfin</span></span>}</div>
      </div>
    </Link></MediaQuickActions>
  );
}
