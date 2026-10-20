import { setAppearance, useAppearance, RATING_SOURCES, APPEARANCE_DEFAULTS } from "../lib/appearance";
import { useState } from "react";
import { Check } from "lucide-react";
import ThemeMotionPreview from "./design/ThemeMotionPreview";
import { Switch } from "./ui/switch";
import { useT } from "../lib/i18n";
import { buzz, hapticsOn, setHaptics } from "../lib/haptics";
import { ACCENTS, setAccent, setTheme, useTheme, resolveAccent } from "../lib/theme";

export default function AppearanceSettings() {
  const appearance = useAppearance();
  const t = useT();
  const { theme, accent } = useTheme();
  const [haptics, setH] = useState(hapticsOn);
  return (
    <section className="n-card p-5">
      <div className="appearance-preview" role="group" aria-label={`Appearance preview: ${theme} theme, ${ACCENTS[accent]?.label || 'custom'} accent${appearance.amoled?', AMOLED enabled':''}`}><ThemeMotionPreview/><span className="n-label">LIVE PREVIEW</span><strong className="font-dot">Make it yours.</strong><div className="appearance-preview-cards" aria-hidden="true"><i/><i/><i/></div><span className="appearance-preview-accent">Your accent · your atmosphere</span></div>
      <div><div className="n-label mb-3">{t("Theme")}</div>
      <div className="mb-5 flex gap-2" role="radiogroup" aria-label={t("Theme")}>
        {[["dark", "Dark"], ["light", "Light"], ["system", "System"]].map(([v, l]) => (
          <button key={v} role="radio" aria-checked={theme === v} data-active={theme === v} onClick={() => { buzz(8); setTheme(v); }} className="n-chip !h-11 flex-1 justify-center">{t(l)}</button>
        ))}
      </div>
      <div className="n-label mb-3">{t("Accent")} · {ACCENTS[accent]?.label || "Custom"}</div>
      <div className="accent-options mb-5" role="radiogroup" aria-label={t("Accent")}>
        {Object.entries(ACCENTS).map(([k, a]) => (
          <button key={k} role="radio" aria-checked={accent === k} aria-label={t(a.label)} title={t(a.label)} onClick={() => { buzz(8); setAccent(k); }}
            className="relative flex h-11 w-11 items-center justify-center rounded-full border border-[color:var(--c-333333)] active:scale-90"
            style={{ boxShadow: accent === k ? `0 0 0 2px var(--c-111111), 0 0 0 4px ${a.color}` : undefined }}>
            <span className="h-6 w-6 rounded-full" style={{ background: a.color }} />
            {accent === k && <Check size={13} className="absolute" style={{ color: resolveAccent(k).fg }} strokeWidth={3} />}
          </button>
        ))}
      </div>
      <label className="accent-custom"><span className="n-label">Custom colour</span><input aria-label="Custom accent colour" type="color" value={resolveAccent(accent).color} onInput={event=>setAccent(event.currentTarget.value)}/><span className="font-nmono text-xs">{resolveAccent(accent).color.toUpperCase()}</span></label>
      </div>
      {Object.entries({ reducedMotion: "Reduce animations", compact: "Compact layout", amoled: "AMOLED black" }).map(([setting, label]) => <label key={setting} className="flex min-h-[52px] items-center justify-between gap-4"><span className="text-sm">{label}</span><Switch aria-label={label} checked={appearance[setting]} onCheckedChange={on => setAppearance({ [setting]: on })} /></label>)}
      <div className="n-label mt-4 mb-2">Atmosphere</div>
      {[["dotIntensity", "Dot Matrix intensity", 10], ["glowIntensity", "Ambient Glow intensity", 30]].map(([setting, label, max]) => <label key={setting} className="ambient-setting"><span className="flex justify-between gap-3 text-sm"><span>{label}</span><output>{appearance[setting]}%</output></span><input type="range" min="0" max={max} step="1" value={appearance[setting]} aria-label={label} onChange={event => setAppearance({ [setting]: Number(event.target.value) })}/></label>)}
      {Object.entries({ ambientAnimation: "Ambient animation", cinematicColors: "Cinematic adaptive colours" }).map(([setting, label]) => <label key={setting} className="flex min-h-[52px] items-center justify-between gap-4"><span className="text-sm">{label}</span><Switch aria-label={label} checked={appearance[setting]} onCheckedChange={on => setAppearance({ [setting]: on })}/></label>)}
      <button className="n-chip my-3" onClick={() => { setTheme('dark'); setAccent('red'); setAppearance(APPEARANCE_DEFAULTS); }}>Reset appearance</button>
      <div className="n-label mt-4">Visible ratings</div>
      {Object.entries(RATING_SOURCES).map(([source, label]) => <label key={source} className="flex min-h-[48px] items-center justify-between gap-4"><span className="text-sm">{label}</span><Switch aria-label={`Show ${label}`} checked={appearance.ratings[source]} onCheckedChange={on => setAppearance({ ratings: { [source]: on } })} /></label>)}
      <label className="flex min-h-[52px] items-center justify-between gap-4">
        <span>
          <span className="block text-sm text-white">{t("Haptics")}</span>

        </span>
        <Switch checked={haptics} onCheckedChange={(on) => { if (setHaptics(on)) { setH(on); if (on) buzz(12); } }} aria-label={t("Haptics")} />
      </label>
    </section>
  );
}
