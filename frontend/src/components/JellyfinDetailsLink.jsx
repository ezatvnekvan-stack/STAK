import JellyfinMatchDialog from './JellyfinMatchDialog';
import MediaQuickActions from "./MediaQuickActions";
import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { api, errMsg } from '../lib/api';
import { detailsHref, jellyfinIdentity } from '../lib/titleIdentity';

export default function JellyfinDetailsLink({ item, className = '', children, ...props }) {
  const direct = detailsHref(jellyfinIdentity(item));
  const navigate = useNavigate(), pending = useRef(false);
  const [error, setError] = useState(''),[matchOpen,setMatchOpen]=useState(false);
  const lifecycle = useRef({active:true});
  useEffect(()=>{const marker={active:true};lifecycle.current=marker;pending.current=false;return()=>{marker.active=false;};},[item.Id]);
  const open = async event => {
    if (direct) return;
    event.preventDefault();
    if (pending.current) return;
    pending.current = true; const marker=lifecycle.current; setError('');
    try {
      const { data } = await api.get('/jellyfin/resolve-title', { params: { item_id: item.Id } });
      if (!marker.active) return;
      const href = detailsHref(data.identity);
      if (href) navigate(href);
      else setError(data.reason || 'No reliable metadata identifier was found.');
    } catch (failure) { if (marker.active) setError(errMsg(failure)); }
    finally { pending.current = false; }
  };
  return <div className="min-w-0 shrink-0 snap-start"><MediaQuickActions item={item}><Link {...props} className={`block ${className}`} to={direct || `/jellyfin/item/${item.Id}`} onClick={open}>{children}</Link></MediaQuickActions>{error && <div role="alert" className="mt-2 max-w-xs text-xs text-neutral-400"><p>{error}</p><Link className="stak-button stak-button--link" to={`/search?q=${encodeURIComponent(item.SeriesName || item.Name || '')}`}>Search title</Link> · <button className="stak-button stak-button--link" onClick={()=>setMatchOpen(true)}>Match manually</button> · <button className="stak-button stak-button--link" onClick={open}>Retry match</button></div>}{matchOpen && <JellyfinMatchDialog item={item} open={matchOpen} onOpenChange={setMatchOpen}/>}</div>;
}
