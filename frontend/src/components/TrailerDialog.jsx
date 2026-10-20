import { useEffect, useRef, useState } from 'react';
import { ExternalLink, Maximize, RefreshCw } from 'lucide-react';
import { Dialog, DialogContent, DialogDescription, DialogTitle } from './ui/dialog';
import NothingSelect from './NothingSelect';
import { errMsg, openExternal } from '../lib/api';
const validKey = key => /^[A-Za-z0-9_-]{11}$/.test(key || '');
const origins = new Set(['https://www.youtube-nocookie.com','https://www.youtube.com']);
const errors = {2:'This trailer address is invalid.',5:'This device cannot play the trailer in the embedded player.',100:'This trailer is unavailable or private.',101:'The uploader does not allow this trailer to play inside apps.',150:'The uploader does not allow this trailer to play inside apps.',153:'YouTube could not verify this WebView. Open the trailer on YouTube instead.'};

export default function TrailerDialog({ videos = [], initialKey, onClose }) {
  const choices = [...new Map((Array.isArray(videos) ? videos : []).filter(v=>validKey(v?.key)).map(v=>[v.key,v])).values()];
  const [key,setKey]=useState(validKey(initialKey)?initialKey:choices[0]?.key || '');
  const [attempt,setAttempt]=useState(0),[loaded,setLoaded]=useState(false),[error,setError]=useState(''),[busy,setBusy]=useState(false);
  const [player,setPlayer]=useState(null);
  const stage=useRef(null);
  const origin=typeof window!=='undefined' && /^https?:$/.test(window.location.protocol)?window.location.origin:null;
  const query=new URLSearchParams({autoplay:'1',playsinline:'1',rel:'0',enablejsapi:'1',...(origin?{origin,widget_referrer:origin}:{})});
  const src=validKey(key)?`https://www.youtube-nocookie.com/embed/${key}?${query}`:null;
  useEffect(()=>{
    setLoaded(false);setError('');
    if(!validKey(key)||!player)return undefined;
    let ready=false;
    const post=message=>{if(!player.isConnected)return;try{player.contentWindow?.postMessage(JSON.stringify(message),'https://www.youtube-nocookie.com');}catch{ /* A closing/replaced frame may no longer accept commands. */ }};
    const send=(func,args=[])=>post({event:'command',func,args});
    const receive=event=>{
      if(!origins.has(event.origin)||event.source!==player?.contentWindow)return;
      let data=event.data;try{if(typeof data==='string')data=JSON.parse(data);}catch{return;}
      if(data?.event==='onReady'||data?.event==='initialDelivery'||data?.event==='onStateChange'){ready=true;setLoaded(true);}
      if(data?.event==='onError'){ready=true;setLoaded(true);setError(errors[Number(data.info)] || 'The provider could not play this trailer. Try YouTube externally.');}
    };
    const listen=()=>{post({event:'listening',id:'dmm-trailer'});send('addEventListener',['onError']);send('addEventListener',['onReady']);};
    const interval=setInterval(listen,800);
    const timeout=setTimeout(()=>{if(!ready){setLoaded(true);setError('Playback could not be confirmed. If the video is blocked or stays blank, open it on YouTube.');}clearInterval(interval);},12000);
    const visibility=()=>{if(document.visibilityState==='hidden')send('pauseVideo');};
    window.addEventListener('message',receive);document.addEventListener('visibilitychange',visibility);
    return()=>{clearInterval(interval);clearTimeout(timeout);window.removeEventListener('message',receive);document.removeEventListener('visibilitychange',visibility);send('stopVideo');};
  },[key,attempt,player]);
  const external=async()=>{if(!validKey(key)||busy)return;setBusy(true);try{await openExternal(`https://www.youtube.com/watch?v=${key}`);}catch(failure){setError(errMsg(failure));}finally{setBusy(false);}};
  const fullscreen=async()=>{try{if(!stage.current?.requestFullscreen)throw new Error('Use the video fullscreen control if supported on your device.');await stage.current.requestFullscreen();}catch(failure){setError(errMsg(failure));}};
  return <Dialog open onOpenChange={value=>{if(!value)onClose();}}><DialogContent className="trailer-dialog">
    <DialogTitle className="font-dot pr-8 text-xl">Watch trailer</DialogTitle><DialogDescription className="text-xs">YouTube · Tap Play if playback does not start. Availability depends on the uploader.</DialogDescription>
    {choices.length>1 && <NothingSelect className="n-input" aria-label="Choose trailer" value={key} onChange={event=>setKey(event.target.value)}>{choices.map(video=><option key={video.key} value={video.key}>{video.name || 'Trailer'}</option>)}</NothingSelect>}
    {src ? <div ref={stage} className="trailer-stage"><iframe key={`${key}:${attempt}`} ref={setPlayer} title="YouTube trailer" src={src} allow="autoplay; encrypted-media; fullscreen; picture-in-picture" allowFullScreen referrerPolicy="strict-origin-when-cross-origin" onLoad={()=>setLoaded(true)} onError={()=>{setLoaded(true);setError('The trailer could not load. Retry or open YouTube.');}}/>{!loaded && <div className="trailer-loading" role="status"><span className="dot-skel h-2 w-10 rounded-full"/>Loading trailer…</div>}</div> : <p className="text-sm text-neutral-400">No trailer is available.</p>}
    {error && <p role="alert" className="text-xs text-neutral-400">{error}</p>}
    <div className="flex flex-wrap gap-2"><button className="n-chip" disabled={!src} onClick={fullscreen}><Maximize size={13}/> Fullscreen</button><button className="n-chip" disabled={!src} onClick={()=>setAttempt(v=>v+1)}><RefreshCw size={13}/> Retry</button><button className="n-chip" disabled={!src||busy} onClick={external}><ExternalLink size={13}/>{busy?'Opening…':'Open YouTube'}</button></div>
  </DialogContent></Dialog>;
}
