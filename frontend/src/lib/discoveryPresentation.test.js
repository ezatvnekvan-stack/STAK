import {discoveryLanguages,providerIsSelected,discoverySorts} from './discoveryPresentation';
test('language names are searchable by name and code without losing valid stored codes',()=>{
 expect(discoveryLanguages('Hungarian').map(item=>item.code)).toEqual(['hu']);
 expect(discoveryLanguages('ja').some(item=>item.code==='ja')).toBe(true);
 expect(discoveryLanguages('', 'zz').some(item=>item.code==='zz')).toBe(true);
 expect(discoveryLanguages('no such language')).toEqual([]);
});
test('raw provider selections and brand selections share an accurate selected state',()=>{
 const service={id:'prime',providerIds:'119'};
 expect(providerIsSelected('prime',null,service)).toBe(true);
 expect(providerIsSelected('provider:119',{providerIds:'119'},service)).toBe(true);
 expect(providerIsSelected('provider:8',{providerIds:'8'},service)).toBe(false);
 expect(providerIsSelected('',null,service)).toBe(false);
});
test('sorting keeps exactly the original supported backend values',()=>{
 expect(discoverySorts.map(([value])=>value)).toEqual(['','popularity.desc','primary_release_date.desc','primary_release_date.asc','vote_average.desc']);
});
