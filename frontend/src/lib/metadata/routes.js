import {filterImdb} from './filterRatings';
import {filteredSearch} from './filteredSearch';
import {metadataContext,saveMetadataSettings,setMetadataCredential} from './settings';
import {LocalPreferences} from '../secureStorage';
import {tmdbRequest} from '../providers/tmdb';
import {omdbRequest,omdbRatings} from '../providers/omdb';
import {cinemetaRequest} from '../providers/cinemeta';
import {resolveIdentity} from './resolveIdentity';
import {titleDetails,searchTitles,canonicalItems} from './service';
import {catalogRoute} from './catalogRoutes';
import {image,normalizeTitleShape} from './normalize';
const result=(config,data)=>({data,status:200,statusText:'OK',headers:{},config});
const keyStatus=async context=>{let tested={};try{tested=JSON.parse((await LocalPreferences.get({key:`dmm.local.v1.${context.scope}.metadata-status`})).value || '{}');}catch{/* corrupt nonsecret status */}return Object.fromEntries(['tmdb','omdb'].map(p=>[p,!context.credentials[p]?'missing':tested[p]==='invalid'?'invalid':'set']));};
export async function metadataRoutes(config){
 const context=await metadataContext(config),method=(config.method || 'get').toLowerCase(),path=String(config.url || '').split('?')[0],params={...Object.fromEntries(new URL(config.url,'https://local.invalid').searchParams),...config.params};let body=config.data;if(typeof body==='string')try{body=JSON.parse(body);}catch{body={};}
 if(path==='/metadata/settings'){
  if(method==='put')return result(config,{settings:await saveMetadataSettings(context.scope,body),status:await keyStatus(context)});
  return result(config,{settings:context.settings,status:await keyStatus(context)});
 }
 if(path==='/metadata/credentials' && method==='put'){
  await setMetadataCredential(context.scope,body?.provider,body?.value);const statuses=await keyStatus(context);delete statuses[body.provider];await LocalPreferences.set({key:`dmm.local.v1.${context.scope}.metadata-status`,value:JSON.stringify(statuses)});return result(config,{saved:true});
 }
 if((path==='/metadata/test' && method==='post') || path==='/tmdb/verify'){
  const provider=path==='/tmdb/verify'?'tmdb':body?.provider;
  if(body?.value && ['tmdb','omdb'].includes(provider)){if(typeof body.value!=='string' || body.value.length>4096 || /[\r\n]/.test(body.value))throw new Error('Invalid provider credential.');context.credentials[provider]=body.value.trim();}
  try{
   if(provider==='tmdb'){const data=await tmdbRequest('/configuration',context);if(!data?.images?.secure_base_url)throw new Error('TMDb returned invalid configuration.');}
   else if(provider==='omdb')await omdbRequest('tt0111161',context,{force:true});else throw new Error('Unknown metadata provider.');
   const statuses=await keyStatus(context);delete statuses[provider];await LocalPreferences.set({key:`dmm.local.v1.${context.scope}.metadata-status`,value:JSON.stringify(statuses)});
   return result(config,{valid:true,status:'set'});
  }catch(error){if(!body?.value && (error.response?.status===401 || error.response?.status===403 || /rejected the API key/i.test(error.message || ''))){let statuses=await keyStatus(context);statuses={...statuses,[provider]:'invalid'};await LocalPreferences.set({key:`dmm.local.v1.${context.scope}.metadata-status`,value:JSON.stringify(statuses)});}throw error;}
 }
 if(path==='/metadata/identity' || path==='/meta/resolve')return result(config,await resolveIdentity({id:params.id || params.imdb_id || `tmdb:${String(params.tmdb_id || "").replace(/^tmdb:/,"")}`,type:params.type},context));
 if(path==='/metadata/search' || (path==='/discover' && (params.q || params.query))){
  if(!String(params.q || params.query || '').trim()){
   const response=await metadataRoutes({...config,url:'/discover',params:{...params,skip:((Number(params.page)||1)-1)*20}});
   return result(config,{...response.data,source:context.credentials.tmdb?'tmdb':'cinemeta',next_cursor:response.data.next_skip!=null?(Number(params.page)||1)+1:null});
  }
  const filters=['provider','certification','language','imdb_rating','sort','genre'].some(name=>params[name]);
  if(filters && context.credentials.tmdb)return result(config,await filteredSearch(config,context,params,metadataRoutes));
  if((params.provider || params.certification || params.language) && !context.credentials.tmdb)throw new Error('Provider, original-language and age-rating filters require TMDb search. Configure TMDb or clear these filters.');
  return result(config,await searchTitles(params,context));
 }
 const title=path.match(/^\/meta\/(movie|series)\/(tt\d+|tmdb:\d+|tvdb:\d+)$/);
 if(title && method==='get')return result(config,await titleDetails({id:title[2],type:title[1]},context));
 const rating=path.match(/^\/ratings\/(tt\d+)$/);if(rating && method==='get')return result(config,omdbRatings(await omdbRequest(rating[1],context)));
 if(path==='/meta/episode'){
  const season=Number(params.season),episode=Number(params.episode);if(!Number.isInteger(season) || season<1 || !Number.isInteger(episode) || episode<1)throw new Error('Choose a valid season and episode.');
  const identity=await resolveIdentity({id:params.imdb_id,type:'series'},context),tasks=[];
  if(identity.tmdb_id && context.credentials.tmdb)tasks.push((async()=>{const data=await tmdbRequest(`/tv/${identity.tmdb_id}/season/${season}/episode/${episode}`,context);return {name:data.name,overview:data.overview,released:data.air_date,thumbnail:image(data.still_path)};})());
  if(context.settings.series==='cinemeta' && identity.imdb_id)tasks.push((async()=>{const data=await cinemetaRequest(`/meta/series/${identity.imdb_id}.json`,context),video=data.meta?.videos?.find(e=>Number(e.season)===season && Number(e.episode)===episode);return video?{name:video.name || video.title,overview:video.overview || video.description}:{};})());
  if(identity.imdb_id && context.settings.ratings && context.credentials.omdb)tasks.push((async()=>{const data=await omdbRequest(identity.imdb_id,context,{season,episode});return {imdbRating:omdbRatings(data).imdb};})());
  const settled=await Promise.allSettled(tasks);if(!settled.length || settled.every(r=>r.status==='rejected'))throw new Error('Episode metadata unavailable. Check Metadata sources.');
  return result(config,{...Object.assign({},...settled.filter(r=>r.status==='fulfilled').map(r=>r.value)),partial:settled.some(r=>r.status==='rejected')});
 }
 if(path==='/discover' || path==='/meta/top'){
  const type=params.type==='series'?'series':'movie';
  if(context.credentials.tmdb && (path!=='/discover' || context.settings.search!=='cinemeta')){const response=await metadataRoutes({...config,url:'/discover/tmdb',params:{...params,type,rating:params.min_rating || params.rating,sort:({rating:'vote_average.desc',newest:'primary_release_date.desc',oldest:'primary_release_date.asc',name:'original_title.asc',popular:''})[params.sort] ?? params.sort,category:params.sort==='rating'?'top':!params.sort && !params.genre && !params.year_from?'trending':'popular',page:Math.floor((Number(params.skip)||0)/20)+1}});return path==='/meta/top'?result(config,response.data.items):result(config,{...response.data,next_skip:response.data.page<response.data.total_pages?(Number(params.skip)||0)+20:null});}
  const category=params.sort==='rating'?'imdbRating':'top',extras=[params.genre?`genre=${encodeURIComponent(params.genre)}`:'',params.skip?`skip=${Number(params.skip)||0}`:''].filter(Boolean).join('&'),data=await cinemetaRequest(`/catalog/${type}/${category}${extras?'/'+extras:''}.json`,context);
  let items=(data.metas || []).map(m=>normalizeTitleShape(m,{type,imdb_id:m.id},'cinemeta')).filter(m=>(!params.year_from || Number(m.year)>=Number(params.year_from)) && (!params.year_to || Number(m.year)<=Number(params.year_to)));
  let ratingErrors=[];if(Number(params.imdb_rating || params.min_rating)>0){const filtered=await filterImdb(items,Number(params.imdb_rating || params.min_rating),context);items=filtered.items;ratingErrors=filtered.errors;}
  return result(config,path==='/meta/top'?items:{items,errors:ratingErrors,next_skip:(data.metas || []).length>=20?(Number(params.skip)||0)+items.length:null,notices:context.credentials.tmdb?[]:['TMDb key missing. Browsing uses Cinemeta.']});
 }
 const response=await catalogRoute(config,context,metadataRoutes);
 if(response?.data?.items)response.data.items=await canonicalItems(response.data.items,context);
 return response;
}
