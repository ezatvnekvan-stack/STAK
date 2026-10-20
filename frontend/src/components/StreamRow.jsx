import { useNavigate } from 'react-router-dom';
import { useState } from 'react';
import { Check, Copy, Loader2, Plus, Sparkles, Zap } from 'lucide-react';
import { toast } from 'sonner';
import { addTorrent, buildMagnet, copyText, errMsg } from '../lib/api';
import { fmtBytes } from '../lib/format';
import { useApp } from '../context/AppContext';
import { buzz } from '../lib/haptics';
import {sourceBadges,sourceSize} from '../lib/sourcePresentation';
import { qualityTags } from '../lib/mediaQuality';
import { releaseMatch } from '../lib/releaseMatch';

export default function StreamRow({s,index,cached,best=false,pack=null,matchContext}) {
  const {key,library=[],libraryHashes,refreshLibrary}=useApp();
  const [busy,setBusy]=useState(false),[added,setAdded]=useState(false),[failure,setFailure]=useState('');
  const navigate=useNavigate();
  const existing=library.find(item=>String(item.hash || '').toLowerCase()===String(s.hash || '').toLowerCase());
  const inLib=!!s.hash && libraryHashes.has(String(s.hash).toLowerCase());
  const magnet=s.magnet?.startsWith('magnet:')?s.magnet:buildMagnet(s.hash,s.name,s.trackers);
  const bytes=sourceSize(s,!!pack,existing),badges=sourceBadges(s);
  const tags=qualityTags(`${s.name || ''} ${s.file || ''}`),match=matchContext?releaseMatch(s,matchContext):null;
  const add=async()=>{
    if(busy)return;
    if(inLib || added){navigate(existing?`/torbox?torrent=${encodeURIComponent(existing.id)}`:'/torbox');return;}
    if(!key)return toast.error('Add your TorBox API key first');
    setBusy(true);setFailure('');
    try {
      const {data}=await addTorrent({magnet,name:s.name});
      setAdded(true);toast.success(data.detail || 'Added to TorBox');buzz([12,40,12]);
      refreshLibrary().catch(()=>toast('Added to TorBox; library refresh failed. Refresh Library to check status.'));
    } catch(error){setFailure(errMsg(error));toast.error(errMsg(error));buzz([30,40,30]);}
    finally{setBusy(false);}
  };
  return <article data-testid={`stream-row-${index}`} className={`n-card source-release ${best && (!match || match.recommendable)?'source-release--suggested':''}`}>
    {best && (!match || match.recommendable) && <div className="source-suggestion"><Sparkles size={12}/> Suggested match</div>}
    <h3 className="source-release-name">{s.name}</h3>
    <div className="source-release-facts">
      {badges.map(tag=><span className="nothing-source-badge" key={tag} title="Inferred from release name">{tag}</span>)}
      <span className="source-size">{bytes?`${fmtBytes(bytes)}${pack?' · pack':''}`:pack?'Pack size unavailable':'Size unavailable'}</span>
      {pack && <span className="nothing-source-badge">S{String(pack.season).padStart(2,'0')} pack{pack.episodes?` · ${pack.episodes} ep`:''}</span>}

    </div>
    <div className="source-release-status">
      {cached && <span data-testid={`stream-cached-badge-${index}`} className="source-cache"><Zap size={12}/> Cached on TorBox</span>}
      <span>{s.seeders ?? 'Unknown'} seeds{s.source?` · ${s.source}`:''}</span>
      {(inLib || added) && <span className="source-cache"><Check size={12}/>{existing?.download_finished || existing?.download_present?'Ready on TorBox':'On TorBox'}</span>}
    </div>
    {match && match.status!=='likely' && <p className="source-match-note" data-match={match.status}>{match.label}{match.reasons.length?` · ${match.reasons.join(' · ')}`:''}</p>}
    {match?.warnings.length>0 && <p className="source-match-note">{match.warnings.join(' · ')}</p>}
    <div className="source-release-actions"><details className="source-technical"><summary>Release details</summary><div className="source-technical-body"><p className="source-original-name">{s.name}</p>{s.file && <p>{s.file}</p>}<p>{tags.join(' · ') || 'No technical tags detected'}</p><p>Tags and title matches are inferred from names. Cached availability does not verify content, language or playback compatibility.</p><button data-testid={`copy-magnet-button-${index}`} className="n-btn-ghost" onClick={()=>copyText(magnet).then(()=>toast('Magnet copied')).catch(error=>toast.error(errMsg(error)))}><Copy size={14}/> Copy magnet</button></div></details><button data-testid={`add-to-torbox-button-${index}`} onClick={add} disabled={busy} className="n-icon-btn source-add" title={inLib || added?'Open TorBox':failure?'Retry add':'Add to TorBox'} aria-label={inLib || added?'Open existing TorBox torrent':failure?'Retry add to TorBox':'Add to TorBox'}>{busy?<Loader2 size={15} className="animate-spin"/>:inLib || added?<Check size={15}/>:<Plus size={15}/>}<span className="sr-only">{busy?'Adding…':inLib || added?'Open TorBox':failure?'Retry add':'Add to TorBox'}</span></button></div>
    {failure && <p role="alert" className="source-add-error">{failure}</p>}
  </article>;
}
