import { useEffect } from "react";

// Sets --py on the element while the page scrolls (use with the .parallax class).
export function useParallax(target, factor = 0.3) {
  useEffect(() => {
    const el = target && typeof target === "object" && "current" in target ? target.current : target;
    if (!el || (window.matchMedia && window.matchMedia("(prefers-reduced-motion: reduce)").matches)) return undefined;
    let raf = 0;
    const update = () => { raf = 0; el.style.setProperty("--py", `${Math.min(window.scrollY, 700) * factor}px`); };
    const onScroll = () => { if (!raf) raf = requestAnimationFrame(update); };
    update();
    window.addEventListener("scroll", onScroll, { passive: true });
    return () => { window.removeEventListener("scroll", onScroll); if (raf) cancelAnimationFrame(raf); };
  }, [target, factor]);
}
