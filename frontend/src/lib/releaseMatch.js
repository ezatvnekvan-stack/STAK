import {seasonRange,isSeasonPack} from './seasonPack';
const normalize=value=>String(value || '').normalize('NFKD').replace(/[\u0300-\u036f]/g,'').toLowerCase().replace(/[^\p{L}\p{N}]+/gu,' ').trim();
const tokens=value=>normalize(value).split(' ').filter(Boolean);
const year=value=>String(value || '').match(/\b(?:19|20)\d{2}\b/)?.[0];
export function releaseMatch(source,context) {
  if(!context?.meta)return {status:'uncertain',label:'Match unconfirmed',reasons:['No title context was provided.'],warnings:[],recommendable:false};
  const {meta,season,episode,packMode}=context;
  const name=String(source.name || ''),all=`${name} ${source.tags || ''} ${source.file || ''}`;
  const names=[meta.name,meta.original_title,meta.original_name,...(Array.isArray(meta.aliases)?meta.aliases:[])].filter(value=>typeof value==='string' && value.trim());
  const technical=/\b(?:prores|dcp|4444|dci|cam|hdcam|telesync|telecine)\b/i.test(all);
  const warnings=technical?['Production or camera-release format detected; playback compatibility is unverified.']:[];
  const reasons=[];
  const extras=/\b(?:prologue|sample|trailer|teaser|featurette|behind[ ._-]the[ ._-]scenes|deleted[ ._-]scenes)\b/i;
  const clip=name.match(extras)?.[0];
  if(clip && !names.some(title=>normalize(title).includes(normalize(clip))))reasons.push(`Release is labelled ${clip}; it may not be the requested full title.`);
  const clean=name.replace(/[._]+/g,' '),cut=clean.search(/\bS\d{1,2}(?:E\d+)?\b|\b\d{1,2}x\d{2,3}\b|\b(?:19|20)\d{2}\b|\b(?:2160p|1080p|720p|480p|4K|UHD|WEB|BluRay|REMUX|HDR)\b/i);
  const prefix=normalize(cut>0?clean.slice(0,cut):clean),normalized=normalize(name);
  const exact=names.some(title=>{
    const n=normalize(title);if(!n)return false;
    // Titles containing a year (e.g. Blade Runner 2049) keep that token.
    if(tokens(title).some(token=>/^(19|20)\d{2}$/.test(token)))return normalized===n || normalized.startsWith(n+' ');
    return prefix===n;
  });
  const expectedYear=year(meta.year || meta.releaseInfo),titleYears=new Set(names.flatMap(tokens).filter(t=>/^(19|20)\d{2}$/.test(t)));
  const releaseYears=[...clean.matchAll(/\b(?:19|20)\d{2}\b/g)].map(m=>m[0]).filter(y=>!titleYears.has(y));
  if(meta.type!=='series' && expectedYear && releaseYears.length && releaseYears[0]!==expectedYear)reasons.push(`Release year ${releaseYears[0]} differs from title year ${expectedYear}.`);
  if(meta.type==='series'){
    const range=seasonRange(name),pair=name.match(/\bS(\d{1,2})[ ._-]?E(\d{1,3})\b|\b(\d{1,2})x(\d{2,3})\b/i);
    if(range && season>0 && (season<range[0] || season>range[1]))reasons.push('Release targets a different season.');
    if(packMode && pair && !isSeasonPack(source,season))reasons.push('This is a single episode, not a season pack.');
    if(!packMode && pair && !isSeasonPack(source,season) && (Number(pair[1] || pair[3])!==Number(season) || Number(pair[2] || pair[4])!==Number(episode)))reasons.push('Release targets a different episode.');
  }
  const status=reasons.length?'conflict':exact?'likely':'uncertain';
  return {status,label:status==='conflict'?'Check title match':status==='likely'?'Filename matches':'Match unconfirmed',reasons:reasons.length?reasons:exact?[]:['The filename could not be confidently matched to this title.'],warnings,recommendable:status==='likely' && !warnings.length};
}
