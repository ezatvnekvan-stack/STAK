export const emptyDiscoveryFilters={genre:'',year_from:'',year_to:'',imdb_rating:'',certification:'',language:'',provider:'',sort:''};
export const discoveryChannels=[
  {value:'popular',label:'Popular',description:'Popular titles, with your current filters.'},
  {value:'trending',label:'Trending now',description:'Current TMDb trending titles; filters apply to loaded pages.'},
  {value:'top',label:'Top rated · TMDb',description:'Audience scores from TMDb, with a minimum vote count. This is not IMDb Top 250.'},
  {value:'new',label:'New releases',description:'First releases from the last 90 days.'},
  {value:'upcoming',label:'Upcoming',description:'Scheduled first releases over the next year; dates may change.'},
  {value:'hidden',label:'Hidden gems',description:'Lower-popularity titles with at least 100 TMDb votes and a TMDb score of 7 or higher.'},
];
export const discoveryCategory=value=>discoveryChannels.some(channel=>channel.value===value)?value:'popular';
export function selectedProvider(value,services,providers=[]){
  if(!value)return null;
  if(value.startsWith('provider:')){const id=value.slice(9),provider=providers.find(p=>String(p.id)===id);return provider?{name:provider.name,providerIds:id,available:true,logo:provider.logo}:null;}
  return services.find(service=>service.id===value) || null;
}
export function discoveryFilterChips({filters,selected,hideWatched,hideLibrary},genres=[]){
  const genre=genres.find(g=>String(typeof g==='string'?g:g.id)===String(filters.genre));
  return [filters.provider && ['provider',selected?.name || 'Selected service'],filters.genre && ['genre',typeof genre==='string'?genre:genre?.name || 'Genre'],(filters.year_from || filters.year_to) && ['year',`${filters.year_from || '…'}–${filters.year_to || '…'}`],filters.imdb_rating && ['imdb_rating',`IMDb ${filters.imdb_rating}+`],filters.language && ['language',filters.language.toUpperCase()],filters.certification && ['certification',filters.certification],filters.sort && ['sort','Custom order'],hideWatched && ['hideWatched','Hide watched'],hideLibrary && ['hideLibrary','Hide library titles']].filter(Boolean);
}
