import { cloneElement, useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Bookmark, Plus, X } from 'lucide-react';
import { toast } from 'sonner';
import { api, errMsg } from '../lib/api';
import { useApp } from '../context/AppContext';
import { jellyfinIdentity } from '../lib/titleIdentity';
import { buzz } from '../lib/haptics';
import { Popover, PopoverAnchor, PopoverContent } from './ui/popover';
let activePress=null;
window.addEventListener('scroll',()=>activePress?.cancel(),{capture:true,passive:true});

export default function MediaQuickActions({item,children}) {
  const {watchIds,toggleWatch,watchUndo,session}=useApp(),navigate=useNavigate();
  const [open,setOpen]=useState(false),[busy,setBusy]=useState(false);
  const focusFrame=useRef(0),anchor=useRef(null),timer=useRef(null),point=useRef(null),blocked=useRef(0),alive=useRef(true);
  const cancel=useCallback(()=>{clearTimeout(timer.current);point.current=null;if(activePress?.owner===timer)activePress=null;},[]);
  useEffect(()=>{alive.current=true;return()=>{alive.current=false;cancel();cancelAnimationFrame(focusFrame.current);};},[cancel]);
  const reveal=()=>{cancel();blocked.current=Date.now()+1000;setOpen(true);buzz(8);};
  const normalize=async()=>{
    let identity=item.Type?jellyfinIdentity(item):null;
    if(item.Type && !identity){const {data}=await api.get('/jellyfin/resolve-title',{params:{item_id:item.Id}});identity=data.identity;if(!identity)throw new Error('This title has no reliable metadata match. Open Details to search for it.');}
    const raw=String(item.imdb_id || item.id || '');
    const id=identity?.id || (/^\d+$/.test(raw)?`tmdb:${raw}`:raw);
    const type=identity?.type || (item.type==='tv'?'series':item.type || 'movie');
    if(!/^(tt\d+|tmdb:\d+)$/.test(id) || !['movie','series'].includes(type))throw new Error('This title has no reliable metadata identifier.');
    return {...item,id,imdb_id:id,type,imdbRating:item.imdbRating ?? item.rating,name:item.SeriesName || item.Name || item.name || item.title,poster:item.poster || '',...(identity?.episode?{season:identity.season,episode:identity.episode}:{})};
  };
  const action=async library=>{
    setBusy(true);
    try{const title=await normalize();if(!alive.current)return;
      if(library){const params=new URLSearchParams({add:'1'});if(title.episode){params.set('season',title.season);params.set('episode',title.episode);}setOpen(false);navigate(`/title/${title.type}/${title.id}?${params}`);}
      else{if(!saved && watchIds.has(title.id)){toast.success('Already in watchlist');setOpen(false);return;}const added=await toggleWatch(title);if(!session?.controller?.signal.aborted){const undo=watchUndo?.(title,added);toast.success(added?'Added to watchlist':'Removed from watchlist',{action:{label:'Undo',onClick:()=>(undo?undo():toggleWatch(title,{desired:!added,expectedCurrent:added})).catch(error=>toast.error(errMsg(error)))}});if(alive.current)setOpen(false);}}
    }catch(error){if(alive.current)toast.error(errMsg(error));}finally{if(alive.current)setBusy(false);}
  };
  const child=cloneElement(children,{
    ref:anchor,
    'data-quick-actions':'true',
    'aria-haspopup':'dialog',
    'aria-expanded':open,
    'aria-keyshortcuts':'Shift+F10',
    'aria-description':'Hold for title actions, or press Shift+F10.',
    onPointerDown:e=>{children.props.onPointerDown?.(e);if(e.button!==0)return;activePress?.cancel();activePress={cancel,owner:timer};point.current={x:e.clientX,y:e.clientY};timer.current=setTimeout(reveal,480);},
    onPointerMove:e=>{children.props.onPointerMove?.(e);if(point.current && Math.hypot(e.clientX-point.current.x,e.clientY-point.current.y)>10)cancel();},
    onPointerUp:e=>{cancel();children.props.onPointerUp?.(e);},onPointerCancel:e=>{cancel();children.props.onPointerCancel?.(e);},
    onContextMenu:e=>{e.preventDefault();e.stopPropagation();reveal();},
    onKeyDown:e=>{if(e.key==='ContextMenu' || (e.shiftKey && e.key==='F10')){e.preventDefault();reveal();}else children.props.onKeyDown?.(e);},
    onClick:e=>{if(Date.now()<blocked.current || open){e.preventDefault();e.stopPropagation();return;}children.props.onClick?.(e);},
    onDragStart:e=>{cancel();e.preventDefault();},
  });
  const saved=watchIds.has(jellyfinIdentity(item)?.id || item.imdb_id || item.id);
  return <Popover open={open} onOpenChange={setOpen}><PopoverAnchor asChild>{child}</PopoverAnchor><PopoverContent onKeyDownCapture={event=>{if(event.key==='Escape'){event.preventDefault();event.stopPropagation();setOpen(false);}}} onCloseAutoFocus={event=>{event.preventDefault();cancelAnimationFrame(focusFrame.current);focusFrame.current=requestAnimationFrame(()=>anchor.current?.focus({preventScroll:true}));}} side="top" align="center" sideOffset={8} collisionPadding={12} className="poster-action-menu" aria-label={`Actions for ${item.name || item.Name || 'title'}`}>
    <div className="mb-2 flex items-center gap-2"><span className="min-w-0 flex-1 truncate text-xs text-neutral-400">{item.name || item.SeriesName || item.Name}</span><button className="n-icon-btn" aria-label="Close title actions" onClick={()=>setOpen(false)}><X size={14}/></button></div>
    <button type="button" disabled={busy} className="stak-button stak-button--subtle poster-action-item" onClick={()=>action(false)}><Bookmark size={15}/>{saved?'Remove from watchlist':'Add to watchlist'}</button>
    <button type="button" disabled={busy} className="stak-button stak-button--subtle poster-action-item" onClick={()=>action(true)}><Plus size={15}/>Add to library</button>
  </PopoverContent></Popover>;
}
