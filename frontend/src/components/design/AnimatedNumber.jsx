// Adapted from Magic UI NumberTicker (MIT). Exact text is always accessible;
// updates animate only while visible and never run with reduced motion.
import { useEffect, useRef } from 'react';
import { animate, useInView } from 'framer-motion';
import { useMotionPolicy } from './MotionSystem';

export default function AnimatedNumber({ value, decimals = 0, suffix = '', className = '', ...props }) {
  const ref = useRef(null), previous = useRef(Number(value)), { reduced } = useMotionPolicy();
  const visible = useInView(ref, { margin: '0px' });
  const numeric = Number(value), valid = Number.isFinite(numeric), places = Math.max(0, Math.min(2, decimals));
  const format = number => Number(number.toFixed(places)).toLocaleString();
  const exact = valid ? `${format(numeric)}${suffix}` : '—';
  useEffect(() => {
    const node = ref.current;
    if (!node || !valid) return undefined;
    const start = previous.current;
    previous.current = numeric;
    if (reduced || !visible || !Number.isFinite(start) || start === numeric) { node.textContent = exact; return undefined; }
    const controls = animate(start, numeric, { duration: .45, ease: 'easeOut', onUpdate: number => { node.textContent = `${Number(number.toFixed(places)).toLocaleString()}${suffix}`; }, onComplete: () => { node.textContent = exact; } });
    return () => { controls.stop(); node.textContent = exact; };
  }, [numeric, exact, places, suffix, valid, visible, reduced]);
  return <span {...props} role="img" aria-label={exact} className={`animated-number ${className}`}><span aria-hidden="true" ref={ref}>{exact}</span></span>;
}
