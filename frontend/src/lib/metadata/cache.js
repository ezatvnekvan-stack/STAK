// Only public metadata and non-secret cooldown markers belong in this database.
// Credentials, authorization headers and signed URLs are never cache keys/values.
const memory=new Map();let connection;
export const DAY=86400000;
function database(){
  if(!globalThis.indexedDB)return Promise.resolve(null);
  if(!connection)connection=new Promise(resolve=>{const r=indexedDB.open('stak-metadata-v1',1);r.onupgradeneeded=()=>r.result.createObjectStore('entries',{keyPath:'key'});r.onsuccess=()=>resolve(r.result);r.onerror=()=>resolve(null);r.onblocked=()=>resolve(null);});
  return connection;
}
async function operation(mode,run){const db=await database();if(!db)return null;return new Promise(resolve=>{try{const tx=db.transaction('entries',mode),r=run(tx.objectStore('entries'));let value; r.onsuccess=()=>{value=r.result;};tx.oncomplete=()=>resolve(value);tx.onerror=()=>resolve(null);tx.onabort=()=>resolve(null);}catch{resolve(null);}});}
export async function cacheGet(key){const record=memory.get(key) || await operation('readonly',s=>s.get(key));if(!record || record.expires<=Date.now()){memory.delete(key);return null;}return record.value;}
let writes=0;
export async function cacheSet(key,value,ttl=DAY){
  const entry={key,value,created:Date.now(),expires:Date.now()+ttl};memory.set(key,entry);if(memory.size>100)memory.delete(memory.keys().next().value);
  await operation('readwrite',s=>s.put(entry));
  if(++writes%32===0){const entries=await operation('readonly',s=>s.getAll());if(entries?.length){let bytes=0,index=0;for(const row of entries.sort((a,b)=>b.created-a.created)){bytes+=JSON.stringify(row).length;if(row.expires<Date.now() || bytes>32*1024*1024 || index++>=600)await operation('readwrite',s=>s.delete(row.key));}}}
  return value;
}
export async function cached(key,ttl,work){const hit=await cacheGet(key);return hit!==null?hit:cacheSet(key,await work(),ttl);}
export async function mapLimit(items,fn,limit=4){const values=new Array(items.length);let cursor=0;await Promise.all(Array.from({length:Math.min(limit,items.length)},async()=>{while(cursor<items.length){const index=cursor++;values[index]=await fn(items[index],index);}}));return values;}
