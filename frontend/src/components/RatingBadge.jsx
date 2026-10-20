import { useAppearance, RATING_SOURCES } from '../lib/appearance';
import { DotBar } from './Bits';
const letters = {
 I:['111','010','010','010','111'], M:['10001','11011','10101','10001','10001'], D:['110','101','101','101','110'], B:['110','101','110','101','110'],
 T:['111','010','010','010','010'], R:['110','101','110','101','101'], C:['011','100','100','100','011'],
};
export function RatingLogo({ source }) {
  const word = { imdb:'IMDB', tmdb:'TMDB', rt:'RT', metacritic:'MC' }[source] || 'IMDB';
  let offset = 2;
  const cells = [...word].flatMap(letter => { const rows = letters[letter] || letters.M, left = offset; offset += rows[0].length + 1; return rows.flatMap((row,y) => [...row].flatMap((value,x) => value === '1' ? [{ x: left+x, y: y+2 }] : [])); });
  return <svg aria-hidden="true" viewBox={`0 0 ${offset+1} 9`} width={source === 'metacritic' || source === 'rt' ? 22 : 34} height="14" className={`pixel-rating-logo pixel-rating-logo--${source}`}><rect x=".5" y=".5" width={offset} height="8" rx="1" fill="none" stroke="currentColor" strokeWidth=".5" />{cells.map(cell => <rect key={`${cell.x}:${cell.y}`} x={cell.x} y={cell.y} width=".8" height=".8" fill="currentColor" />)}</svg>;
}
export default function RatingBadge({ source='imdb', value, compact=false }) {
  const { ratings } = useAppearance();
  const number = parseFloat(value), max = ['imdb','tmdb'].includes(source) ? 10 : 100;
  if (!ratings[source] || value == null || !Number.isFinite(number) || number <= 0 || number > max) return null;
  return <span className={`rating-badge ${compact ? 'rating-badge-small' : ''}`} data-testid={`rating-${source}`} title={`${RATING_SOURCES[source]}: ${value}`} aria-label={`${RATING_SOURCES[source]}: ${value}${String(value).includes('%') ? '' : ` out of ${max}`}`}><RatingLogo source={source}/><span className="sr-only">{RATING_SOURCES[source]}</span><span className="rating-value font-nmono">{value}</span>{!compact && <DotBar value={number} max={max}/>}</span>;
}
