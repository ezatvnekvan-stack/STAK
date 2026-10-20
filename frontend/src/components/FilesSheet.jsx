import { useEffect, useMemo, useRef, useState } from "react";
import { ChevronDown, Copy, Download, ExternalLink, File as FileIcon, FileArchive, FileAudio, FileText, FileVideo, Folder, Loader2, Link2 } from "lucide-react";
import { toast } from "sonner";
import { Drawer, DrawerContent, DrawerDescription, DrawerHeader, DrawerTitle } from "./ui/drawer";
import { api, buildMagnet, copyText, errMsg, openExternal } from "../lib/api";
import {torrentPresentation} from "../lib/collectionModel";
import { fmtBytes, fmtDate, statusOf } from "../lib/format";
import { useT } from "../lib/i18n";

const kindOf = (name) => {
  const ext = String(name || "").split(".").pop().toLowerCase();
  if (["mkv", "mp4", "avi", "mov", "m4v", "ts", "webm", "wmv"].includes(ext)) return FileVideo;
  if (["srt", "ass", "ssa", "sub", "vtt", "idx", "nfo", "txt"].includes(ext)) return FileText;
  if (["mka", "mp3", "flac", "aac", "ac3", "dts", "eac3", "opus"].includes(ext)) return FileAudio;
  return FileIcon;
};

// Group files by their folder. A folder shared by every file (the torrent root) is dropped from the label.
function groupFiles(files) {
  const dirs = files.map((f) => String(f.name || "").split("/").slice(0, -1));
  const root = dirs.length && dirs.every((d) => d[0] && d[0] === dirs[0][0]) ? dirs[0][0] : null;
  const map = new Map();
  files.forEach((f, i) => {
    const parts = root ? dirs[i].slice(1) : dirs[i];
    const label = parts.join(" / ");
    if (!map.has(label)) map.set(label, []);
    map.get(label).push(f);
  });
  return [...map.entries()].sort(([a], [b]) => a.localeCompare(b, undefined, { numeric: true }));
}

function FileRow({ file, i, url, onLink, busy }) {
  const tr = useT();
  const Kind = kindOf(file.short_name || file.name);
  return (
    <div data-testid={`file-row-${i}`} className="flex items-center gap-3 border-b border-[color:var(--c-1c1c1c)] py-3 last:border-0">
      <Kind size={16} className="shrink-0 text-neutral-400" aria-hidden="true" />
      <div className="min-w-0 flex-1">
        <div className="break-all text-[13px] text-white">{file.short_name || file.name}</div>
        <div className="font-nmono text-[10px] text-neutral-500">{fmtBytes(file.size)}</div>
      </div>
      {url ? (
        <>
          <a data-testid={`file-open-link-${i}`} href={url} onClick={e => { e.preventDefault(); openExternal(url)?.catch?.(error => toast.error(errMsg(error))); }} target="_blank" rel="noreferrer" className="n-icon-btn !h-10 !w-10 !border-[#10B981] !text-[#10B981]"><ExternalLink size={15} /></a>
          <button data-testid={`file-copy-link-${i}`} onClick={() => copyText(url).then(() => toast("Link copied"))} className="n-icon-btn !h-10 !w-10"><Copy size={15} /></button>
        </>
      ) : (
        <button data-testid={`generate-download-link-button-${i}`} disabled={busy} onClick={() => onLink(file.id)} className="n-btn-ghost !min-h-[44px] !px-4">
          {busy ? <Loader2 size={14} className="animate-spin" /> : <Link2 size={14} />} {tr("Link")}
        </button>
      )}
    </div>
  );
}

