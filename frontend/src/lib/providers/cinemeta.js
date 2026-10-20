import {externalRequest} from '../transport';
import {cached,DAY} from '../metadata/cache';
import {normalizeTitleShape,normalizeEpisode} from '../metadata/normalize';
export async function cinemetaRequest(path,context){return cached(`cinemeta:${path}`,DAY,()=>externalRequest(`https://v3-cinemeta.strem.io${path}`,{signal:context.signal}));}
export async function cinemetaDetails(identity,context){if(!identity.imdb_id)throw new Error('Cinemeta requires an IMDb identifier.');const data=await cinemetaRequest(`/meta/${identity.type}/${identity.imdb_id}.json`,context);if(!data.meta || typeof data.meta!=='object')throw new Error('Title not found in Cinemeta.');return normalizeTitleShape({...data.meta,videos:(data.meta.videos || []).map(e=>normalizeEpisode({...e,imdbRating:null},identity))},identity,'cinemeta');}
