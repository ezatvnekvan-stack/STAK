// Sum accessible movie/episode media files, including alternate versions, once per path.
// No raw server paths are returned to the UI or written to logs/storage.
export function addMediaSizes(state, items) {
  for (const item of items) {
    const sources=Array.isArray(item.MediaSources)?item.MediaSources:[];
    if (!sources.length) { state.missing++; continue; }
    for (const [index,source] of sources.entries()) {
      if (!source || typeof source!=='object') { state.missing++; continue; }
      const identity=source.Path || `${item.Id}:${source.Id || index}`;
      if (state.seen.has(identity)) continue;
      state.seen.add(identity);
      const size=Number(source.Size);
      if (source.Size==null || !Number.isFinite(size) || size<0) state.missing++;
      else state.bytes+=size;
    }
  }
  return state;
}
export function scanTask(tasks) {
  const task=tasks.find(t=>t?.Key==='RefreshLibrary');
  if(!task)return {supported:false};
  const raw=task.CurrentProgressPercentage, progress=raw==null?null:Number(raw);
  return {supported:true,state:task.State,progress:Number.isFinite(progress)?Math.max(0,Math.min(100,progress)):null,last_result:task.LastExecutionResult?{status:task.LastExecutionResult.Status,end_time:task.LastExecutionResult.EndTime}:null};
}
export function scanFinished(current, baseline) {
  if (!current?.supported || current.state!=='Idle') return false;
  return Boolean(current.last_result?.end_time && current.last_result.end_time!==baseline?.last_result?.end_time);
}
