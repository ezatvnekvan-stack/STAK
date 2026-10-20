import React from 'react';

/** Keep a failed screen from trapping the Android WebView on a black page. */
export default class AppErrorBoundary extends React.Component {
  state = { error: null };
  static getDerivedStateFromError(error) { return { error }; }
  componentDidCatch(error, info) {
    // Never log credentials, request headers or full remote API responses.
    const reactCode=String(error?.message || '').match(/^Minified React error #(\d+)/)?.[1];
    const reason=/Maximum call stack size exceeded/.test(error?.message || '')?'stack-overflow':/Invalid time value/.test(error?.message || '')?'invalid-date':/locale/i.test(error?.message || '')?'locale':'';
    console.error('STAK UI render failed', error?.name || 'Error', reactCode?`React ${reactCode}`:reason, info?.componentStack?.slice(0, 800));
  }
  render() {
    if (!this.state.error) return this.props.children;
    return <main className="stak-recovery" role="alert">
      <div className="font-dot text-3xl">STAK</div>
      <h1 className="font-dot mt-6 text-2xl">Screen unavailable</h1><p className="mt-3 text-sm text-neutral-400">Your account data has not been cleared.</p>

      <button onClick={() => this.setState({error:null})} className="n-btn-primary mt-5">Try again</button>
      <button onClick={() => window.location.assign('/account')} className="n-btn-ghost ml-3">Settings</button>
    </main>;
  }
}
