import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { api, errMsg, openExternal } from '../lib/api';
import { detailsHref } from '../lib/titleIdentity';

// Compatibility for saved Jellyfin links. The former playback page has been removed.
export default function JellyfinItemPage() {
  const { id } = useParams(), navigate = useNavigate();
  const [result, setResult] = useState(null), [error, setError] = useState(''), [retry, setRetry] = useState(0);
  useEffect(() => {
    let active = true; setResult(null); setError('');
    api.get('/jellyfin/resolve-title', { params: { item_id: id } }).then(({ data }) => {
      if (!active) return;
      const href = detailsHref(data.identity);
      if (href) navigate(href, { replace: true }); else setResult(data);
    }).catch(failure => { if (active) setError(errMsg(failure)); });
    return () => { active = false; };
  }, [id, navigate, retry]);
  if (!result && !error) return <p role="status" className="n-card p-4 text-sm">Finding title details…</p>;
  return <div className="n-card space-y-3 p-4"><h1 className="font-dot text-2xl">Title match unavailable</h1><p role="alert" className="text-sm text-neutral-400">{error || result?.reason}</p><div className="flex flex-wrap gap-2"><button className="n-chip" onClick={() => setRetry(value => value + 1)}>Retry match</button><Link className="n-chip" to={`/search?q=${encodeURIComponent(result?.name || '')}`}>Search title</Link><Link className="n-chip" to="/library">Back to library</Link>{result?.web_url && <button className="n-chip" onClick={() => openExternal(result.web_url)}>Open Jellyfin externally</button>}</div></div>;
}
