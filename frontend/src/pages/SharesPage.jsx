import { useEffect, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { ArrowRight, Share2, Trash2 } from "lucide-react";
import { toast } from "sonner";
import { useApp } from "../context/AppContext";
import { IS_CAPACITOR_OFFLINE, api, errMsg } from "../lib/api";
import { fmtBytes, fmtDate } from "../lib/format";
import { DotLoader, Empty, NeedKey, PageTitle } from "../components/Bits";
import { nativeShare, shareLocalList } from "../components/ShareCreateDialog";

function OpenShare() {
  const nav = useNavigate();
  const [v, setV] = useState("");
  const go = (e) => {
    e.preventDefault();
    const id = v.trim().split("/share/").pop().split(/[?#/]/)[0];
    if (id) nav(`/share/${id}`);
  };
  return (
    <form onSubmit={go} className="mb-6 flex gap-2">
      <input data-testid="open-share-input" value={v} onChange={(e) => setV(e.target.value)} placeholder="Shared link or ID" className="n-input flex-1" />
      <button data-testid="open-share-submit" className="n-icon-btn !h-12 !w-12 !border-white !bg-white !text-black"><ArrowRight size={18} /></button>
    </form>
  );
}

export default function SharesPage() {
  const { key } = useApp();
  const [list, setList] = useState([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!key) return;
    setLoading(true);
    api.get("/shares/mine").then(({ data }) => setList(data)).catch((e) => toast.error(errMsg(e))).finally(() => setLoading(false));
  }, [key]);

  const del = async (id) => {
    try {
      await api.delete(`/shares/${id}`);
      setList((l) => l.filter((x) => x.id !== id));
      toast.success("List deleted");
    } catch (e) {
      toast.error(errMsg(e));
    }
  };

  return (
    <div className="stak-screen stak-screen-shares">
      <PageTitle eyebrow="Hash lists" title="Share" testId="shares-title" />
      {!IS_CAPACITOR_OFFLINE && <OpenShare />}
      {IS_CAPACITOR_OFFLINE && <details className="stak-help"><summary>Sharing limits</summary><p>Lists stay on this device. Export magnet links to share; public hosted links require a server.</p></details>}
      <div className="n-label mb-3">My lists</div>
      {!key ? <NeedKey text="Your lists are tied to your TorBox key. Shared links can be opened without a key." /> : loading ? <DotLoader /> : !list.length ? (
        <Empty title="No lists" text="Select torrents in the Library, then tap the share button." testId="shares-empty" />
      ) : (
        <div data-testid="shares-list" className="space-y-2.5">
          {list.map((s, i) => (
            <div key={s.id} data-testid={`share-item-${i}`} className="n-card rise flex items-center gap-2 p-3">
              <Link to={`/share/${s.id}`} data-testid={`share-open-${i}`} className="min-w-0 flex-1 pl-1">
                <div className="truncate text-sm font-semibold text-white">{s.title}</div>
                <div className="font-nmono text-[10px] text-neutral-500">{s.items.length} torrents · {fmtBytes(s.items.reduce((a, x) => a + x.size, 0))} · {fmtDate(s.created_at)}</div>
              </Link>
              {<button data-testid={`share-send-${i}`} onClick={() => IS_CAPACITOR_OFFLINE ? shareLocalList(s) : nativeShare(s.id, s.title)} className="n-icon-btn !h-10 !w-10"><Share2 size={15} /></button>}
              <button data-testid={`share-delete-${i}`} onClick={() => del(s.id)} className="n-icon-btn !h-10 !w-10 !text-[#ff4d55]"><Trash2 size={15} /></button>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
