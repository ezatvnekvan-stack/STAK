import { useAppearance } from "../lib/appearance";
import { useMetadataQuery } from '../lib/metadataQuery';
import {useMetadataSources} from '../lib/useMetadataSources';
import RatingBadge from './RatingBadge';

export default function RatingsBlock({ meta }) {
  const { ratings } = useAppearance();
  const {settings}=useMetadataSources();
  const id = meta.imdb_id || meta.id;
  const { data:r } = useMetadataQuery(`/ratings/${id}`, {}, { enabled: settings.ratings && /^tt\d+$/.test(id || '') && Boolean(ratings.imdb || ratings.rt || ratings.metacritic) });
  const imdb = settings.ratings?r?.imdb:null;
  return <div className="mt-2 flex flex-wrap gap-1.5" aria-label="Ratings">
    <RatingBadge source="imdb" value={imdb} compact />
    <RatingBadge source="tmdb" value={meta.tmdbRating} compact />
    <RatingBadge source="rt" value={settings.ratings?r?.rt:null} compact />
    <RatingBadge source="metacritic" value={settings.ratings?r?.metacritic:null} compact />
  </div>;
}
