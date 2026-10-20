import { useEffect, useRef } from 'react';

// One viewport layer outside route transitions; only its glow can animate.
export default function AmbientBackground() {
  const ref = useRef(null);
  useEffect(() => {
    let nativeActive = true;
    const update = () => { if (ref.current) ref.current.dataset.paused = String(document.hidden || !nativeActive); };
    const native = event => { nativeActive = event.detail !== false; update(); };
    update();
    document.addEventListener('visibilitychange', update);
    window.addEventListener('stak-app-active', native);
    return () => { document.removeEventListener('visibilitychange', update); window.removeEventListener('stak-app-active', native); };
  }, []);
  return <div ref={ref} className="stak-ambient" data-testid="ambient-background" aria-hidden="true"><div className="stak-ambient-glow"/><div className="stak-ambient-dots"/></div>;
}
