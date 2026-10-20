import { uploadTorrent } from "../lib/torrentUpload";
import { useState } from "react";
import { Loader2 } from "lucide-react";
import { toast } from "sonner";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "./ui/dialog";
import { addTorrent, errMsg } from "../lib/api";

export default function AddMagnetDialog({ open, onOpenChange, onDone }) {
  const [text, setText] = useState("");
  const [busy, setBusy] = useState(false);
  const [file, setFile] = useState(null);

  const submit = async () => {
    const lines = text.split(/\r?\n/).map((l) => l.trim()).filter(Boolean);
    if (!lines.length && !file) return;
    setBusy(true);
    let ok = 0;
    let fileFailed = false;
    if (file) { try { await uploadTorrent(file); ok++; setFile(null); } catch (e) { fileFailed = true; toast.error(errMsg(e)); } }
    const failed = [];
    for (const l of lines) {
      try {
        await addTorrent(l.startsWith("magnet:") ? { magnet: l } : { hash: l });
        ok++;
      } catch (e) {
        failed.push(l);
        toast.error(`${l.slice(0, 24)}…: ${errMsg(e)}`);
      }
    }
    setBusy(false);
    if (ok) toast.success(`${ok} torrent(s) added`);
    setText(failed.join("\n"));
    if (ok && !failed.length && !fileFailed) onOpenChange(false);
    if (ok) onDone();
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent data-testid="add-magnet-dialog" className="w-[calc(100%-2rem)] rounded-3xl border-[color:var(--c-2a2a2a)] bg-[color:var(--c-0f0f0f)]">
        <DialogHeader className="text-left">
          <DialogTitle className="font-dot text-2xl text-white">Magnet / hash</DialogTitle>
          <DialogDescription className="text-neutral-400">One magnet link or info hash per line.</DialogDescription>
        </DialogHeader>
        <textarea
          data-testid="add-magnet-textarea"
          value={text}
          onChange={(e) => setText(e.target.value)}
          rows={6}
          placeholder="magnet:?xt=urn:btih:..."
          className="stak-input font-nmono"
        />
        <label className="text-xs text-neutral-400">Or choose a torrent file (max 5 MiB)<input type="file" accept=".torrent,application/x-bittorrent" className="mt-2 block w-full" aria-label="Torrent file" disabled={busy} onChange={e => setFile(e.target.files?.[0] || null)} /></label>
        <button data-testid="add-magnet-submit" onClick={submit} disabled={busy || (!text.trim() && !file)} className="n-btn-red w-full">
          {busy && <Loader2 size={14} className="animate-spin" />} Add to TorBox
        </button>
      </DialogContent>
    </Dialog>
  );
}
