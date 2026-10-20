// Embla's installed React adapter supplies drag, focus and snap behaviour.
import useEmblaCarousel from 'embla-carousel-react';
import { useMotionPolicy } from './MotionSystem';

export default function GestureRail({ children, label, className = '', testId }) {
  const { reduced } = useMotionPolicy();
  const [viewport] = useEmblaCarousel({ dragFree: true, containScroll: 'trimSnaps', align: 'start', duration: reduced ? 0 : 22 });
  return <div ref={viewport} className="gesture-viewport" role="group" aria-label={label} data-gesture-rail="embla"><div data-testid={testId} className={`gesture-track ${className}`}>{children}</div></div>;
}
