import {useState} from 'react';
import NothingSelect from './NothingSelect';
import {Switch} from './ui/switch';
import {discoveryLanguages} from '../lib/discoveryPresentation';

export default function DiscoveryFiltersPanel({filters,genreList,type,hideWatched,hideLibrary,change,update,setSaved}) {
  const [languageQuery,setLanguageQuery]=useState('');
  const languages=discoveryLanguages(languageQuery,filters.language);
  // Keep the current choice available while searching, without silently clearing it.
  if(filters.language && !languages.some(item=>item.code===filters.language))languages.unshift(...discoveryLanguages('',filters.language).filter(item=>item.code===filters.language));
  return <div className="discovery-filter-sections">
    <fieldset><legend>Content</legend><div className="discovery-filter-grid">
      <label className="filter-label">Genre<NothingSelect aria-label="Genre" value={filters.genre} onChange={e=>change('genre',e.target.value)}><option value="">All genres</option>{genreList.map(genre=><option key={typeof genre==='string'?genre:genre.id} value={typeof genre==='string'?genre:genre.id}>{typeof genre==='string'?genre:genre.name}</option>)}</NothingSelect></label>
      <label className="filter-label">US age rating<NothingSelect aria-label="US age rating" value={filters.certification} onChange={e=>change('certification',e.target.value)}>{(type==='series'?['','TV-Y','TV-Y7','TV-G','TV-PG','TV-14','TV-MA']:['','G','PG','PG-13','R','NC-17']).map(value=><option key={value} value={value}>{value || 'Any'}</option>)}</NothingSelect></label>
      <label className="filter-label discovery-full-field">Find a language<input aria-label="Find a language" className="n-input" type="search" value={languageQuery} placeholder="Language name or code" onChange={e=>setLanguageQuery(e.target.value)}/></label>
      <label className="filter-label discovery-full-field">Original language<NothingSelect aria-label="Original language" value={filters.language} onChange={e=>change('language',e.target.value)}><option value="">Any language</option>{languages.map(item=><option key={item.code} value={item.code}>{item.name} · {item.code.toUpperCase()}</option>)}</NothingSelect></label>
      {!languages.length && <p role="status" className="discovery-full-field discovery-help">No matching language. Try a name or two-letter code.</p>}
    </div></fieldset>
    <fieldset><legend>Release</legend><div className="discovery-filter-grid">
      <label className="filter-label discovery-full-field">Decade<NothingSelect aria-label="Decade" value={filters.year_from && Number(filters.year_to)===Number(filters.year_from)+9?filters.year_from:''} onChange={e=>setSaved(previous=>({...previous,filters:{...previous.filters,year_from:e.target.value,year_to:e.target.value?String(Number(e.target.value)+9):''}}))}><option value="">Any / custom range</option>{Array.from({length:13},(_,i)=>2020-i*10).map(year=><option value={year} key={year}>{year}s</option>)}</NothingSelect></label>
      <label className="filter-label">From year<input aria-label="From year" className="n-input" type="number" inputMode="numeric" min="1880" max="2100" value={filters.year_from} onChange={e=>change('year_from',e.target.value)}/></label>
      <label className="filter-label">To year<input aria-label="To year" className="n-input" type="number" inputMode="numeric" min="1880" max="2100" value={filters.year_to} onChange={e=>change('year_to',e.target.value)}/></label>
    </div></fieldset>
    <fieldset><legend>Ratings</legend><label className="filter-label">Minimum IMDb rating<NothingSelect aria-label="IMDb rating" value={filters.imdb_rating || ''} onChange={e=>change('imdb_rating',e.target.value)}>{['','6','7','8','9'].map(value=><option key={value} value={value}>{value?`${value}+`:'Any'}</option>)}</NothingSelect></label><p className="discovery-help">Uses your OMDb quota. Unrated titles are excluded; ratings are cached for 24 hours.</p></fieldset>
    <fieldset><legend>Your library</legend><div className="discovery-switch-row"><label htmlFor="discover-hide-watched">Hide watched</label><Switch id="discover-hide-watched" aria-label="Hide watched" checked={hideWatched} onCheckedChange={value=>update({hideWatched:value})}/></div><div className="discovery-switch-row"><label htmlFor="discover-hide-library">Hide known library titles</label><Switch id="discover-hide-library" aria-label="Hide known library titles" checked={hideLibrary} onCheckedChange={value=>update({hideLibrary:value})}/></div></fieldset>
  </div>;
}
