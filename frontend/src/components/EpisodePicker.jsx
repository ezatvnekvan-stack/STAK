import { Check } from 'lucide-react';
import { useMemo } from 'react';
import { episodesOf } from '../lib/episodes';
import RatingBadge from './RatingBadge';
import EpisodeRail from './EpisodeRail';
import {AnimatedSelection} from './design/MotionSystem';
export default function EpisodePicker({ videos, season, episode, onChange, onOpenEpisode, watched = {} }) {
  const allEpisodes = useMemo(() => episodesOf(videos).filter(v=>v.season>0), [videos]);
  const seasons = [...new Set(allEpisodes.map((v) => v.season))];
  const eps = allEpisodes.filter((v) => v.season === Number(season));
  const seasonDone = (s) => {
    const all = allEpisodes.filter((v) => v.season === s);
    return all.length > 0 && all.every((v) => watched[`${s}:${v.episode}`]?.played);
  };
  return (
    <div className="compact-episodes mb-4 space-y-2">
      <div className="n-label">Season</div>
      <AnimatedSelection value={Number(season)} className="no-scrollbar -mx-4 flex gap-2 overflow-x-auto px-4" aria-label="Seasons">
        {seasons.map((s) => (
          <button key={s} data-value={s} data-testid={`season-chip-${s}`} data-active={s === Number(season)} aria-pressed={s === Number(season)} onClick={() => onChange(s, allEpisodes.find(v => v.season === s)?.episode || 1)} className="n-chip">
            {s===0?"Specials":`S${String(s).padStart(2,"0")}`}
            {seasonDone(s) && <span className="ml-1.5 h-1.5 w-1.5 rounded-full bg-[#10B981]" />}
          </button>
        ))}
      </AnimatedSelection>
      
      <div className="n-label flex justify-between"><span>Episodes · {eps.length}</span></div>
      <EpisodeRail selectedKey={`${season}:${episode}`} label={`Episodes in season ${season}`} onNavigate={onOpenEpisode?button=>onChange(Number(season),Number(button.dataset.episode)):undefined}>
        {eps.map(v => <button type="button" data-episode={v.episode} data-testid={`episode-chip-${v.season}-${v.episode}`} aria-pressed={v.episode === Number(episode)} onClick={() => {onChange(v.season, v.episode);onOpenEpisode?.(v);}} key={`detail-${v.id || v.episode}`} className="episode-card overflow-hidden text-left">
          <div className="episode-thumb relative aspect-video w-full overflow-hidden bg-[color:var(--c-222222)]">
            {(v.thumbnail || v.image || v.still || v.poster) && <img src={v.thumbnail || v.image || v.still || v.poster} alt="" loading="lazy" className="h-full w-full object-cover" />}
            {watched[`${v.season}:${v.episode}`]?.played && <span className="episode-watched-overlay"><Check size={12}/> Watched</span>}
            {!watched[`${v.season}:${v.episode}`]?.played && Number(watched[`${v.season}:${v.episode}`]?.progress)>0 && <span className="episode-progress-line" role="progressbar" aria-label={`Episode ${v.episode} playback`} aria-valuemin={0} aria-valuemax={100} aria-valuenow={Math.min(100,Math.max(0,Number(watched[`${v.season}:${v.episode}`].progress)))} style={{width:`${Math.min(100,Math.max(0,Number(watched[`${v.season}:${v.episode}`].progress)))}%`}}/>}
          </div>
          <div className="episode-copy min-w-0 p-2"><div className="episode-name text-xs font-semibold text-white">{v.episode}. {v.name || v.title || `Episode ${v.episode}`}</div>
            {v.imdbRating != null && <div className="mt-1"><RatingBadge source="imdb" value={v.imdbRating} compact /></div>}
            {v.released && <span className="mt-1 block font-nmono text-[9px] text-neutral-500">{String(v.released).slice(0,10)}</span>}
          </div>
        </button>)}
      </EpisodeRail>
      {!eps.length && <p className="text-sm text-neutral-400">No episodes are available for this season.</p>}
    </div>
  );
}
