import { useMemo, useState } from 'react';
import { Grid3X3 } from 'lucide-react';
import { Dialog, DialogContent, DialogDescription, DialogTitle } from './ui/dialog';
import { episodesOf } from '../lib/episodes';
import { useAppearance } from '../lib/appearance';

export default function EpisodeRatingsGrid({ videos = [], onSelect }) {
  const [open, setOpen] = useState(false);
  const [selected, setSelected] = useState(null);
  const { ratings } = useAppearance();
  const episodes = useMemo(() => episodesOf(videos), [videos]);
  const seasons = [...new Set(episodes.map(v => v.season))];
  const maxEpisode = Math.max(0, ...episodes.map(v => v.episode));
  const byPosition = new Map(episodes.map(v => [`${v.season}:${v.episode}`, v]));
  const ratingOf = video => {
    const source = video?.imdbRating != null || video?.imdb_rating != null ? 'imdb' : video?.tmdbRating != null ? 'tmdb' : null;
    const n = Number(source === 'imdb' ? video.imdbRating ?? video.imdb_rating : video?.tmdbRating);
    return source && ratings[source] && Number.isFinite(n) && n > 0 && n <= 10 ? { value: n, source } : null;
  };
  const count = episodes.filter(v => ratingOf(v)).length;
  if (!seasons.length || (!ratings.imdb && !ratings.tmdb)) return null;
  return <>
    <button type="button" className="n-btn-ghost mb-5" onClick={() => { setSelected(null); setOpen(true); }}><Grid3X3 size={16} /> Episode Ratings · {count ? `${count} rated` : 'Ratings unavailable'}</button>
    <Dialog open={open} onOpenChange={setOpen}><DialogContent className="max-w-4xl w-[calc(100vw-24px)] bg-[color:var(--c-101010)]">
      <DialogTitle className="font-dot text-2xl pr-8">Episode Ratings</DialogTitle>
      <DialogDescription>Available IMDb or TMDb episode scores. Missing ratings are shown as a dash.</DialogDescription>
      <div className="overflow-x-auto pb-3"><table className="w-full border-separate border-spacing-1 text-center text-xs"><thead><tr><th className="p-2">S / E</th>{Array.from({ length: maxEpisode }, (_, i) => <th className="min-w-[44px] p-2" key={i}>E{i + 1}</th>)}</tr></thead><tbody>{seasons.map(season => <tr key={season}><th scope="row" className="p-2">S{season}</th>{Array.from({ length: maxEpisode }, (_, i) => {
        const video = byPosition.get(`${season}:${i + 1}`), score = ratingOf(video);
        return <td key={i}><button type="button" disabled={!video} aria-label={video ? `Season ${season} episode ${i + 1}: ${video.name || video.title || ''}; ${score ? `${score.source} ${score.value}/10` : 'not rated'}` : 'No episode'} onClick={() => setSelected(video)} className="h-11 min-w-[44px] w-full rounded-md border border-[color:var(--c-333333)] bg-[color:var(--c-181818)] font-nmono disabled:opacity-20">{video ? score?.value.toFixed(1) ?? '—' : ''}</button></td>;
      })}</tr>)}</tbody></table></div>
      {!count && <p className="text-sm text-neutral-400">The current metadata does not provide visible episode ratings. No scores are generated.</p>}
      {selected && <section className="n-card p-3 space-y-2"><h3 className="font-semibold">S{selected.season} E{selected.episode} · {selected.name || selected.title || 'Episode'}</h3><p className="text-xs text-neutral-400">{selected.overview || selected.description || 'No description available.'}</p><button className="n-chip" onClick={() => { onSelect?.(selected.season, selected.episode); setOpen(false); }}>Select episode</button></section>}
    </DialogContent></Dialog>
  </>;
}
