import {dateKey} from './calendarModel';
export function activityPeriodRows(items=[],period='week',now=new Date()) {
  if(period==='all')return items;
  const start=new Date(now);start.setHours(0,0,0,0);start.setDate(start.getDate()-(period==='month'?29:6));
  return items.filter(item=>{const date=new Date(item.last_played);return item.last_played && !Number.isNaN(date.getTime()) && date>=start && date<=now;});
}
export function activitySummary(items=[]) {
  const known=items.filter(item=>typeof item.duration_hours==='number' && Number.isFinite(item.duration_hours) && item.duration_hours>0);
  return {movies:items.filter(item=>item.type==='movie').length,episodes:items.filter(item=>item.type==='episode').length,hours:known.reduce((sum,item)=>sum+item.duration_hours,0),knownRuntime:known.length,total:items.length};
}
export function normalizeTracking(value={}) {
 const today=dateKey();
 const date=typeof value.date==='string' && /^\d{4}-\d{2}-\d{2}$/.test(value.date) && dateKey(value.date)===value.date?value.date:today;
 return {
  view:value.view==='calendar'?'calendar':'activity',calendarView:'month',scope:'following',date,
  calendarArtwork:value.calendarArtwork===true,
  calendarType:['movie','series'].includes(value.calendarType)?value.calendarType:'all',
  days:[7,30,90].includes(value.days)?value.days:30,
  weekStart:[0,1].includes(value.weekStart)?value.weekStart:'locale',
  historySchema:2,period:value.historySchema===2 && ['week','month'].includes(value.period)?value.period:'all',
  filter:value.filter==='episode'?'series':['all','movie','series'].includes(value.filter)?value.filter:'all',
  day:typeof value.day==='string' && /^\d{4}-\d{2}-\d{2}$/.test(value.day)?value.day:'',
 };
}
