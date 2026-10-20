import { NavLink, useLocation } from "react-router-dom";
import { useT } from "../lib/i18n";
import {AnimatedSelection} from './design/MotionSystem';

// Jellyfin | TorBox switch shown on both library screens (one "Library" tab in the nav).
export default function LibrarySwitch() {
  const t = useT();
  const {pathname}=useLocation();
  return (
    <AnimatedSelection value={pathname} className="library-switch stak-tabs mb-4">
      {[["/library", "Jellyfin"], ["/torbox", "TorBox"]].map(([to, l]) => (
        <NavLink key={to} data-value={to} to={to} data-testid={`library-switch-${l.toLowerCase()}`}
          className="n-chip">
          {t(l)}
        </NavLink>
      ))}
    </AnimatedSelection>
  );
}
