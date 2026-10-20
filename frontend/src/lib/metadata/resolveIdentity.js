import {tmdbRequest,tmdbType} from '../providers/tmdb';
import {cached,DAY} from './cache';
import {metadataContext} from './settings';
// The second argument carries captured account/provider state; no credentials are persisted.
export async function resolveIdentity(input,context){
 context=context || await metadataContext();const value=typeof input==='object'?input:{id:input},raw=String(value.imdb_id || value.id || (value.tmdb_id?`tmdb:${value.tmdb_id}`:value.tvdb_id?`tvdb:${value.tvdb_id}`:''));
 if(!/^(tt\d+|tmdb:\d+|tvdb:\d+)$/.test(raw))throw new Error('A valid IMDb, TMDb or TVDB title identifier is required.');
 const type=value.type==='tv'?'series':value.type;
 if(!context.credentials.tmdb)return {imdb_id:raw.startsWith('tt')?raw:null,tmdb_id:value.tmdb_id || (raw.startsWith('tmdb:')?Number(raw.slice(5)):null),tvdb_id:value.tvdb_id || (raw.startsWith('tvdb:')?Number(raw.slice(5)):null),type:type || (raw.startsWith('tvdb:')?'series':null)};
 return cached(`identity:${type || 'auto'}:${raw}`,30*DAY,async()=>{
  let tmdb_id=raw.startsWith('tmdb:')?Number(raw.slice(5)):value.tmdb_id,resolvedType=type,imdb_id=raw.startsWith('tt')?raw:null,tvdb_id=raw.startsWith('tvdb:')?Number(raw.slice(5)):value.tvdb_id || null;
  if(!tmdb_id){const found=await tmdbRequest(`/find/${raw.startsWith('tvdb:')?raw.slice(5):raw}`,context,{params:{external_source:raw.startsWith('tvdb:')?'tvdb_id':'imdb_id'}}),movie=found.movie_results?.[0],tv=found.tv_results?.[0],match=resolvedType==='movie'?movie:resolvedType==='series'?tv:movie || tv;
   if(!match)return {imdb_id,tmdb_id:null,tvdb_id,type:resolvedType || null};tmdb_id=match.id;resolvedType=resolvedType || (match===tv?'series':'movie');}
  if(!resolvedType){const candidates=await Promise.allSettled(['movie','series'].map(async kind=>({kind,external:await tmdbRequest(`/${tmdbType(kind)}/${tmdb_id}/external_ids`,context,{params:{language:'en-US'}})})));const valid=candidates.filter(r=>r.status==='fulfilled' && r.value.external && (r.value.external.id || r.value.external.imdb_id));if(valid.length!==1)throw new Error('This TMDb identifier is ambiguous. Include its movie or series type.');resolvedType=valid[0].value.kind;}
  const external=await tmdbRequest(`/${tmdbType(resolvedType)}/${tmdb_id}/external_ids`,context,{params:{language:'en-US'}});
  return {imdb_id:/^tt\d+$/.test(external.imdb_id || '')?external.imdb_id:imdb_id,tmdb_id:Number(tmdb_id),tvdb_id:Number(external.tvdb_id || tvdb_id) || null,type:resolvedType};
 });
}

export async function canonicalStreamId(id,type,context){const match=String(id).match(/^(tt\d+|(?:tmdb|tvdb):\d+)(:\d+:\d+)?$/);if(!match)throw new Error('Torrent search requires a valid IMDb title identifier.');const identity=await resolveIdentity({id:match[1],type},context);if(!identity.imdb_id)throw new Error('No IMDb identifier is available. Torrent source lookup is disabled for this title.');return identity.imdb_id+(match[2] || '');}
