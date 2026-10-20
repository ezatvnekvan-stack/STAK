import {catalogRoute} from './catalogRoutes';
import {canonicalItems} from './service';
export async function filteredSearch(config,context,params,dispatch){
 const types=['movie','series'];let cursor=null;try{cursor=JSON.parse(params.pages || 'null');}catch{/* first page */}
 const requests=types.filter(kind=>!cursor || cursor[kind]!=null),results=await Promise.allSettled(requests.map(async type=>{
  const response=await catalogRoute({...config,url:'/discover/tmdb',params:{...params,type,category:'search',query:params.q,page:cursor?Number(cursor[type]):Number(params.page)||1}},context,dispatch);
  return {...response.data,items:await canonicalItems(response.data.items || [],context)};
 }));if(results.every(r=>r.status==='rejected'))throw results[0].reason;
 const next=Object.fromEntries(results.map((r,i)=>[requests[i],r.status==='fulfilled' && Number(r.value.page)<Math.min(500,Number(r.value.total_pages))?Number(r.value.page)+1:null]));
 return {source:'tmdb',items:results.flatMap(r=>r.status==='fulfilled'?r.value.items:[]),next_cursor:Object.values(next).some(v=>v!==null)?next:null,notices:[],errors:results.flatMap(r=>r.status==='rejected'?['Some TMDb search results could not load.']:r.value.rating_errors?['Some IMDb ratings could not be verified. OMDb daily quota applies.']:[])};
}
