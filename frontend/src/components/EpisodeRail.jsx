import { useEffect, useRef } from 'react';
import { useAppearance } from '../lib/appearance';

// One horizontal axis: changing an episode never scrolls the surrounding page.
export default function EpisodeRail({ selectedKey, label, children, testId = 'episode-rail',onNavigate }) {
  const ref = useRef(null);
  const { reducedMotion } = useAppearance();
  useEffect(() => {
    const rail = ref.current;
    const selected = rail?.querySelector('[aria-pressed="true"]');
    if (!selected) return;
    const viewport = rail.getBoundingClientRect(), card = selected.getBoundingClientRect();
    if (card.left >= viewport.left && card.right <= viewport.right) return;
    const left = Math.max(0, rail.scrollLeft + card.left - viewport.left - (viewport.width - card.width) / 2);
    const reduce = reducedMotion || window.matchMedia?.('(prefers-reduced-motion: reduce)')?.matches;
    if (rail.scrollTo) rail.scrollTo({ left, behavior: reduce ? 'auto' : 'smooth' });
    else rail.scrollLeft = left;
  }, [selectedKey, reducedMotion]);
  const navigate = event => {
    if (!['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) return;
    const buttons = [...event.currentTarget.querySelectorAll('button:not(:disabled)')];
    const index = buttons.indexOf(event.target);
    if (index < 0) return;
    const next = event.key === 'Home' ? 0 : event.key === 'End' ? buttons.length - 1
      : Math.max(0, Math.min(buttons.length - 1, index + (event.key === 'ArrowRight' ? 1 : -1)));
    event.preventDefault();
    buttons[next].focus({ preventScroll: true });
    if(onNavigate)onNavigate(buttons[next]);else buttons[next].click();
  };
  return <div ref={ref} role="group" aria-label={label} data-testid={testId} className="episode-rail no-scrollbar" onKeyDown={navigate}>{children}</div>;
}
