import {useState} from 'react';
import {dateKey} from '../lib/calendarModel';
import {releaseDay,watchedDates} from '../lib/watchedDate';
import {Dialog,DialogContent,DialogTitle,DialogDescription} from './ui/dialog';

export default function WatchedDateDialog({open,onOpenChange,meta,episodes=[],onSave,busy=false}) {
  const [date,setDate]=useState(dateKey),[error,setError]=useState('');
  const releaseAvailable=episodes.length?episodes.every(pair=>releaseDay(meta,pair)):!!releaseDay(meta);
  const save=release=>{
    try{const payload=watchedDates(meta,episodes,date,release);setError('');onSave(payload);}
    catch(failure){setError(failure.message);}
  };
  return <Dialog open={open} onOpenChange={value=>{if(!busy){setError('');onOpenChange(value);}}}>
    <DialogContent className="watched-date-dialog">
      <DialogTitle>When did you watch it?</DialogTitle>
      <DialogDescription>{episodes.length>1?`${episodes.length} selected episodes · ${meta.name || 'Series'}`:meta.name || 'Choose the date for your history.'}</DialogDescription>
      <label className="watched-date-field">Watched on<input type="date" aria-label="Watched on" className="n-input" value={date} max={dateKey()} disabled={busy} onChange={e=>setDate(e.target.value)}/></label>
      {error && <p role="alert" className="source-add-error">{error}</p>}
      <button type="button" className="n-btn-primary" data-testid="save-watched-date" disabled={busy || !date} onClick={()=>save(false)}>{busy?'Saving…':'Save watched date'}</button>
      <button type="button" className="n-btn-ghost" data-testid="watched-on-release-day" disabled={busy || !releaseAvailable} onClick={()=>save(true)}>Watched on release day</button>
      {!releaseAvailable && <p className="watched-date-note">Release date unavailable. Choose a date above.</p>}
    </DialogContent>
  </Dialog>;
}
