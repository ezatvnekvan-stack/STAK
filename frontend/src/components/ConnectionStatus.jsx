import {useState} from 'react';
import {Link} from 'react-router-dom';
import {ChevronRight,RefreshCw} from 'lucide-react';
import {useConnections} from '../lib/useConnections';
import {useApp} from '../context/AppContext';
import {api} from '../lib/api';
import {accountRequest} from '../lib/accountQueries';

export default function ConnectionStatus() {
 const {st,busy,check}=useConnections(),{session}=useApp();
 const [testing,setTesting]=useState(false),[verified,setVerified]=useState(null);
 const test=async()=>{setTesting(true);setVerified(null);try{await check();if(st.jf==='ok'){const {data}=await api.get('/jellyfin/diagnostics',accountRequest(session,{timeout:15000}));setVerified(Boolean(data.connected && data.checks?.length && data.checks.every(item=>item.ok)));}}catch{setVerified(false);}finally{setTesting(false);}};
 const rows=[
  {label:'TorBox',section:'account',state:st.tb==='ok'?'ok':st.tb==='wait'?'wait':'off',detail:st.tb==='ok'?'Verified':st.tb==='wait'?'Checking…':'Not verified'},
  {label:'Jellyfin',section:'jellyfin',state:verified===false?'bad':verified?'ok':st.jf==='ok'?'configured':st.jf==='wait'?'wait':'off',detail:verified===false?'Unavailable':verified?'Verified':st.jf==='ok'?'Configured':st.jf==='wait'?'Checking…':'Not configured'},
  {label:'Indexers',section:'sources',state:st.ix==='ok'?'configured':st.ix==='wait'?'wait':'off',detail:st.ix==='ok'?`${st.ixCount} configured`:st.ix==='wait'?'Checking…':'Not configured'}
 ];
 return <section className="settings-connections" data-testid="connection-status" aria-label="Connections">
  <header><h2 className="n-label">Connections</h2><button type="button" onClick={test} disabled={busy || testing} aria-label="Test connections" className="n-icon-btn"><RefreshCw size={15} className={busy || testing?'animate-spin':''}/></button></header>
  {rows.map(row=><Link key={row.section} to={`/account?section=${row.section}`} className="connection-row"><span className="connection-dot" data-state={row.state}/><span>{row.label}</span><span className="connection-detail">{row.detail}</span><ChevronRight size={15}/></Link>)}
  {verified===false && <p role="status" className="settings-connection-error">Jellyfin could not be verified. Open its settings to retry.</p>}
 </section>;
}
