import {useT} from '../lib/i18n';
export default function MetadataNotice({notices=[]}){const t=useT();return <>{notices.length>0 && <div role="status" className="mb-3 space-y-1 text-xs text-neutral-400">{[...new Set(notices)].map(message=><p key={message}>{t(message)}</p>)}</div>}</>;}
