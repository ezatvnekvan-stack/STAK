import {readStored} from '../storage';
import {accountScope} from '../accountScope';
import {LocalPreferences,getScopedCredential,saveScopedCredential} from '../secureStorage';
export const metadataDefaults={search:'tmdb',movie:'tmdb',series:'tmdb',ratings:true,imdbOnLists:false,language:'en-US'};
export function normalizeMetadataSettings(value={}){return {search:'tmdb',movie:value.movie==='cinemeta'?'cinemeta':'tmdb',series:value.series==='cinemeta'?'cinemeta':'tmdb',ratings:value.ratings!==false,imdbOnLists:value.imdbOnLists===true,language:'en-US'};}
export async function metadataContext(config={}){
 const legacy={'X-TorBox-Key':'tb_api_key','X-TMDb-Key':'tmdb_api_key','X-OMDb-Key':'omdb_api_key'},header=name=>config.headers?.get?.(name) ?? config.headers?.[name] ?? (legacy[name]?readStored(legacy[name]):'') ?? '',scope=await accountScope(header('X-TorBox-Key') || 'guest');
 const saved=await LocalPreferences.get({key:`dmm.local.v1.${scope}.metadata`});let value={};try{value=JSON.parse(saved.value || '{}');}catch{/* recover invalid preferences */}
 const credentials={};for(const provider of ['tmdb','omdb']){const own=await getScopedCredential(scope,provider);credentials[provider]=own===null?header({tmdb:'X-TMDb-Key',omdb:'X-OMDb-Key'}[provider]):own;}
 return {scope,settings:normalizeMetadataSettings(value),credentials,signal:config.signal};
}
export async function saveMetadataSettings(scope,value){const settings=normalizeMetadataSettings(value);await LocalPreferences.set({key:`dmm.local.v1.${scope}.metadata`,value:JSON.stringify(settings)});return settings;}
export async function setMetadataCredential(scope,provider,value){if(!['tmdb','omdb'].includes(provider))throw new Error('Unknown metadata provider.');if(typeof value!=='string' || value.length>4096 || /[\r\n]/.test(value))throw new Error('Invalid provider credential.');await saveScopedCredential(scope,provider,value.trim());}
