import { createPortal } from "react-dom";
import { Loader2, RotateCw, Share2, Trash2, X } from "lucide-react";
import { useT } from "../lib/i18n";
import { buzz } from "../lib/haptics";

export default function BulkBar({ count, busy, onDelete, onShare, onReannounce, onClear }) {
  const t = useT();
  if (!count) return null;
  const act = (fn) => () => { buzz(10); fn(); };
  return createPortal(
    <div data-testid="bulk-action-bar" role="toolbar" aria-label={t("selected")} className="sheet-up fixed inset-x-3 bottom-[calc(76px+env(safe-area-inset-bottom))] z-[60] mx-auto flex max-w-xl items-center gap-2 rounded-full border border-[color:var(--c-333333)] bg-[rgb(var(--c-141414-rgb)/0.95)] p-1.5 pl-4 backdrop-blur-xl">
      <span data-testid="bulk-selected-count" className="font-dot text-xl text-white">{count}</span>
      <span className="n-label flex-1">{t("selected")}</span>
      <button data-testid="reannounce-selected-button" disabled={busy} onClick={act(onReannounce)} className="n-icon-btn !h-11 !w-11" aria-label="Reannounce"><RotateCw size={15} /></button>
      <button data-testid="create-share-list-button" disabled={busy} onClick={act(onShare)} className="n-icon-btn !h-11 !w-11" aria-label="Share"><Share2 size={15} /></button>
      <button data-testid="delete-selected-torrents-button" disabled={busy} onClick={act(onDelete)} className="n-icon-btn !h-11 !w-11 !border-[#D71921] !text-[#ff4d55]" aria-label={t("Delete")}>
        {busy ? <Loader2 size={15} className="animate-spin" /> : <Trash2 size={15} />}
      </button>
      <button data-testid="clear-selection-button" disabled={busy} onClick={onClear} className="n-icon-btn !h-11 !w-11 !border-transparent" aria-label="Clear selection"><X size={15} /></button>
    </div>,
    document.body
  );
}
