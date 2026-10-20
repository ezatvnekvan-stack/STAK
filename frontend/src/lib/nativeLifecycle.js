import { useEffect, useRef } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import { Capacitor } from '@capacitor/core';
import { App } from '@capacitor/app';
import { StatusBar, Style } from '@capacitor/status-bar';
import { toast } from 'sonner';
import { useTheme } from './theme';
export function useNativeLifecycle() {
  const { pathname } = useLocation();
  const navigate = useNavigate();
  const client = useQueryClient();
  const { resolved } = useTheme();
  const currentPath = useRef(pathname);
  currentPath.current = pathname;
  useEffect(() => {
    const error = () => toast.error('Storage could not save the change. Retry before closing the app.');
    window.addEventListener('dmm-storage-error', error);
    return () => window.removeEventListener('dmm-storage-error', error);
  }, []);
  useEffect(() => {
    if (!Capacitor.isNativePlatform()) return;
    const handles = [];
    let disposed = false;
    const add = async (name, callback) => { const h = await App.addListener(name, callback); if (disposed) h.remove(); else handles.push(h); };
    add('backButton', ({ canGoBack }) => {
      if (document.fullscreenElement) { document.exitFullscreen().catch(() => {}); return; }
      const menu=document.querySelector('[role="listbox"][data-state="open"]');
      if(menu){menu.dispatchEvent(new KeyboardEvent('keydown',{key:'Escape',code:'Escape',bubbles:true}));return;}
      const dialogs = [...document.querySelectorAll('[role="dialog"],[role="alertdialog"]')];
      const top = dialogs.reverse().find(el => el.dataset.state !== 'closed' && el.getAttribute('aria-hidden') !== 'true');
      if (top) { const close = top.querySelector('button[aria-label^="Close"]'); if (close) close.click(); else top.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', code: 'Escape', bubbles: true })); return; }
      if (document.activeElement?.matches('input,textarea')) { document.activeElement.blur(); return; }
      if (canGoBack && window.history.state?.idx > 0) navigate(-1);
      else if (currentPath.current !== '/') navigate('/', { replace: true });
      else App.minimizeApp();
    }).catch(() => {});
    add('appStateChange', ({ isActive }) => { window.dispatchEvent(new CustomEvent('stak-app-active', { detail: isActive })); if (isActive) { client.invalidateQueries({ queryKey: ['account'] }); client.invalidateQueries({queryKey:['jellyfin-library']}); window.dispatchEvent(new Event('dmm-foreground')); } }).catch(() => {});
    return () => { disposed = true; handles.forEach(h => h.remove()); };
  }, [navigate, client]);
  useEffect(() => {
    if (!Capacitor.isNativePlatform()) return;
    StatusBar.setStyle({ style: resolved === 'light' ? Style.Light : Style.Dark }).catch(() => {});
    StatusBar.setBackgroundColor({ color: resolved === 'light' ? '#f4f4f2' : '#080808' }).catch(() => {});
    StatusBar.setOverlaysWebView({ overlay: false }).catch(() => {});
  }, [resolved]);
}
