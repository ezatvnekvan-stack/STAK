import { useEffect, useState } from "react";
import { useParams } from "react-router-dom";
import { Check, Copy, Loader2, Plus, Share2, Zap } from "lucide-react";
import { toast } from "sonner";
import { useApp } from "../context/AppContext";
import { IS_CAPACITOR_OFFLINE, addTorrent, api, buildMagnet, checkCached, copyText, errMsg } from "../lib/api";
import { fmtBytes, fmtDate } from "../lib/format";
import { DotLoader, Empty, PageTitle } from "../components/Bits";
import { nativeShare } from "../components/ShareCreateDialog";

export default function ShareViewPage() {
  const { id } = useParams();
  const { key, libraryHashes, refreshLibrary } = useApp();
  const [share, setShare] = useState(null);
  const [error, setError] = useState("");
  const [cached, setCached] = useState(new Set());
  const [busy, setBusy] = useState(null);

  useEffect(() => {
    api.get(`/shares/${id}`).then(({ data }) => {
      setShare(data);
      checkCached(data.items.map((x) => x.hash)).then(setCached).catch(() => {});
    }).catch((e) => setError(errMsg(e)));
  }, [id, key]);

  const add = async (items, tag) => {
    if (!key) return toast.error("Add your TorBox API key first");
    setBusy(tag);
    let ok = 0;
    for (const it of items) {
      if (libraryHashes.has(it.hash.toLowerCase())) continue;
      try {
        await addTorrent({ hash: it.hash, name: it.name });
        ok++;
      } catch (e) {
        toast.error(`${it.name.slice(0, 30)}: ${errMsg(e)}`);
      }
    }
    setBusy(null);
    toast.success(`${ok} torrent(s) added`);
    refreshLibrary().catch(() => {});
  };

  if (error) return <Empty title="Not found" text={error} testId="share-view-error" />;
  if (!share) return <DotLoader />;

  return (
    <div className="stak-screen stak-screen-share">
      <PageTitle eyebrow={`${share.items.length} torrents · ${fmtBytes(share.items.reduce((a, x) => a + x.size, 0))} · ${fmtDate(share.created_at)}`} title={share.title} testId="share-view-title" />
      <div className="mb-5 flex flex-wrap gap-2">
        <button data-testid="share-add-all-button" disabled={!!busy} onClick={() => add(share.items, "all")} className="n-btn-red">
          {busy === "all" ? <Loader2 size={14} className="animate-spin" /> : <Plus size={14} />} Add all
        </button>
        {!IS_CAPACITOR_OFFLINE && <button data-testid="share-view-send-button" onClick={() => nativeShare(share.id, share.title)} className="n-btn-ghost"><Share2 size={14} /> Share</button>}
      </div>
      <div data-testid="share-items-list" className="space-y-2.5">
        {share.items.map((it, i) => {
          const inLib = libraryHashes.has(it.hash.toLowerCase());
          return (
            <div key={it.hash} data-testid={`share-view-item-${i}`} className="n-card rise flex items-center gap-3 p-3">
              <div className="min-w-0 flex-1">
                <div className="break-all text-[13px] font-medium text-white">{it.name || it.hash}</div>
                <div className="mt-2 flex flex-wrap gap-1.5">
                  <span className="glyph">{fmtBytes(it.size)}</span>
                  {cached.has(it.hash.toLowerCase()) && <span className="glyph !border-[#10B981]/50 !text-[#10B981]"><Zap size={10} /> Cache</span>}
                  {inLib && <span className="glyph !border-[#D71921]/60 !text-[#ff4d55]"><Check size={10} /> In library</span>}
                </div>
              </div>
              <button data-testid={`share-view-add-${i}`} disabled={!!busy || inLib} onClick={() => add([it], it.hash)} className="n-icon-btn !border-white !bg-white !text-black">
                {busy === it.hash ? <Loader2 size={16} className="animate-spin" /> : <Plus size={18} />}
              </button>
              <button data-testid={`share-view-copy-${i}`} onClick={() => copyText(buildMagnet(it.hash, it.name)).then(() => toast("Magnet copied"))} className="n-icon-btn"><Copy size={15} /></button>
            </div>
          );
        })}
      </div>
    </div>
  );
}
