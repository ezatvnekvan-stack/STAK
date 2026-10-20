import {normalizeTitle,searchTerms} from '../searchRelevance';
import {arrayResponse,objectResponse} from '../validation';
import {tmdbRequest,tmdbType} from '../providers/tmdb';
import {omdbRequest} from '../providers/omdb';
import {resolveIdentity} from './resolveIdentity';
import {image,normalizeTitleShape} from './normalize';
const result=(config,data)=>({data,status:200,statusText:'OK',headers:{},config});
export async function catalogRoute(config,context,executeAdapter){
 const method=(config.method || 'get').toLowerCase(),path=String(config.url || '').split('?')[0],params={...Object.fromEntries(new URL(config.url,'https://local.invalid').searchParams),...config.params};
  const tmdb=(route,options={})=>tmdbRequest(route,context,options);
  const tmdbFind=async id=>{const identity=await resolveIdentity({id,type:params.type},context);return identity.tmdb_id?{id:identity.tmdb_id,type:tmdbType(identity.type)}:null;};
  const tmdbPoster=path=>image(path);
  const tmdbItem=(m,kind)=>normalizeTitleShape(m,{tmdb_id:m.id,type:kind==='tv'?'series':'movie',imdb_id:m.imdb_id},'tmdb');
  if (path === "/meta/genres" && method === "get") {
    if (!context.credentials.tmdb) return result(config, { movie: ["Action", "Adventure", "Animation", "Comedy", "Crime", "Documentary", "Drama", "Family", "Fantasy", "History", "Horror", "Mystery", "Romance", "Science Fiction", "Thriller", "War", "Western"], series: ["Action", "Adventure", "Animation", "Comedy", "Crime", "Documentary", "Drama", "Family", "Fantasy", "Mystery", "Science Fiction"] });
    const [movie, tv] = await Promise.all([tmdb("/genre/movie/list"), tmdb("/genre/tv/list")]);
    const genres = list => arrayResponse(list, "TMDb genres").filter(g => Number.isInteger(g.id) && typeof g.name === "string").map(g => ({ id: g.id, name: g.name }));
    return result(config, { movie: genres(movie.genres), series: genres(tv.genres) });
  }
  if (path === "/discover/providers" && method === "get") {
    const region = /^[A-Z]{2}$/.test(String(params.region || '')) ? params.region : 'US';
    const [providers, regions] = await Promise.all([tmdb(`/watch/providers/${tmdbType(params.type)}`, { params: { watch_region: region } }), tmdb('/watch/providers/regions')]);
    return result(config, { region, providers: arrayResponse(providers.results, 'TMDb providers').map(p => ({ id: p.provider_id, name: p.provider_name, logo: tmdbPoster(p.logo_path) })), regions: arrayResponse(regions.results, 'TMDb regions').map(r => ({ id: r.iso_3166_1, name: r.english_name })) });
  }
  if (path === "/discover/directors" && method === "get") {
    const query = String(params.q || '').trim().slice(0, 120);
    const pages = query ? [await tmdb('/search/person', { params: { query, include_adult: false } })] : await Promise.all([1, 2, 3].map(page => tmdb('/person/popular', { params: { page } })));
    const people = pages.flatMap(page => arrayResponse(page.results, 'TMDb people')).filter(p => p.known_for_department === 'Directing');
    return result(config, { items: [...new Map(people.map(p => [p.id, { id: p.id, name: p.name, photo: tmdbPoster(p.profile_path) }])).values()] });
  }
  if (path === "/discover/collections" && method === "get") {
    const q=String(params.q || '').trim().slice(0,120),page=Math.max(1,Math.min(500,Number(params.page)||1));
    if(!q) return executeAdapter({...config,url:'/discover/tmdb',params:{type:'movie',category:'collections',page}});
    const query=/^mcu$/i.test(q)?'Marvel Cinematic Universe':q;
    const results=await Promise.allSettled([tmdb('/search/collection',{params:{query,page,include_adult:false}}),tmdb('/search/keyword',{params:{query,page}})]);
    if(results.every(r=>r.status==='rejected'))throw results[0].reason;
    const items=results.flatMap((r,index)=>r.status==='fulfilled'?arrayResponse(r.value.results,'TMDb collections').map(c=>({id:c.id,name:c.name,poster:c.poster_path?tmdbPoster(c.poster_path):null,kind:index?'keyword':'collection'})):[]);
    return result(config,{items,page,total_pages:Math.max(...results.map(r=>r.status==='fulfilled'?Math.min(500,Number(r.value.total_pages)||page):page)),errors:results.flatMap((r,i)=>r.status==='rejected'?[i?'Universe search unavailable':'Collection search unavailable']:[])});
  }
  if (path === "/discover/collection-items" && method === "get") {
    if(!/^\d+$/.test(String(params.id || '')))throw new Error('Invalid collection identifier.');
    if(params.kind==='collection')return executeAdapter({...config,url:'/discover/tmdb',params:{type:'movie',category:'collections',collection_id:params.id}});
    if(params.kind!=='keyword')throw new Error('Unsupported collection type.');
    const type=tmdbType(params.type),page=Math.max(1,Math.min(500,Number(params.page)||1));
    const data=await tmdb(`/discover/${type}`,{params:{with_keywords:params.id,page,include_adult:false,sort_by:'primary_release_date.asc',...(type==='tv'?{sort_by:'first_air_date.asc'}:{})}});
    return result(config,{items:arrayResponse(data.results,'TMDb universe catalog').map(m=>tmdbItem(m,type)),page,total_pages:Math.min(500,Number(data.total_pages)||page)});
  }
  if (path === "/discover/tmdb" && method === "get") {
    if(params.genre && !/^\d+(?:[|,]\d+)*$/.test(String(params.genre))){const list=await tmdb(`/genre/${tmdbType(params.type)}/list`);const genre=(list.genres || []).find(g=>normalizeTitle(g.name)===normalizeTitle(params.genre));if(!genre)return result(config,{items:[],page:1,total_pages:1});params.genre=genre.id;}
    const type = tmdbType(params.type), category = params.category || "trending";
    const page = Math.max(1, Number(params.page) || 1);
    let route = `/trending/${type}/${params.period === "day" ? "day" : "week"}`;
    if (category === "popular") route = `/${type}/popular`;
    if (category === "top") route = `/${type}/top_rated`;
    if (["new", "upcoming"].includes(category)) route = `/discover/${type}`;
    if (category === "search") route = `/search/${type}`;
    if (category === "catalog") route = `/discover/${type}`;
    if (category === "hidden") route = `/discover/${type}`;
    if (category === "collections") {
      if (type !== "movie") return result(config, { items: [], collections: [] });
      if (params.collection_id) {
        if (!/^\d+$/.test(String(params.collection_id))) throw new Error('Invalid collection identifier.');
        const collection = objectResponse(await tmdb(`/collection/${params.collection_id}`), 'TMDb collection');
        return result(config, { items: arrayResponse(collection.parts, 'TMDb collection').map(m => tmdbItem(m, 'movie')), collections: [], name: collection.name });
      }
      const trending = await tmdb("/movie/popular", { params: { page } });
      const settled = await Promise.allSettled(arrayResponse(trending.results, "TMDb").slice(0, 12).map(m => tmdb(`/movie/${m.id}`)));
      const unique = new Map();
      for (const r of settled) if (r.status === "fulfilled" && r.value?.belongs_to_collection) { const c = r.value.belongs_to_collection; unique.set(c.id, c); }
      return result(config, { items: [], collections: [...unique.values()] });
    }
    const discoverParams = {
      page, with_genres: params.genre || undefined, "primary_release_date.gte": params.year_from ? `${params.year_from}-01-01` : undefined,
      "primary_release_date.lte": params.year_to ? `${params.year_to}-12-31` : undefined,
      "vote_average.gte": params.rating || undefined, with_original_language: params.language || undefined,
      with_watch_providers: params.provider || undefined, watch_region: params.provider ? (params.region || "US") : undefined,
      sort_by: params.sort || (category === "top" ? "vote_average.desc" : "popularity.desc"),
      include_adult: false, with_watch_monetization_types: params.provider ? "flatrate|free|ads" : undefined,
      "vote_count.gte": category === "top" ? 200 : undefined
    };
    if (!["trending", "search"].includes(category) && (category === "hidden" || params.genre || params.year_from || params.year_to || params.rating || params.language || params.provider || params.sort || params.certification)) route = `/discover/${type}`;
    const today = new Date().toISOString().slice(0,10);
    if(category==='top' || params.released_only){
      const requested=discoverParams['primary_release_date.lte'];
      discoverParams['primary_release_date.lte']=requested && requested<today?requested:today;
      route=`/discover/${type}`;
    }
    if (type === "tv") {
      if (discoverParams["primary_release_date.gte"]) discoverParams["first_air_date.gte"] = discoverParams["primary_release_date.gte"];
      if (discoverParams["primary_release_date.lte"]) discoverParams["first_air_date.lte"] = discoverParams["primary_release_date.lte"];
      delete discoverParams["primary_release_date.gte"]; delete discoverParams["primary_release_date.lte"];
      discoverParams.sort_by = discoverParams.sort_by.replace("primary_release_date", "first_air_date");
    }
    if (["new", "upcoming"].includes(category)) {
      const boundary = new Date(); boundary.setUTCDate(boundary.getUTCDate() + (category === "new" ? -90 : 365));
      const dateKey = type === "tv" ? "first_air_date" : "primary_release_date";
      const start = category === "new" ? boundary.toISOString().slice(0,10) : today;
      const end = category === "new" ? today : boundary.toISOString().slice(0,10);
      discoverParams[`${dateKey}.gte`] = [start, discoverParams[`${dateKey}.gte`]].filter(Boolean).sort().pop();
      discoverParams[`${dateKey}.lte`] = [end, discoverParams[`${dateKey}.lte`]].filter(Boolean).sort()[0];
      if (discoverParams[`${dateKey}.gte`] > discoverParams[`${dateKey}.lte`]) return result(config, { items: [], collections: [], page, total_pages: page });
      discoverParams.sort_by = (params.sort || `${dateKey}.${category === "new" ? "desc" : "asc"}`).replace('primary_release_date','first_air_date');
      if (type === 'movie') discoverParams.sort_by = discoverParams.sort_by.replace('first_air_date','primary_release_date');
    }
    if (category === "hidden") Object.assign(discoverParams, { "vote_count.gte": 100, "vote_average.gte": params.rating || 7, "popularity.lte": 30 });
    if (params.certification && type === "movie") Object.assign(discoverParams, { certification_country: "US", certification: params.certification });
    const imdbQuery=category==='search' && /^tt\d+$/.test(String(params.query || ''));
    const found=imdbQuery?await tmdb(`/find/${params.query}`,{params:{external_source:'imdb_id'}}):null;
    const data=imdbQuery?{results:found[type==='tv'?'tv_results':'movie_results'] || [],page:1,total_pages:1}:await tmdb(route,{params:route.startsWith('/discover/')?discoverParams:{page,...(category==='search'?{query:String(params.query || '').slice(0,160),include_adult:false}:{})}});
    let entries = arrayResponse(data.results, 'TMDb discovery');
    // Enforce release eligibility even when a provider returns inconsistent rows.
    if(category==='top' || params.released_only)entries=entries.filter(item=>{const date=item.release_date || item.first_air_date;return date && date<=today && (category!=='top' || Number(item.vote_count)>=200);});
    const parts=searchTerms(params.query);
    if(category==='search' && parts.year && !entries.some(item=>[item.title,item.name,item.original_title,item.original_name].some(name=>normalizeTitle(name)===normalizeTitle(params.query)))){
      const fallback=await tmdb(`/search/${type}`,{params:{page,query:parts.title,include_adult:false}});
      entries=arrayResponse(fallback.results,'TMDb title/year search').filter(item=>String(item.release_date || item.first_air_date || '').startsWith(parts.year));
      data.total_pages=fallback.total_pages;
    }
    if (["trending", "search"].includes(category)) {
      const earliest = new Date(); earliest.setUTCFullYear(earliest.getUTCFullYear() - 2);
      entries = entries.filter(m => {
        const date = m.release_date || m.first_air_date || '', year = Number(date.slice(0,4));
        return (!params.genre || (m.genre_ids || []).map(String).includes(String(params.genre))) && (!params.year_from || year >= Number(params.year_from)) && (!params.year_to || year <= Number(params.year_to)) && (!params.rating || Number(m.vote_average) >= Number(params.rating)) && (!params.language || m.original_language === params.language) && (!params.recent_only || (date && date >= earliest.toISOString().slice(0,10) && date <= today));
      });
      if (params.provider) {
        const accepted = new Set(); let cursor = 0;
        await Promise.all(Array.from({ length: Math.min(4, entries.length) }, async () => { while (cursor < entries.length) { const item = entries[cursor++]; const availability = await tmdb(`/${type}/${item.id}/watch/providers`); const local = availability.results?.[params.region || 'US']; if (['flatrate', 'free', 'ads'].some(kind => (local?.[kind] || []).some(p => String(params.provider).split('|').includes(String(p.provider_id))))) accepted.add(item.id); } }));
        entries = entries.filter(m => accepted.has(m.id));
      }
    }
    if (params.certification && type === 'movie' && ["trending", "search"].includes(category)) {
      const accepted = new Set(); let cursor = 0;
      await Promise.all(Array.from({ length: Math.min(4, entries.length) }, async () => { while (cursor < entries.length) { const item = entries[cursor++]; const ratings = await tmdb(`/movie/${item.id}/release_dates`); if ((ratings.results || []).some(r => r.iso_3166_1 === 'US' && (r.release_dates || []).some(d => d.certification === params.certification))) accepted.add(item.id); } }));
      entries = entries.filter(m => accepted.has(m.id));
    }
    if (params.certification && type === 'tv') {
      // TMDb has no TV certification discover parameter. Use its existing
      // content_ratings capability for the returned page, and report failures.
      const matches = new Set(); let cursor = 0;
      await Promise.all(Array.from({ length: Math.min(4, entries.length) }, async () => {
        while (cursor < entries.length) {
          const title = entries[cursor++];
          const ratings = objectResponse(await tmdb(`/tv/${title.id}/content_ratings`), 'TMDb content ratings');
          if ((ratings.results || []).some(r => r.iso_3166_1 === 'US' && r.rating === params.certification)) matches.add(title.id);
        }
      }));
      entries = entries.filter(m => matches.has(m.id));
    }
    if (['trending', 'search'].includes(category) && params.sort) {
      const [field, direction] = String(params.sort).split('.');
      const value = item => field === 'primary_release_date' ? Date.parse(item.release_date || item.first_air_date || '') || 0 : Number(item[field]) || 0;
      entries.sort((a,b) => (value(a)-value(b)) * (direction === 'asc' ? 1 : -1));
    }
    if(Number(params.imdb_rating)>0 && (!context.settings.ratings || !context.credentials.omdb))throw new Error('Enable OMDb ratings and configure its key before using the IMDb filter. Daily OMDb quota applies.');
    let ratingErrors=0;
    if(Number(params.imdb_rating)>0){
      let cursor=0;const verified=new Map();
      await Promise.all(Array.from({length:Math.min(4,entries.length)},async()=>{while(cursor<entries.length){const item=entries[cursor++];try{
        const ids=await tmdb(`/${type}/${item.id}/external_ids`),imdb=ids.imdb_id;
        if(!/^tt\d+$/.test(imdb || ''))continue;
        const data=await omdbRequest(imdb,context);
        const rating=Number(data?.imdbRating);
        if(Number.isFinite(rating) && rating>=Number(params.imdb_rating) && rating<=10)verified.set(item.id,{imdb,rating});
      }catch{ratingErrors++;}}}));
      entries=entries.filter(item=>verified.has(item.id)).map(item=>({...item,imdb_id:verified.get(item.id).imdb,_verifiedImdbRating:verified.get(item.id).rating}));
    }
    return result(config, { items: entries.map(m => ({...tmdbItem(m,type),imdbRating:Number(params.imdb_rating)>0?m._verifiedImdbRating || null:null})),imdb_filter:Number(params.imdb_rating)>0,rating_errors:ratingErrors, collections: [], page, total_pages: Number(data.total_pages) || page, filter_scope: (['trending', 'search'].includes(category) && [params.genre, params.year_from, params.year_to, params.rating, params.language, params.provider, params.certification, params.sort].some(Boolean)) || (params.certification && type === 'tv') ? 'Filters apply to the returned provider page; availability is regional TMDb / JustWatch data.' : null });
  }
  if (path === "/meta/certification" && method === "get") {
    const match = await tmdbFind(params.imdb_id);
    if (!match) return result(config, { certification: null });
    const data = await tmdb(`/${match.type}/${match.id}/${match.type === "tv" ? "content_ratings" : "release_dates"}`);
    const us = (data.results || []).find(x => x.iso_3166_1 === "US");
    return result(config, { certification: match.type === "tv" ? us?.rating || null : us?.release_dates?.find(x => x.certification)?.certification || null });
  }
  if (path === "/meta/extras" && method === "get") {
    const match = await tmdbFind(params.imdb_id);
    if (!match) return result(config, { configured: true, cast: [], crew: [], similar: [], collection: [], videos: [] });
    const details = await tmdb(`/${match.type}/${match.id}`, { params: { append_to_response: `credits,similar,recommendations,videos,images${match.type === "tv" ? ",aggregate_credits" : ""}` } });
    const cast = (details.credits?.cast || []).slice(0, 18).map(p => ({ id: p.id, name: p.name,
      character: p.character || "", photo: tmdbPoster(p.profile_path) }));
    const creators = match.type === 'tv' ? (details.created_by || []).map(p => ({ id: p.id, name: p.name, job: 'Creator' })) : [];
    const aggregate = match.type === 'tv' ? (details.aggregate_credits?.crew || []).flatMap(p => (p.jobs || []).filter(j=>['Writer','Screenplay','Story','Teleplay','Showrunner'].includes(j.job)).map(j=>({...p,job:j.job}))) : [];
    const crew = [...creators, ...aggregate, ...(details.credits?.crew || []).filter(p => ['Director', 'Writer', 'Screenplay', 'Story', 'Teleplay'].includes(p.job) && (match.type === 'movie' || p.job !== 'Director')).map(p => ({ id: p.id, name: p.name, job: p.job }))].filter((p,i,a) => a.findIndex(x => x.id === p.id && x.job === p.job) === i);
    const images = (details.images?.backdrops || []).slice(0, 12).map(image => ({ thumb: `https://image.tmdb.org/t/p/w500${image.file_path}`, full: `https://image.tmdb.org/t/p/original${image.file_path}` }));
    const similar = [...(details.recommendations?.results || []), ...(details.similar?.results || [])].slice(0, 18).map(m => tmdbItem(m, match.type));
    let collection = [];
    if (details.belongs_to_collection?.id) {
      const c = await tmdb(`/collection/${details.belongs_to_collection.id}`).catch(() => null);
      collection = (c?.parts || []).map(m => tmdbItem(m, "movie"));
    }
    const videos = (details.videos?.results || []).filter(v => v.site === "YouTube" && v.key)
      .map(v => ({ key: v.key, name: v.name, type: v.type }));
    return result(config, { configured: true, cast, crew, images, similar, collection, videos });
  }
  const personMatch = path.match(/^\/meta\/person\/(\d+)$/);
  if (personMatch && method === "get") {
    const id = personMatch[1];
    const person = await tmdb(`/person/${id}`, { params: { append_to_response: "combined_credits" } });
    return result(config, { ...person, photo: tmdbPoster(person.profile_path),
      items: [...new Map([...(person.combined_credits?.crew || []).filter(m => ['Director', 'Writer', 'Screenplay', 'Creator'].includes(m.job)), ...(person.combined_credits?.cast || [])].map(m => [`${m.media_type}:${m.id}`, { ...tmdbItem(m, m.media_type), credit_role: m.job || m.character || null }])).values()].slice(0, 100) });
  }
  if (path === "/meta/trailer" && method === "get") {
    const match = await tmdbFind(params.imdb_id);
    if (!match) return result(config, { youtube_key: null });
    const videos = await tmdb(`/${match.type}/${match.id}/videos`);
    const video = (videos.results || []).find(v => v.site === "YouTube" && v.type === "Trailer" && v.official) ||
      (videos.results || []).find(v => v.site === "YouTube" && v.type === "Trailer");
    return result(config, { youtube_key: video?.key || null });
  }
  if (path === "/meta/title-logo" && method === "get") {
    const match = await tmdbFind(params.imdb_id);
    if (!match) return result(config, {});
    const [images, details] = await Promise.all([tmdb(`/${match.type}/${match.id}/images`, { params: { include_image_language: 'en,null' } }), tmdb(`/${match.type}/${match.id}`, { params: { append_to_response: "external_ids" } })]);
    const logo = images.logos?.find(l => l.iso_639_1 === 'en') || images.logos?.find(l => l.iso_639_1 == null);
    const url = logo ? `https://image.tmdb.org/t/p/original${logo.file_path}` : null;
    const network = details?.networks?.[0];
    return result(config, { url, logo: url, genres: (details?.genres || []).map(g=>g.name).filter(Boolean), imdb_id: details?.imdb_id || details?.external_ids?.imdb_id || null, network: network ? { name: network.name, logo: tmdbPoster(network.logo_path) } : null, series_info: match.type === 'tv' && details ? { first_air_date: details.first_air_date, last_air_date: details.last_air_date, status: details.status } : null });
  }
  if (path === "/meta/season-posters" && method === "get") {
    const match = await tmdbFind(params.imdb_id);
    if (!match || match.type !== "tv") return result(config, { posters: {} });
    const show = await tmdb(`/tv/${match.id}`);
    return result(config, { posters: Object.fromEntries((show.seasons || []).filter(x => x.poster_path).map(x => [x.season_number, tmdbPoster(x.poster_path)])) });
  }

}
