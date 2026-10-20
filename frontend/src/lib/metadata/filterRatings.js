import {omdbRequest} from '../providers/omdb';
import {mapLimit} from './cache';
export async function filterImdb(items,minimum,context){
 if(!context.settings.ratings || !context.credentials.omdb)throw new Error('Enable OMDb ratings and configure its key before using the IMDb filter. Daily OMDb quota applies.');
 let errors=0;const checked=await mapLimit(items,async item=>{try{const data=await omdbRequest(item.imdb_id || item.id,context),score=Number(data?.imdbRating);return score>=minimum && score<=10?{...item,imdbRating:score,rating:{...item.rating,imdb:score}}:null;}catch(error){if(error.code==='ERR_CANCELED')throw error;errors++;return null;}});
 return {items:checked.filter(Boolean),errors:errors?['Some IMDb ratings could not be verified. OMDb daily quota applies.']:[]};
}
