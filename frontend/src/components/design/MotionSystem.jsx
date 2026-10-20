// Adapted from Motion Primitives AnimatedBackground and TransitionPanel (MIT).
// Controlled selection preserves the caller's handlers, semantics and focus.
import { Children, cloneElement, isValidElement, useId, useSyncExternalStore } from 'react';
import { LayoutGroup, MotionConfig, motion } from 'framer-motion';
import { useAppearance } from '../../lib/appearance';
import { motionPolicy, STAK_EASE } from '../../lib/motionPolicy';

const systemMotion = typeof window !== 'undefined' && window.matchMedia ? window.matchMedia('(prefers-reduced-motion: reduce)') : null;
const motionSnapshot = () => Boolean(systemMotion?.matches);
const subscribeMotion = listener => {
  if (!systemMotion) return () => {};
  if (systemMotion.addEventListener) {
    systemMotion.addEventListener('change', listener);
    return () => systemMotion.removeEventListener('change', listener);
  }
  systemMotion.addListener(listener);
  return () => systemMotion.removeListener(listener);
};

export function useMotionPolicy() {
  // Framer Motion 11's hook snapshots the initial preference. Subscribe here
  // so mounted controls also respond to OS changes without a route reload.
  const appearance = useAppearance(), systemReduced = useSyncExternalStore(subscribeMotion, motionSnapshot, () => true);
  return motionPolicy(appearance, systemReduced);
}

export function MotionProvider({ children }) {
  const { reduced } = useMotionPolicy();
  return <MotionConfig reducedMotion={reduced ? 'always' : 'never'} transition={{ duration: reduced ? 0 : .22, ease: STAK_EASE }}>{children}</MotionConfig>;
}

export function AnimatedSelection({ value, children, className = '', ...props }) {
  const id = useId(), { reduced, transition } = useMotionPolicy();
  return <LayoutGroup id={id}><div {...props} className={`animated-selection ${className}`}>
    {Children.map(children, child => {
      if (!isValidElement(child)) return child;
      const selected = child.props['data-value'] === value;
      return cloneElement(child, { className: `selection-item ${child.props.className || ''}`, 'data-selected': selected }, <>
        {selected && (reduced ? <span className="selection-fill" aria-hidden="true"/> : <motion.span className="selection-fill" aria-hidden="true" layoutId="selected-surface" initial={false} transition={transition}/>)}
        <span className="selection-content">{child.props.children}</span>
      </>);
    })}
  </div></LayoutGroup>;
}

// Initial load is static; only actual navigation/selection changes enter.
// No exit overlay holds an old page or its controls on screen.
export function TransitionPanel({ panelKey, children, className = '', ...props }) {
  const { reduced, duration } = useMotionPolicy();
  return <motion.div key={panelKey} {...props} className={`transition-panel ${className}`} initial={reduced ? false : { opacity: .35, y: 8 }} animate={{ opacity: 1, y: 0 }} transition={{ duration, ease: STAK_EASE }}>{children}</motion.div>;
}
