import {releaseMatch} from './releaseMatch';
import {bestHash,scoreStream} from './score';
const context={meta:{name:'The Odyssey',year:'2026',type:'movie'}},cache=new Set(['clip','film']);
test('cached 4K prologue/DCP cannot be recommended over the requested full movie',()=>{
 const clip={hash:'clip',name:'The Odyssey - Prologue (2025) IMAX DCP rip 4K ProRes 4444 PCM',resolution:'4K',seeders:57},film={hash:'film',name:'The Odyssey (2026) 1080p WEBRip x265',resolution:'1080p',seeders:100};
 expect(releaseMatch(clip,context)).toMatchObject({status:'conflict',recommendable:false});expect(releaseMatch(clip,context).warnings.length).toBeGreaterThan(0);
 expect(bestHash([clip,film],cache,false,context)).toBe('film');expect(scoreStream(film,cache,false,context)).toBeGreaterThan(scoreStream(clip,cache,false,context));
});
test('unknown filenames are retained but never presented as a recommended match',()=>{
 expect(releaseMatch({name:'Some.Release.1080p'},context).status).toBe('uncertain');expect(bestHash([{hash:'clip',name:'Unknown',resolution:'4K'}],cache,false,context)).toBeNull();
});
test('year conflicts, alternate titles and titles containing years are handled explicitly',()=>{
 expect(releaseMatch({name:'The.Odyssey.1997.1080p'},context).status).toBe('conflict');
 expect(releaseMatch({name:'Le.Fabuleux.Destin.2001.1080p'},{meta:{name:'Amelie',aliases:['Le Fabuleux Destin'],year:'2001',type:'movie'}}).status).toBe('likely');
 expect(releaseMatch({name:'Blade.Runner.2049.2017.1080p'},{meta:{name:'Blade Runner 2049',year:'2017',type:'movie'}}).status).toBe('likely');
});
test('season and episode mismatches cannot receive the recommendation badge',()=>{
 const show={meta:{name:'Game of Thrones',year:'2011',type:'series'},season:1,episode:2,packMode:false};
 expect(releaseMatch({name:'Game.of.Thrones.S01E01.1080p'},show).status).toBe('conflict');
 expect(releaseMatch({name:'Game.of.Thrones.S02.2160p'},{...show,packMode:true}).status).toBe('conflict');
 expect(releaseMatch({name:'Game.of.Thrones.S01.2012.1080p'},{...show,packMode:true}).status).toBe('likely');
});
