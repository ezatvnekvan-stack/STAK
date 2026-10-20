import { useEffect } from 'react';
import { useAppearance } from '../lib/appearance';
import { getArtworkPalette, GRAPHITE } from '../lib/artworkPalette';

// Feed the single shared viewport environment; never create a second image layer.
export default function TitleAmbient({src}){
  const { cinematicColors } = useAppearance();
  useEffect(() => {
    let live = true;
    const shell = document.querySelector('[data-app-shell]');
    if (!shell) return undefined;
    shell.dataset.cinematic = 'true';
    shell.style.setProperty('--stak-artwork-rgb', GRAPHITE);
    if (src && cinematicColors) getArtworkPalette(src).then(color => { if (live) shell.style.setProperty('--stak-artwork-rgb', color); });
    return () => { live = false; delete shell.dataset.cinematic; shell.style.removeProperty('--stak-artwork-rgb'); };
  }, [src, cinematicColors]);
  return null;
}
