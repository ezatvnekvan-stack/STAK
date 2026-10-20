import {sourceSize,sourceBadges} from './sourcePresentation';
test('a season pack never misrepresents the selected episode size as the torrent total',()=>{
 const source={size:1024,size_scope:'file'};expect(sourceSize(source,true)).toBeNull();expect(sourceSize(source,false)).toBe(1024);
 expect(sourceSize({...source,torrent_size:8192},true)).toBe(8192);expect(sourceSize({...source,size_scope:'torrent'},true)).toBe(1024);expect(sourceSize(source,true,{size:4096})).toBe(4096);
});
test('technical badges include requested source properties without duplicate HDR labels',()=>{
 expect(sourceBadges({resolution:'4K',name:'Show.HDR10.Atmos.WEB-DL.REMUX'})).toEqual(['4K','HDR10','Atmos','WEB-DL','REMUX']);
});
