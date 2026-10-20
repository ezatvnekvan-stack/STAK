import {metadataContext} from './settings';
import {resolveIdentity} from './resolveIdentity';
import {filterImdb} from './filterRatings';
import {tmdbDetails,tmdbRequest,tmdbType} from '../providers/tmdb';
import {cinemetaDetails,cinemetaRequest} from '../providers/cinemeta';
import {normalizeTitleShape,dedupeTitles} from './normalize';
import {mapLimit} from './cache';
import {rankTitles,searchTerms} from '../searchRelevance';
export async function canonicalItems(items,context){return dedupeTitles(await mapLimit(items,async item=>{try{const identity=await resolveIdentity({id:item.imdb_id || item.id,tmdb_id:item.tmdb_id,type:item.type},context);return {...item,...identity,id:identity.imdb_id || item.id,stream_lookup_available:!!identity.imdb_id};}catch(error){if(error.code==='ERR_CANCELED')throw error;return {...item,stream_lookup_available:/^tt\d+$/.test(item.imdb_id || item.id || '')};}}));}
export async function titleDetails(input,context){
 context=context || await metadataContext();const notices=[];
 let identity;try{identity=await resolveIdentity(input,context);}catch(error){if(error.code==='ERR_CANCELED')throw error;const id=typeof input==='string'?input:input.id;identity={imdb_id:/^tt\d+$/.test(id)?id:null,type:input.type};notices.push('TMDb identity resolution failed. Some metadata may be unavailable.');}
 const type=identity.type || input.type || 'movie',selected=context.settings[type==='series'?'series':'movie'];
 if(selected==='cinemeta'){
  try{return {...await cinemetaDetails(identity,context),notices};}catch(error){if(error.code==='ERR_CANCELED')throw error;notices.push('Cinemeta metadata unavailable. Using TMDb.');}
 }
 if(!context.credentials.tmdb || !identity.tmdb_id){
  if(identity.imdb_id){const meta=await cinemetaDetails(identity,context);return {...meta,notices:[...notices,'TMDb key or matching identity missing. Showing Cinemeta metadata.']};}
  throw new Error('Configure TMDb to load metadata for this title.');
 }
 const base=await tmdbDetails({...identity,type},context);
 return {...base,notices:[...notices,...(base.notices || [])]};
}
export async function searchTitles(params,context){
 const query=String(params.q || params.query || '').trim().slice(0,160),page=Math.max(1,Number(params.page)||1),type=['movie','series'].includes(params.type)?params.type:null;
 const source=!context.credentials.tmdb?'cinemeta':'tmdb',notices=[];
 if(source==='cinemeta')notices.push('TMDb key missing. Search is using Cinemeta. Configure TMDb in Metadata sources.');
 if(!query)return {items:[],source,notices,next_cursor:null};
 let items=[],pages=page;
 if(source==='tmdb'){
  if(/^tt\d+$/.test(query)){const found=await tmdbRequest(`/find/${query}`,context,{params:{external_source:'imdb_id'}});items=[...(type==='series'?[]:(found.movie_results || []).map(m=>normalizeTitleShape(m,{type:'movie',imdb_id:query},'tmdb'))),...(type==='movie'?[]:(found.tv_results || []).map(m=>normalizeTitleShape(m,{type:'series',imdb_id:query},'tmdb')))];pages=1;}
  else{
   const route=type?`/search/${tmdbType(type)}`:'/search/multi',parts=searchTerms(query);let data=await tmdbRequest(route,context,{params:{query,include_adult:false,page}});
   if(parts.year && !(data.results || []).some(m=>(m.release_date || m.first_air_date || '').startsWith(parts.year)))data=await tmdbRequest(route,context,{params:{query:parts.title,include_adult:false,page}});
   items=(data.results || []).filter(m=>type || ['movie','tv'].includes(m.media_type)).filter(m=>!parts.year || (m.release_date || m.first_air_date || '').startsWith(parts.year)).map(m=>normalizeTitleShape(m,{type:type || (m.media_type==='tv'?'series':'movie')},'tmdb'));pages=Math.min(500,Number(data.total_pages)||page);
  }
 }else{
  const types=type?[type]:['movie','series'];const results=await Promise.allSettled(types.map(async kind=>{
   const data=await cinemetaRequest(/^tt\d+$/.test(query)?`/meta/${kind}/${query}.json`:`/catalog/${kind}/top/search=${encodeURIComponent(query)}.json`,context);
   return (data.meta?[data.meta]:data.metas || []).map(m=>normalizeTitleShape(m,{imdb_id:m.id,type:kind},'cinemeta'));
  }));if(results.every(r=>r.status==='rejected'))throw new Error('Cinemeta search unavailable.');items=results.flatMap(r=>r.status==='fulfilled'?r.value:[]);pages=1;
 }
 items=await canonicalItems(items,context);
 let ratingErrors=[];
 if(Number(params.imdb_rating)>0){const filtered=await filterImdb(items,Number(params.imdb_rating),context);items=filtered.items;ratingErrors=filtered.errors;}
 let genre=params.genre;
 if(source==='cinemeta' && /^\d+$/.test(String(genre)) && context.credentials.tmdb){const lists=await Promise.all(['movie','tv'].map(kind=>tmdbRequest(`/genre/${kind}/list`,context,{params:{language:'en-US'}})));genre=lists.flatMap(list=>list.genres || []).find(g=>String(g.id)===String(genre))?.name || genre;}
 const from=Number(params.year_from)||0,to=Number(params.year_to)||0;
 items=items.filter(m=>(!from || Number(m.year)>=from) && (!to || Number(m.year)<=to) && (!genre || (source==='tmdb'?((m.genre_ids || []).map(String).includes(String(genre)) || m.genres.includes(genre)):m.genres.includes(genre))));
 if(['primary_release_date.asc','oldest','primary_release_date.desc','newest'].includes(params.sort))items.sort((a,b)=>(Number(a.year)-Number(b.year))*(String(params.sort).endsWith('asc') || params.sort==='oldest'?1:-1));
 return {items:params.sort && ['primary_release_date.asc','oldest','primary_release_date.desc','newest'].includes(params.sort)?items:rankTitles(items,query),source,notices,errors:ratingErrors,page,total_pages:pages,next_cursor:page<pages?page+1:null};
}
