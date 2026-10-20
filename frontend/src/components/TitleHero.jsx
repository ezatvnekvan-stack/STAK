import { useState } from 'react';
import { useMetadataQuery } from '../lib/metadataQuery';
import { ArrowLeft } from 'lucide-react';
import RatingsBlock from './RatingsBlock';
export default function TitleHero({ meta, onBack }) {
  const [failedLogo,setFailedLogo] = useState(null);
  const [failedArtwork,setFailedArtwork] = useState(null);
  const id=meta.imdb_id || meta.id;
  const { data } = useMetadataQuery('/meta/title-logo',{imdb_id:id,type:meta.type});
  const logo=data?.logo, info=data?.series_info;
  const year=String(meta.year || meta.releaseInfo || '').match(/\d{4}/)?.[0] || info?.first_air_date?.slice(0,4);
  const details=[year, meta.status || info?.status, meta.runtime].filter(Boolean);
  return <header className="title-hero" data-artwork={!!meta.background && failedArtwork!==meta.background}>
    {meta.background && failedArtwork!==meta.background && <img src={meta.background} alt="" className="title-hero-art" decoding="async" fetchpriority="high" onError={()=>setFailedArtwork(meta.background)}/>}<div className="title-hero-fade"/>
    <button data-testid="title-back-button" className="n-icon-btn absolute left-4 top-3 z-10" onClick={onBack} aria-label="Back to previous page"><ArrowLeft size={17}/></button>
    <div className="title-hero-content">
      <div className="title-identity"><div className="title-branding">
        <h1 data-testid="title-name" className={logo && failedLogo!==logo?'sr-only':'font-dot'}>{meta.name}</h1>
        {logo && failedLogo!==logo && <img src={logo} alt="" decoding="async" onError={()=>setFailedLogo(logo)}/>}
      </div>
      <div className="flex flex-wrap gap-x-2 text-[10px] text-neutral-400">{details.map((detail,i)=><span key={i}>{i?'· ':''}{detail}</span>)}</div>
      <RatingsBlock meta={meta}/>
      <p className="mt-2 text-[10px] text-neutral-400">{(meta.genres || []).slice(0,3).join(' · ')}</p>
      </div>
    </div>
  </header>;
}
