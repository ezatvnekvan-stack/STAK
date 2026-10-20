import {useEffect,useState} from 'react';
const searches=new Map();
// Search text stays in memory for this login, never in an account backup.
export function useCollectionSearch(name,scope) {
  const id=`${scope}:${name}`;
  const [value,setValue]=useState(()=>searches.get(id)||'');
  useEffect(()=>{searches.set(id,value);if(searches.size>30)searches.delete(searches.keys().next().value);},[id,value]);
  return [value,setValue];
}
