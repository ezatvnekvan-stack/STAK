import { writeStored } from "../lib/storage";
import { useState } from "react";
import { ChevronDown, ChevronUp } from "lucide-react";
import { NAV_ITEMS, NAV_PREF_KEY, readNavPrefs } from "./AppShell";
import { Switch } from "./ui/switch";
import { useT } from "../lib/i18n";

export default function NavigationSettings() {
  const t = useT();
  const [ids, setIds] = useState(readNavPrefs);
  const [error, setError] = useState('');
  const save = (next) => {
    if (!writeStored(NAV_PREF_KEY, JSON.stringify(next))) { setError('Navigation could not be saved. Your previous layout is unchanged.'); return; }
    setError('');
    setIds(next);
    window.dispatchEvent(new Event("dmm-nav-change"));
  };
  const move = (id, delta) => {
    const a = [...ids], i = a.indexOf(id), j = i + delta;
    if (j < 0 || j >= a.length) return;
    [a[i], a[j]] = [a[j], a[i]];
    save(a);
  };
  return (
    <section className="n-card p-5">
      <h2 className="n-h2 mb-2">{t("Taskbar")}</h2>
      {error && <p role="alert" className="mb-3 text-xs text-red-400">{error}</p>}
      <p className="mb-4 text-xs text-neutral-400">{t("Choose tabs and their order. Home and Settings always stay available.")}</p>
      <div className="space-y-2">
        {[...ids.map(id=>NAV_ITEMS.find(item=>item.id===id)).filter(Boolean),...NAV_ITEMS.filter(item=>!ids.includes(item.id))].map((item) => {
          const on = ids.includes(item.id);
          const locked = item.id === "home" || item.id === "account";
          return (
            <div key={item.id} className="flex min-h-[56px] items-center gap-2 rounded-2xl border border-[color:var(--c-2a2a2a)] px-3">
              <item.icon size={16} className="shrink-0 text-neutral-400" aria-hidden="true" />
              <span className="flex-1 text-sm text-white">{t(item.label)}</span>
              <button aria-label={`Move ${item.label} up`} disabled={!on || ids.indexOf(item.id) === 0} onClick={() => move(item.id, -1)} className="n-icon-btn !h-10 !w-10"><ChevronUp size={14} /></button>
              <button aria-label={`Move ${item.label} down`} disabled={!on || ids.indexOf(item.id) === ids.length - 1} onClick={() => move(item.id, 1)} className="n-icon-btn !h-10 !w-10"><ChevronDown size={14} /></button>
              <Switch checked={on} disabled={locked} aria-label={t(item.label)} onCheckedChange={(v) => save(v ? [...ids, item.id] : ids.filter((x) => x !== item.id))} />
            </div>
          );
        })}
      </div>
    </section>
  );
}
