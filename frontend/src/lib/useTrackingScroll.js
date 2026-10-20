import {useCallback,useEffect} from 'react';
const positions=new Map();
// Per-view, per-login scroll memory. No account history/credentials enter storage.
export function useTrackingScroll(view,scope) {
 const remember=useCallback(()=>{positions.set(`${scope}:${view}`,window.scrollY);if(positions.size>40)positions.delete(positions.keys().next().value);},[view,scope]);
 useEffect(()=>{
  const key=`${scope}:${view}`,target=positions.get(key) || 0;
  let restoring=true,frame=0;
  const finish=()=>{restoring=false;observer.disconnect();clearTimeout(timer);};
  const restore=()=>{if(!restoring)return;window.scrollTo(0,target);if(Math.abs(window.scrollY-target)<3)finish();};
  const observer=typeof ResizeObserver==='function'?new ResizeObserver(()=>{cancelAnimationFrame(frame);frame=requestAnimationFrame(restore);}):{observe(){},disconnect(){}};
  const timer=setTimeout(finish,10000);observer.observe(document.documentElement);
  frame=requestAnimationFrame(restore);
  const scroll=()=>{if(!restoring)remember();};
  const interrupt=()=>finish();
  window.addEventListener('scroll',scroll,{passive:true});window.addEventListener('touchstart',interrupt,{passive:true});window.addEventListener('wheel',interrupt,{passive:true});
  return()=>{finish();cancelAnimationFrame(frame);window.removeEventListener('scroll',scroll);window.removeEventListener('touchstart',interrupt);window.removeEventListener('wheel',interrupt);};
 },[view,scope,remember]);
 return remember;
}
