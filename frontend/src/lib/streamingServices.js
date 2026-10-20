const brands = [
  ['apple','Apple TV',/^Apple TV(?:\+| Plus)?$/i],
  ['netflix','Netflix',/^Netflix(?: Standard with Ads)?$/i],
  ['hbo','HBO Max',/^(?:HBO Max|Max)$/i],
  ['disney','Disney Plus',/^Disney(?:\+| Plus)$/i],
  ['hulu','Hulu',/^Hulu$/i],
  ['prime','Prime Video',/^(?:Amazon )?Prime Video(?: with Ads)?$/i],
  ['paramount','Paramount Plus',/^Paramount(?:\+| Plus)(?: with Showtime)?$/i],
];
// IDs and availability come from the live region/type catalog, never invented.
export function streamingServices(providers=[]) {
  return brands.map(([id,name,pattern])=>{
    const matches=(Array.isArray(providers)?providers:[]).filter(p=>pattern.test(p.name || '') && /^\d+$/.test(String(p.id)));
    return {id,name,providerIds:[...new Set(matches.map(p=>String(p.id)))].join('|'),logo:matches[0]?.logo || null,available:matches.length>0};
  });
}
