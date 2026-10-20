// Presentation helpers only. Provider IDs and catalog behavior stay in discovery.js.
export const discoverySorts = [['','Recommended'],['popularity.desc','Popular first'],['primary_release_date.desc','Newest first'],['primary_release_date.asc','Oldest first'],['vote_average.desc','Highest TMDb score']];
const languageCodes = 'aa ab ae af ak am an ar as av ay az ba be bg bh bi bm bn bo br bs ca ce ch co cr cs cu cv cy da de dv dz ee el en eo es et eu fa ff fi fj fo fr fy ga gd gl gn gu gv ha he hi ho hr ht hu hy hz ia id ie ig ii ik io is it iu ja jv ka kg ki kj kk kl km kn ko kr ks ku kv kw ky la lb lg li ln lo lt lu lv mg mh mi mk ml mn mr ms mt my na nb nd ne ng nl nn no nr nv ny oc oj om or os pa pi pl ps pt qu rm rn ro ru rw sa sc sd se sg si sk sl sm sn so sq sr ss st su sv sw ta te tg th ti tk tl tn to tr ts tt tw ty ug uk ur uz ve vi vo wa wo xh yi yo za zh zu'.split(' ');
export function discoveryLanguages(query='', current='') {
  let names;
  try { names = new Intl.DisplayNames(['en'], {type:'language'}); } catch { /* ISO code fallback for older WebViews. */ }
  const codes = [...new Set([...languageCodes,...(/^[a-z]{2}$/.test(current)?[current]:[])])];
  const needle = query.trim().toLowerCase();
  return codes.map(code=>({code,name:names?.of(code) || code.toUpperCase()})).filter(item=>`${item.name} ${item.code}`.toLowerCase().includes(needle)).sort((a,b)=>a.name.localeCompare(b.name));
}
export function providerIsSelected(value,selected,service) {
  return value===service.id || Boolean(value && selected?.providerIds && selected.providerIds===service.providerIds);
}
