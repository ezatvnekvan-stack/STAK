import {useState} from 'react';
import {Check,ChevronDown} from 'lucide-react';
import {Sheet,SheetContent,SheetHeader,SheetTitle,SheetDescription} from './ui/sheet';
import {Button} from './StakUI';
import {providerIsSelected} from '../lib/discoveryPresentation';

export default function DiscoveryServices({value,selected,services,providers,loading,error,onRetry,onChange}) {
  const [open,setOpen]=useState(false),[search,setSearch]=useState(''),[failed,setFailed]=useState(()=>new Set());
  const choose=next=>{onChange(next);setOpen(false);setSearch('');};
  const art=service=>service.logo && !failed.has(service.logo)?<img src={service.logo} alt="" loading="lazy" decoding="async" onError={()=>setFailed(previous=>new Set([...previous,service.logo]))}/>:<span className="provider-monogram">{service.name}</span>;
  const matches=(providers || []).filter(provider=>provider.name.toLowerCase().includes(search.trim().toLowerCase()));
  return <section aria-label="Streaming services" className="discovery-services">
    <div className="discover-provider-row no-scrollbar">
      <Button variant="outlined" className="discovery-service-picker" aria-label="All services" aria-haspopup="dialog" aria-expanded={open} onClick={()=>setOpen(true)}><span>{selected?.name || (value?'Selected service':'All services')}</span><ChevronDown size={14}/></Button>
      {services.filter(service=>service.available).map(service=>{const active=providerIsSelected(value,selected,service);return <button key={service.id} className="provider-choice" aria-label={service.name} title={service.name} aria-pressed={active} data-active={active} disabled={loading} onClick={()=>onChange(active?'':service.id)}>{art(service)}{active && <span className="provider-selected"><Check size={10}/></span>}</button>;})}
    </div>
    {error && <p role="alert" className="discovery-help">{error} <Button variant="subtle" onClick={onRetry}>Retry services</Button></p>}
    {loading && <p role="status" className="discovery-help">Loading services…</p>}
    <Sheet open={open} onOpenChange={setOpen}><SheetContent side="bottom" className="discovery-sheet" data-testid="discovery-services-sheet">
      <SheetHeader className="discovery-sheet-header"><SheetTitle className="font-dot">Streaming services</SheetTitle><SheetDescription>Availability follows your saved region.</SheetDescription></SheetHeader>
      <div className="discovery-sheet-body"><input className="n-input" aria-label="Search streaming services" placeholder="Find a service" type="search" value={search} onChange={e=>setSearch(e.target.value)}/><div className="discovery-service-list">
        <button className="discovery-service-option" aria-pressed={!value} onClick={()=>choose('')}>All services{!value && <Check size={16}/>}</button>
        {matches.map(provider=>{const active=value===`provider:${provider.id}` || selected?.providerIds?.split('|').includes(String(provider.id));return <button key={provider.id} className="discovery-service-option" aria-pressed={Boolean(active)} onClick={()=>choose(`provider:${provider.id}`)}><span className="discovery-service-art">{art(provider)}</span><span>{provider.name}</span>{active && <Check size={16}/>}</button>;})}
        {!loading && !matches.length && <p role="status" className="discovery-help">{error?'Services could not be loaded. Close this sheet and retry.':providers?.length?'No matching services. Try another name.':'No services available for this region and media type.'}</p>}
      </div></div>
    </SheetContent></Sheet>
  </section>;
}
