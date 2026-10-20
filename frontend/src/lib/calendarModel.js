// Calendar dates are civil dates, not UTC timestamps. Noon avoids DST boundaries.
export function dateKey(value=new Date()) {
  const date=value instanceof Date?value:new Date(`${value}T12:00:00`);
  if(Number.isNaN(date.getTime()))return '';
  return `${date.getFullYear()}-${String(date.getMonth()+1).padStart(2,'0')}-${String(date.getDate()).padStart(2,'0')}`;
}
export function validDate(value) {
  return typeof value==='string' && /^\d{4}-\d{2}-\d{2}$/.test(value) && dateKey(value)===value;
}
export const addDays=(day,n)=>{const date=new Date(`${day}T12:00:00`);date.setDate(date.getDate()+n);return dateKey(date);};
export const shiftMonth=(month,delta)=>{const date=new Date(`${month}-01T12:00:00`);date.setMonth(date.getMonth()+delta);return dateKey(date).slice(0,7);};
export function monthCells(month,weekStart=1) {
  const first=`${month}-01`,weekday=new Date(`${first}T12:00:00`).getDay();
  const start=addDays(first,-((weekday-weekStart+7)%7));
  return Array.from({length:42},(_,i)=>({date:addDays(start,i),inMonth:addDays(start,i).startsWith(month)}));
}
export function weekCells(selected,weekStart=1) {
  const weekday=new Date(`${selected}T12:00:00`).getDay();
  const start=addDays(selected,-((weekday-weekStart+7)%7));
  return Array.from({length:7},(_,index)=>({date:addDays(start,index),inMonth:addDays(start,index).slice(0,7)===selected.slice(0,7)}));
}
export function localeWeekStart(locale=globalThis.navigator?.language || 'en-US') {
  try { const value=new Intl.Locale(locale),info=value.getWeekInfo?.() || value.weekInfo;return info?.firstDay===7?0:info?.firstDay===1?1:1; } catch { return 1; }
}
const formatters=new Map();
export function dateLabel(day,options={weekday:'long',month:'long',day:'numeric',year:'numeric'}) {
  const date=new Date(`${day}T12:00:00`);
  if(Number.isNaN(date.getTime()))return String(day || '');
  const locale=globalThis.navigator?.language || 'en-US',key=`${locale}:${JSON.stringify(options)}`;
  let formatter=formatters.get(key);
  if(!formatter){
    try{formatter=new Intl.DateTimeFormat(locale,options);}
    catch{try{formatter=new Intl.DateTimeFormat('en-US',options);}catch{return String(day);}}
    if(formatters.size>=16)formatters.delete(formatters.keys().next().value);
    formatters.set(key,formatter);
  }
  try{return formatter.format(date);}catch{return String(day);}
}
export function calendarRange(params={},today=dateKey()) {
  if(params.start!==undefined || params.end!==undefined) {
    if(!validDate(params.start) || !validDate(params.end) || params.start>params.end || (new Date(`${params.end}T12:00:00Z`)-new Date(`${params.start}T12:00:00Z`))/86400000>41)throw new Error('Calendar range must contain 1–42 valid consecutive dates.');
    return {today,start:params.start,end:params.end};
  }
  const days=Math.max(1,Math.min(90,Number(params.days)||30));
  return {today,start:today,end:addDays(today,days)};
}
export function orderedCalendarEvents(events=[]) {
  const seen=new Set();
  return events.filter(event=>{if(!validDate(event.date))return false;const id=event.imdb_id || event.id || event.name;const key=`${event.type}:${id}:${event.season ?? ''}:${event.episode ?? ''}:${event.date}`;if(seen.has(key))return false;seen.add(key);return true;}).sort((a,b)=>a.date.localeCompare(b.date) || String(a.name).localeCompare(String(b.name)) || Number(a.season || 0)-Number(b.season || 0) || Number(a.episode || 0)-Number(b.episode || 0));
}
export function calendarTitleGroups(events=[]) {
  const groups=new Map();
  for(const event of orderedCalendarEvents(events)){const key=`${event.type}:${event.imdb_id || event.id || event.name}`;if(!groups.has(key))groups.set(key,{key,event,episodes:[]});groups.get(key).episodes.push(event);}
  return [...groups.values()];
}
