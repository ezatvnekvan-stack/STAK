import { Capacitor } from "@capacitor/core";
import { Share } from "@capacitor/share";
import { useState } from "react";
import { Copy, Loader2, Share2 } from "lucide-react";
import { toast } from "sonner";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "./ui/dialog";
import { IS_CAPACITOR_OFFLINE, api, copyText, errMsg } from "../lib/api";

export const shareUrl = (id) => `${window.location.origin}/share/${id}`;

export const exportMagnetList = (list) => {
  const items = Array.isArray(list?.items) ? list.items : [];
  const lines = items.filter((item) => /^[a-f0-9]{40}$|^[a-z2-7]{32}$/i.test(item.hash || ""))
    .map((item) => `magnet:?xt=urn:btih:${item.hash}&dn=${encodeURIComponent(item.name || "")}`);
  return [`# ${list?.title || "STAK hash list"}`, ...lines].join("\n");
};

export const shareLocalList = async (list) => {
  const text = exportMagnetList(list);
  if (!list?.items?.length || !text.includes("magnet:?")) { toast.error("No valid torrent hashes to share"); return; }
  if (Capacitor.isNativePlatform()) { try { await Share.share({ title: list.title || "STAK hash list", text }); return; } catch (error) { if (error?.name === "AbortError" || /cancel/i.test(error?.message || "")) return; } }
  if (navigator.share) {
    try { await navigator.share({ title: list.title || "STAK hash list", text }); return; }
    catch (error) { if (error?.name === "AbortError") return; }
  }
  await copyText(text);
  toast.success("Magnet list copied");
};

export const nativeShare = async (id, title) => {
  if (IS_CAPACITOR_OFFLINE) { toast.error("Use Export magnets to share an offline list."); return; }
  const url = shareUrl(id);
  if (window.DMMApp) return window.DMMApp.share(title, url);
  if (Capacitor.isNativePlatform()) { await Share.share({ title, url }); return; }
  if (navigator.share) {
    try {
      await navigator.share({ title, url });
      return;
    } catch {
      /* fall through to copy */
    }
  }
  await copyText(url);
  toast("Link copied");
};

export default function ShareCreateDialog({ items, open, onOpenChange }) {
  const [title, setTitle] = useState("");
  const [busy, setBusy] = useState(false);
  const [created, setCreated] = useState(null);

  const create = async () => {
    setBusy(true);
    try {
      const { data } = await api.post("/shares", {
        title: title || `Hash list · ${items.length} torrents`,
        items: items.map((t) => ({ hash: t.hash, name: t.name, size: t.size || 0 })),
      });
      setCreated(data);
    } catch (e) {
      toast.error(errMsg(e));
    } finally {
      setBusy(false);
    }
  };
  const close = (o) => {
    if (!o) {
      setCreated(null);
      setTitle("");
    }
    onOpenChange(o);
  };

  return (
    <Dialog open={open} onOpenChange={close}>
      <DialogContent data-testid="share-create-dialog" className="w-[calc(100%-2rem)] rounded-3xl border-[color:var(--c-2a2a2a)] bg-[color:var(--c-0f0f0f)]">
        <DialogHeader className="text-left">
          <DialogTitle className="font-dot text-2xl text-white">{created ? "Done" : "Hash list"}</DialogTitle>
          <DialogDescription className="text-neutral-400">{items.length} selected torrents — {IS_CAPACITOR_OFFLINE ? "save a list on this device" : "share with a link"}.</DialogDescription>
        </DialogHeader>
        {created ? (
          <div className="space-y-3">
            <div data-testid="share-created-url" className="break-all rounded-2xl border border-[color:var(--c-2a2a2a)] bg-black p-4 font-nmono text-xs text-white">{IS_CAPACITOR_OFFLINE ? "Saved locally — export the magnet links to share" : shareUrl(created.id)}</div>
            {<div className="flex gap-2">
              <button data-testid="share-copy-url-button" onClick={() => IS_CAPACITOR_OFFLINE ? copyText(exportMagnetList(created)).then(() => toast("Magnet list copied")) : copyText(shareUrl(created.id)).then(() => toast("Link copied"))} className="n-btn-ghost flex-1"><Copy size={14} /> {IS_CAPACITOR_OFFLINE ? "Copy magnets" : "Copy"}</button>
              <button data-testid="share-native-button" onClick={() => IS_CAPACITOR_OFFLINE ? shareLocalList(created) : nativeShare(created.id, created.title)} className="n-btn-red flex-1"><Share2 size={14} /> {IS_CAPACITOR_OFFLINE ? "Export magnets" : "Share"}</button>
            </div>}
          </div>
        ) : (
          <>
            <input data-testid="share-title-input" value={title} onChange={(e) => setTitle(e.target.value)} placeholder="List name (optional)" className="n-input" />
            <button data-testid="share-create-submit" onClick={create} disabled={busy} className="n-btn-red w-full">
              {busy && <Loader2 size={14} className="animate-spin" />} {IS_CAPACITOR_OFFLINE ? "Save list" : "Create link"}
            </button>
          </>
        )}
      </DialogContent>
    </Dialog>
  );
}
