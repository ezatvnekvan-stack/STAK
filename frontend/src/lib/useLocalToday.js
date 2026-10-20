import {useEffect,useState} from 'react';
import {dateKey} from './calendarModel';
export function useLocalToday() {
 const [today,setToday]=useState(()=>dateKey());
 useEffect(()=>{
  const now=new Date(),midnight=new Date(now.getFullYear(),now.getMonth(),now.getDate()+1);
  const update=()=>setToday(dateKey()),timer=setTimeout(update,Math.max(1000,midnight-now+100));
  const visible=()=>{if(document.visibilityState!=='hidden')update();};
  document.addEventListener('visibilitychange',visible);window.addEventListener('focus',update);
  return()=>{clearTimeout(timer);document.removeEventListener('visibilitychange',visible);window.removeEventListener('focus',update);};
 },[today]);
 return today;
}
