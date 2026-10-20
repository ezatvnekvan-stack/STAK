// One policy for React motion, adapted components and optional canvas assets.
export const STAK_EASE = [.22, .68, .18, 1];
export const STAK_SPRING = { type: 'spring', stiffness: 420, damping: 38, mass: .7 };
export function motionPolicy(appearance, systemReduced = false) {
  const reduced = Boolean(appearance?.reducedMotion || systemReduced);
  return { reduced, duration: reduced ? 0 : .22, transition: reduced ? { duration: 0 } : STAK_SPRING };
}
