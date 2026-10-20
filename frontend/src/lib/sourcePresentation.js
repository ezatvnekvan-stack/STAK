import {qualityTags} from './mediaQuality';
export function sourceSize(source,pack=false,existing) {
  const total=Number(source.torrent_size || existing?.size || existing?.total_size || (source.size_scope==='torrent'?source.size:0));
  if(pack)return Number.isFinite(total) && total>0?total:null;
  const bytes=Number(source.size);return bytes>0 && Number.isFinite(bytes)?bytes:null;
}
export function sourceBadges(source) {
  const tags=qualityTags(`${source.name || ''} ${source.file || ''} ${source.tags || ''}`);
  const ordered=['HDR10+','HDR10','HDR','Dolby Vision','Atmos','WEB-DL','REMUX'];
  const hdr=ordered.slice(0,3).find(tag=>tags.includes(tag));
  return [...new Set([source.resolution || tags.find(tag=>['4K','1080p','720p'].includes(tag)),hdr,...ordered.slice(3).filter(tag=>tags.includes(tag))].filter(Boolean))];
}
