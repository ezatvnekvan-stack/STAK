import { useMemo } from "react";
import { Link } from "react-router-dom";
import { useApp } from "../context/AppContext";
import { useT } from "../lib/i18n";
import { cleanName, fmtBytes, statusOf } from "../lib/format";

// Newly finished torrents on TorBox, one tap to the library.
export default function ReadyStrip() {
  const t = useT();
  const { library } = useApp();
  const ready = useMemo(() => library.filter((x) => statusOf(x) === "done")
    .sort((a, b) => new Date(b.created_at || 0) - new Date(a.created_at || 0)).slice(0, 10), [library]);
  if (!ready.length) return null;
  return (
    <section data-testid="home-ready" className="rise section-gap">
      <div className="mb-3 flex items-center justify-between">
        <h2 className="n-h2">{t("Ready on TorBox")}</h2>
        <Link to="/torbox" className="inline-flex min-h-[32px] items-center font-nmono text-[10px] uppercase tracking-widest text-neutral-400 hover:text-white">{t("All")} →</Link>
      </div>
      <div className="no-scrollbar -mx-4 flex snap-x snap-proximity gap-3 overflow-x-auto scroll-px-4 px-4 pb-1">
        {ready.map((x) => (
          <Link key={x.id} to="/torbox" className="n-card w-44 shrink-0 snap-start p-3">
            <div className="glyph mb-2 !border-[#10B981]/50 !text-[#10B981]"><span className="h-1.5 w-1.5 rounded-full bg-[#10B981]" />{t("Ready")}</div>
            <div className="line-clamp-2 min-h-[2.5rem] text-[13px] leading-snug text-white">{cleanName(x.name)}</div>
            <div className="mt-2 font-nmono text-[10px] text-neutral-400">{fmtBytes(x.size)}</div>
          </Link>
        ))}
      </div>
    </section>
  );
}
