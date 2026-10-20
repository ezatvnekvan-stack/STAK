import { NavLink } from "react-router-dom";
import { useT } from "../lib/i18n";

export default function ListSwitch() {
  const t = useT();
  return (
    <div className="mb-4 flex overflow-x-auto rounded-full border border-[color:var(--c-2a2a2a)] p-1">
      {[["/watchlist", "Watchlist"], ["/history", "History"], ["/progress", "Progress"], ["/calendar", "Calendar"]].map(([to, l]) => (
        <NavLink key={to} to={to} data-testid={`list-switch-${l.toLowerCase()}`}
          className={({ isActive }) => `rounded-full px-3.5 py-2 font-nmono text-[11px] uppercase tracking-wider ${isActive ? "bg-white text-black" : "text-neutral-400"}`}>
          {t(l)}
        </NavLink>
      ))}
    </div>
  );
}
