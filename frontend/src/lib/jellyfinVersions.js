// Group only verified provider/presentation identities. Similar names alone can
// represent remakes or unrelated titles and must never be silently merged.
export function uniqueJellyfinTitles(items){
  const parents=items.map((_,i)=>i),aliases=new Map();
  const root=i=>{while(parents[i]!==i){parents[i]=parents[parents[i]];i=parents[i];}return i;};
  items.forEach((item,i)=>{
    const ids=Object.entries(item.ProviderIds || {}).filter(([key,value])=>['imdb','tmdb','tvdb'].includes(key.toLowerCase()) && value).map(([key,value])=>`${item.Type}:${key.toLowerCase()}:${value}`);
    if(item.PresentationUniqueKey)ids.push(`${item.Type}:presentation:${item.PresentationUniqueKey}`);
    ids.push(`${item.Type}:item:${item.Id}`);
    for(const id of ids){if(aliases.has(id))parents[root(i)]=root(aliases.get(id));else aliases.set(id,i);}
  });
  const groups=new Map();items.forEach((item,i)=>{const id=root(i);if(!groups.has(id))groups.set(id,[]);groups.get(id).push(item);});
  return [...groups.values()].map(group=>{
    const resume=group.filter(item=>!item.UserData?.Played && Number(item.UserData?.PlaybackPositionTicks)>0).sort((a,b)=>String(b.UserData?.LastPlayedDate || '').localeCompare(String(a.UserData?.LastPlayedDate || '')))[0];
    return {...(resume || group.find(item=>item.UserData?.Played) || group[0]),VersionCount:group.length};
  });
}
