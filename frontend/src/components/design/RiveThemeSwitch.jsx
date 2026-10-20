// MIT-licensed Rive runtime + bundled upstream switch example, credited in
// THIRD_PARTY_NOTICES.md. No CDN, remote code or exported credentials.
import { useEffect, useRef, useState } from 'react';
import { RuntimeLoader, useRive } from '@rive-app/react-canvas';

RuntimeLoader.setWasmUrl(`${process.env.PUBLIC_URL || ''}/animations/rive.wasm`);
RuntimeLoader.setWasmFallbackUrl(`${process.env.PUBLIC_URL || ''}/animations/rive_fallback.wasm`);
const MACHINE = 'Main State Machine';

export default function RiveThemeSwitch({ dark }) {
  const [ready, setReady] = useState(false), [failed, setFailed] = useState(false);
  const lastDark = useRef(false);
  const { rive, RiveComponent } = useRive({ src: `${process.env.PUBLIC_URL || ''}/animations/theme-switch.riv`, stateMachines: MACHINE, autoplay: false, shouldDisableRiveListeners: true, onLoad: () => setReady(true), onLoadError: () => setFailed(true) });
  useEffect(() => {
    if (!rive || !ready || failed) return undefined;
    // The authored state machine starts in day mode. Toggle only when the real
    // app theme changes; resetting the runtime would discard its input state.
    const input = rive.stateMachineInputs(MACHINE)?.find(item => item.name === 'Click');
    if (!input || typeof input.fire !== 'function') { setFailed(true); return undefined; }
    rive.play(MACHINE);
    if (dark !== lastDark.current) { input.fire(); lastDark.current = dark; }
    // This illustration is event-driven, never a permanent animation loop.
    const timer = setTimeout(() => rive.pause(), 1400);
    return () => { clearTimeout(timer); if (rive.loaded) rive.pause(); };
  }, [rive, ready, failed, dark]);
  if (failed) return <span data-rive-status="failed" className="sr-only">Static theme preview</span>;
  return <span className="rive-theme-art" data-rive-status={ready ? 'ready' : 'loading'} aria-hidden="true" style={{ opacity: ready ? 1 : 0 }}><RiveComponent/></span>;
}