export default function FilesSheet({ t, onClose }) {
  const tr = useT();
  const [openGroups, setOpenGroups] = useState(() => new Set());
  const groups = useMemo(() => groupFiles(t?.files || []), [t]);
  const flat = groups.length <= 1 || (t?.files || []).length <= 8;
  const toggleGroup = (label) => setOpenGroups((s) => { const n = new Set(s); n.has(label) ? n.delete(label) : n.add(label); return n; });
  const [links, setLinks] = useState({});
  const [busy, setBusy] = useState(null);
  const currentId = useRef(t?.id); currentId.current = t?.id;
  useEffect(() => { setLinks({}); setBusy(null); setOpenGroups(new Set()); }, [t?.id]);
  const ready = t && statusOf(t) === "done";

  const getLink = async (fileId, zip = false) => {
    const { data } = await api.get("/torbox/download", { params: { torrent_id: t.id, file_id: fileId, zip } });
    return data.url;
  };
  const onLink = async (fileId) => {
    const torrentId = t.id;
    setBusy(fileId);
    try {
      const url = await getLink(fileId);
      if (currentId.current === torrentId) setLinks((l) => ({ ...l, [fileId]: url }));
    } catch (e) {
      toast.error(errMsg(e));
    } finally {
      if (currentId.current === torrentId) setBusy(null);
    }
  };
  const allLinks = async () => {
    const torrentId = t.id;
    setBusy("all");
    try {
      const out = {};
      for (const f of t.files || []) { if (currentId.current !== torrentId) return; out[f.id] = links[f.id] || (await getLink(f.id)); }
      if (currentId.current !== torrentId) return;
      setLinks(out);
      await copyText(Object.values(out).join("\n"));
      toast.success(`${Object.keys(out).length} links copied`);
    } catch (e) {
      toast.error(errMsg(e));
    } finally {
      if (currentId.current === torrentId) setBusy(null);
    }
  };
  const zip = async () => {
    const torrentId = t.id;
    setBusy("zip");
    try {
      const url = await getLink(undefined, true);
      if (currentId.current === torrentId) await openExternal(url);
    } catch (e) {
      toast.error(errMsg(e));
    } finally {
      if (currentId.current === torrentId) setBusy(null);
    }
  };

  return (
    <Drawer open={!!t} onOpenChange={(o) => !o && onClose()}>
      <DrawerContent data-testid="files-sheet" className="max-h-[88dvh] border-[color:var(--c-2a2a2a)] bg-[color:var(--c-0d0d0d)]">
        {t && (
          <div className="mx-auto flex w-full max-w-2xl flex-col overflow-hidden px-4 pb-6">
            <DrawerHeader className="px-0 text-left">
              <div className="n-label">{tr("Files")} · {(t.files || []).length} · {fmtBytes(t.size)}</div>
              <DrawerTitle className="font-semibold text-xl leading-tight text-white">{torrentPresentation(t).title}</DrawerTitle>
              <DrawerDescription className="font-nmono text-[10px] text-neutral-500">{torrentPresentation(t).coordinates || "Torrent files and download actions"}</DrawerDescription>
              <details className="collection-original"><summary>Original filename and hash</summary><p>{t.name || "No filename reported"}</p><p className="mt-2 font-nmono">{t.hash}</p><p className="mt-2">Added {fmtDate(t.created_at)} · Reported state: {t.download_state || "Not supplied"}</p></details>
            </DrawerHeader>
            <div className="mb-3 flex flex-wrap gap-2">
              <button data-testid="copy-all-links-button" disabled={!ready || busy !== null} onClick={allLinks} className="n-btn-primary !min-h-[44px]">
                {busy === "all" ? <Loader2 size={14} className="animate-spin" /> : <Download size={14} />} {tr("All links")}
              </button>
              <button data-testid="zip-download-button" disabled={!ready || busy !== null} onClick={zip} className="n-btn-ghost !min-h-[44px]"><FileArchive size={14} /> ZIP</button>
              <button data-testid="sheet-copy-magnet-button" onClick={() => copyText(buildMagnet(t.hash, t.name)).then(() => toast("Magnet copied"))} className="n-btn-ghost !min-h-[44px]"><Copy size={14} /> {tr("Magnet")}</button>
            </div>
            {!ready && <div className="mb-2 text-xs text-[#F59E0B]">Torrent not ready yet — links are available once it finishes downloading.</div>}
            <div className="overflow-y-auto">
              {flat
                ? (t.files || []).map((f, i) => <FileRow key={f.id} file={f} i={i} url={links[f.id]} onLink={onLink} busy={busy === f.id || !ready} />)
                : groups.map(([label, list], gi) => {
                    const open = openGroups.has(label);
                    const total = list.reduce((a, f) => a + (f.size || 0), 0);
                    return (
                      <section key={label || `root-${gi}`} className="mb-2 rounded-2xl border border-[color:var(--c-1f1f1f)]">
                        <button onClick={() => toggleGroup(label)} aria-expanded={open} className="flex min-h-[48px] w-full items-center gap-3 px-3 text-left">
                          <Folder size={16} className="shrink-0 text-neutral-400" />
                          <span className="min-w-0 flex-1 truncate text-[13px] text-white">{label || tr("Folder")}</span>
                          <span className="font-nmono text-[10px] text-neutral-400">{list.length} · {fmtBytes(total)}</span>
                          <ChevronDown size={15} className={`shrink-0 text-neutral-400 transition-transform ${open ? "rotate-180" : ""}`} />
                        </button>
                        {open && <div className="px-3">{list.map((f) => <FileRow key={f.id} file={f} i={f.id} url={links[f.id]} onLink={onLink} busy={busy === f.id || !ready} />)}</div>}
                      </section>
                    );
                  })}
            </div>
          </div>
        )}
      </DrawerContent>
    </Drawer>
  );
}
