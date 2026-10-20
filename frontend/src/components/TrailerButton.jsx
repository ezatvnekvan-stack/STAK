import { useEffect, useState } from 'react';
import { Play } from 'lucide-react';
import { api, errMsg } from '../lib/api';
import TrailerDialog from './TrailerDialog';
export default function TrailerButton({ imdbId, type }) {
  const [video, setVideo] = useState(null);
  const [open, setOpen] = useState(false),[loading,setLoading]=useState(true),[error,setError]=useState(''),[retry,setRetry]=useState(0);
  useEffect(() => {
    let live = true;
    setVideo(null); setOpen(false);setLoading(true);setError('');
    if (!imdbId) return () => { live = false; };
    api.get('/meta/trailer', { params: { imdb_id: imdbId, type } })
      .then(({ data }) => { if (live && /^[A-Za-z0-9_-]{11}$/.test(data?.youtube_key || '')) setVideo(data.youtube_key); })
      .catch(failure => {if(live)setError(errMsg(failure));}).finally(()=>{if(live)setLoading(false);});
    return () => { live = false; };
  }, [imdbId, type,retry]);
  return <div className="mb-3 flex flex-wrap items-center gap-2">
    <button type="button" data-testid="watch-trailer" disabled={!video || loading} className="n-chip nothing-action dmm-trailer-btn gap-2" onClick={() => setOpen(true)}><Play size={12} fill="currentColor"/> {loading?'Loading trailer…':video?'Watch trailer':'Trailer unavailable'}</button>
    {error && <span role="status" className="text-xs text-neutral-500">{error} <button className="stak-button stak-button--link" onClick={()=>setRetry(v=>v+1)}>Retry</button></span>}
    {open && <TrailerDialog videos={[{ key: video, name: 'Trailer' }]} initialKey={video} onClose={() => setOpen(false)} />}
  </div>;
}
