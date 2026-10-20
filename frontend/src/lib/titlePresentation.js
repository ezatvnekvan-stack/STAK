export function compactCrew(crew=[],limit=4) {
  const people=new Map();
  for(const person of Array.isArray(crew)?crew:[]) {
    if(!person?.id || !person.name)continue;
    if(!people.has(person.id))people.set(person.id,{...person,jobs:new Set()});
    if(person.job)people.get(person.id).jobs.add(person.job);
  }
  return [...people.values()].sort((a,b)=>Number(![...a.jobs].some(job=>/^(Director|Creator|Showrunner)$/.test(job)))-Number(![...b.jobs].some(job=>/^(Director|Creator|Showrunner)$/.test(job)))).slice(0,limit).map(person=>({...person,job:[...person.jobs].join(' · ')}));
}
