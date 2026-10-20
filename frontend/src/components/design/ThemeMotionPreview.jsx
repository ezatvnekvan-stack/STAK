import { lazy, Suspense, useEffect, useState } from 'react';
import { Moon, Sun } from 'lucide-react';
import { setTheme, useTheme } from '../../lib/theme';
import { useMotionPolicy } from './MotionSystem';

const RiveThemeSwitch = lazy(() => import('./RiveThemeSwitch'));

export default function ThemeMotionPreview() {
  const { resolved } = useTheme(), { reduced } = useMotionPolicy();
  const capable = typeof window.CanvasRenderingContext2D === 'function' && typeof WebAssembly === 'object';
  const [active, setActive] = useState(() => !document.hidden);
  useEffect(() => {
    let native = true;
    const changed = () => setActive(native && !document.hidden);
    const lifecycle = event => { native = event.detail !== false; changed(); };
    document.addEventListener('visibilitychange', changed);
    window.addEventListener('stak-app-active', lifecycle);
    return () => { document.removeEventListener('visibilitychange', changed); window.removeEventListener('stak-app-active', lifecycle); };
  }, []);
  const Icon = resolved === 'dark' ? Moon : Sun;
  return <button type="button" data-testid="theme-motion-preview" className="theme-motion-preview" aria-label={`Switch to ${resolved === 'dark' ? 'light' : 'dark'} theme`} onClick={() => setTheme(resolved === 'dark' ? 'light' : 'dark')}>
    <span className="theme-motion-static" aria-hidden="true"><Icon size={24}/></span>
    {capable && !reduced && active && <Suspense fallback={null}><RiveThemeSwitch dark={resolved === 'dark'}/></Suspense>}
  </button>;
}
