import ProgressIndicator from './ProgressIndicator';
import { Link } from "react-router-dom";
import { Film, KeyRound } from "lucide-react";
import { useT } from "../lib/i18n";

export const PageTitle = ({ eyebrow, title, right, testId }) => (
  <div className="stak-page-header rise">
    <div className="min-w-0">
      {eyebrow && <div className="n-label mb-2">{eyebrow}</div>}
      <h1 tabIndex={-1} data-testid={testId} className="stak-page-title font-dot text-white">
        {title}
      </h1>
    </div>
    {right}
  </div>
);

// Dot-matrix wave instead of a spinner.
export const DotLoader = ({ label = "Loading" }) => {
  const t = useT();
  return (
    <div data-testid="loading-indicator" role="status" className="flex flex-col items-center justify-center gap-4 py-16">
      <div className="grid grid-cols-5 gap-1.5">
        {Array.from({ length: 15 }, (_, i) => (
          <span key={i} className="dot-wave h-1.5 w-1.5 rounded-full bg-white" style={{ animationDelay: `${((i % 5) + Math.floor(i / 5)) * 0.12}s` }} />
        ))}
      </div>
      <span className="n-label">{t(label)}</span>
    </div>
  );
};

// One empty / error state for the whole app: Doto title, one sentence, one optional action.
export const Empty = ({ title, text, children, testId, to, actionLabel, onAction }) => (
  <div data-testid={testId} className="n-card stak-empty rise flex flex-col items-center gap-3 px-6 py-12 text-center">
    <span className="state-glyph" aria-hidden="true"><Film size={24}/></span>
    <div className="font-dot text-2xl text-white">{title}</div>
    {text && <p className="max-w-sm text-sm text-neutral-400">{text}</p>}
    {to && actionLabel && <Link to={to} className="n-btn-ghost mt-1">{actionLabel}</Link>}
    {!to && onAction && actionLabel && <button onClick={onAction} className="n-btn-ghost mt-1">{actionLabel}</button>}
    {children}
  </div>
);

export const NeedKey = ({ text = "This feature needs a TorBox API key." }) => (
  <Empty title="No key" text={text} testId="need-key-banner">
    <Link to="/account" data-testid="need-key-go-account" className="n-btn-red mt-2">
      <KeyRound size={14} /> Add key
    </Link>
  </Empty>
);

// Highlights the matching part of a title in search results.
export const Highlight = ({ text, term }) => {
  const s = String(text || "");
  const q = String(term || "").trim();
  if (!q) return s;
  const i = s.toLowerCase().indexOf(q.toLowerCase());
  if (i < 0) return s;
  return (
    <>
      {s.slice(0, i)}
      <span className="text-[color:var(--n-accent-ink)]">{s.slice(i, i + q.length)}</span>
      {s.slice(i + q.length)}
    </>
  );
};

// 10-dot rating bar (value on a 0..max scale).
export const DotBar = ({ value, max = 10, dots = 10, className = "" }) => {
  const n = Math.max(0, Math.min(dots, Math.round((Number(value) / max) * dots)));
  return (
    <span className={`inline-flex items-center gap-[3px] ${className}`} role="img" aria-label={`${value} / ${max}`}>
      {Array.from({ length: dots }, (_, i) => (
        <span key={i} className={`h-1 w-1 rounded-full ${i < n ? "bg-[color:var(--n-accent)]" : "bg-[color:var(--c-333333)]"}`} />
      ))}
    </span>
  );
};

// Backward-compatible dot variant, sharing clamping and accessibility semantics.
export const DotProgress=({pct=0,dots=24})=><ProgressIndicator value={pct} variant="dots" label="Progress" style={{'--progress-segments':Math.max(2,Math.min(48,Number(dots)||24))}}/>;

// Ring of dots that flies outward: success (green) or error (red). Parent must be `relative`.
export const GlyphBurst = ({ run, tone = "ok" }) => {
  if (!run) return null;
  return (
    <span aria-hidden="true" className="glyph-burst" style={{ "--burst": tone === "ok" ? "#10B981" : "#D71921" }}>
      {Array.from({ length: 10 }, (_, i) => <i key={i} style={{ "--a": `${i * 36}deg` }} />)}
    </span>
  );
};
