import { useState } from "react";
import { Link } from "react-router-dom";
import { Check } from "lucide-react";
import { useApp } from "../context/AppContext";
import { useConnections } from "../lib/useConnections";
import { useT } from "../lib/i18n";

const DISMISS = "dmm-onboard-dismissed";
const DONE = "dmm-onboard-done";
const flag = (k) => { try { return localStorage.getItem(k) === "1"; } catch { return false; } };
const setFlag = (k) => { try { localStorage.setItem(k, "1"); } catch { /* ignore */ } };

function Inner() {
  const t = useT();
  const { key } = useApp();
  const { st } = useConnections();
  const [hidden, setHidden] = useState(false);
  const steps = [
    { id: "tb", label: t("TorBox API key"), ok: !!key },
    { id: "jf", label: t("Connect Jellyfin"), ok: st.jf === "ok" },
    { id: "ix", label: t("Add an indexer"), ok: st.ix === "ok" },
  ];
  const loading = !!key && (st.tb === "wait" || st.jf === "wait" || st.ix === "wait");
  const allOk = steps.every((s) => s.ok);
  if (allOk && !loading) setFlag(DONE); // never check again once everything is set up
  if (hidden || loading || allOk) return null;
  return (
    <section data-testid="onboarding-card" className="n-card rise block-gap p-5">
      <h2 className="n-h2 mb-4">{t("Set up STAK")}</h2>
      <ul className="mb-4 space-y-1">
        {steps.map((s) => (
          <li key={s.id} className="flex min-h-[44px] items-center gap-3">
            <span className={`flex h-5 w-5 items-center justify-center rounded-full border ${s.ok ? "border-[#10B981] text-[#10B981]" : "border-[color:var(--c-333333)]"}`}>
              {s.ok ? <Check size={12} strokeWidth={3} /> : <span className="h-1.5 w-1.5 rounded-full bg-[color:var(--n-accent)]" />}
            </span>
            <span className={`text-sm ${s.ok ? "text-neutral-500 line-through" : "text-white"}`}>{s.label}</span>
          </li>
        ))}
      </ul>
      <div className="flex gap-2">
        <Link to="/account" className="n-btn-red flex-1">{t("Open settings")}</Link>
        <button type="button" className="n-btn-ghost" onClick={() => { setFlag(DISMISS); setHidden(true); }}>{t("Later")}</button>
      </div>
    </section>
  );
}

// Wrapper: skip all network checks once the user finished (or dismissed) the setup.
export default function OnboardingCard() {
  if (flag(DONE) || flag(DISMISS)) return null;
  return <Inner />;
}
