import {useEffect,useRef,useState} from 'react';

// Mount each enabled shelf once it approaches the viewport. Query caches retain
// data after a search hides Home; hidden shelves do not run provider effects.
export default function DeferredSection({children,id}) {
 const ref=useRef(null),[ready,setReady]=useState(()=>typeof IntersectionObserver==='undefined');
 useEffect(()=>{
  if(ready || !ref.current)return undefined;
  const observer=new IntersectionObserver(entries=>{if(entries.some(entry=>entry.isIntersecting)){setReady(true);observer.disconnect();}},{rootMargin:'240px'});
  observer.observe(ref.current);return()=>observer.disconnect();
 },[ready]);
 return <div ref={ref} className="home-deferred-section" data-home-section={id} data-ready={ready}>{ready?children:<div className="home-shelf-placeholder" aria-hidden="true"/>}</div>;
}
