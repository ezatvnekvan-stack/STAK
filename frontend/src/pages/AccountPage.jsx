import { saveCredential } from "../lib/secureStorage";
import { KEY_STORAGE } from "../lib/api";
import { useState } from "react";
import {useSearchParams} from "react-router-dom";
import { Eye, EyeOff, LogOut, Loader2, ExternalLink } from "lucide-react";
import { toast } from "sonner";
import { useApp } from "../context/AppContext";
import { api, errMsg } from "../lib/api";
import { fmtBytes, fmtDate } from "../lib/format";
import { PageTitle } from "../components/Bits";
import MetadataSources from "../components/MetadataSources";
import AppCard from "../components/AppCard";
import JellyfinSettings from "../components/JellyfinSettings";
import HomeSettings from "../components/HomeSettings";
import BackupSettings from "../components/BackupSettings";
import MediaToolkit from "../components/MediaToolkit";
import NavigationSettings from "../components/NavigationSettings";
import SourcesSettings from "../components/SourcesSettings";
import SettingsGroup from "../components/SettingsGroup";
import ConnectionStatus from "../components/ConnectionStatus";
import AppearanceSettings from "../components/AppearanceSettings";
import { useT } from "../lib/i18n";

const PLANS = { 0: "Free", 1: "Essential", 2: "Pro", 3: "Standard" };

const Stat = ({ label, value, testId }) => (
  <div className="n-card p-4">
    <div className="n-label mb-2">{label}</div>
    <div data-testid={testId} className="break-all font-nmono text-sm text-white">{value}</div>
  </div>
);

export default function AccountPage() {
  const t = useT();
  const [params]=useSearchParams(),section=params.get('section');
  const titles={account:'TorBox account',metadata:'Metadata & ratings',sources:'Search sources',jellyfin:'Jellyfin',appearance:'Appearance',home:'Home & browsing',advanced:'Data & app'};
  const { key, setKey, me, meError } = useApp();
  const [input, setInput] = useState("");
  const [show, setShow] = useState(false);
  const [busy, setBusy] = useState(false);

  const save = async () => {
    const k = input.trim();
    if (!k) return;
    setBusy(true);
    try {
      await api.get("/torbox/me", { headers: { "X-TorBox-Key": k }, timeout: 30_000 });
      await saveCredential(KEY_STORAGE, k);
      setKey(k);
      setInput("");
      toast.success("TorBox connected");
    } catch (e) {
      toast.error(`Invalid key: ${errMsg(e)}`);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="settings-page">
      <PageTitle title={titles[section] || t("Settings")} testId="account-title" />
      <div className="space-y-3">
        {!titles[section] && <ConnectionStatus />}
        <SettingsGroup id="account" title="TorBox account" hint={key ? meError?"Connection needs attention":me?"Account verified":"Key saved · not verified":t("No key")}>
          {key && (
            <div className="space-y-3">
              {meError ? (
                <div data-testid="account-key-error" className="n-card border-[#D71921]/60 p-4 text-sm text-[#ff4d55]">Key error: {meError}</div>
              ) : (
                <div className="grid grid-cols-2 gap-2.5 sm:grid-cols-4">
                  <Stat label="E-mail" value={typeof me?.email === "string" ? me.email : "…"} testId="account-email" />
                  <Stat label="Plan" value={me ? PLANS[me.plan] ?? "Unknown" : "…"} testId="account-plan" />
                  <Stat label="Expires" value={fmtDate(me?.premium_expires_at)} testId="account-expiry" />
                  <Stat label="Downloaded" value={fmtBytes(me?.total_downloaded)} testId="account-downloaded" />
                </div>
              )}
              <div className="n-card flex items-center justify-between p-4">
                <div className="min-w-0">
                  <div className="n-label mb-1">Active key</div>
                  <div data-testid="account-masked-key" className="font-nmono text-sm text-white">{key.slice(0, 4)}••••••••{key.slice(-4)}</div>
                </div>
                <button data-testid="torbox-logout-button" onClick={async () => { try { await saveCredential(KEY_STORAGE, null); setKey(""); toast("Signed out"); } catch (e) { toast.error(errMsg(e)); } }} className="n-btn-destructive"><LogOut size={14} /> {t("Sign out")}</button>
              </div>
            </div>
          )}
          <div className="n-card p-5">
            <div className="n-h2 mb-2">{key ? t("Replace key") : t("Connect")}</div>
            <p className="mb-4 text-sm text-neutral-400">Stored on this device; encrypted with Android Keystore.</p>
            <div className="relative mb-3">
              <input disabled={busy} data-testid="torbox-api-key-input" type={show ? "text" : "password"} value={input} onChange={(e) => setInput(e.target.value)} placeholder="TorBox API key" className="n-input !pr-12 font-nmono" autoComplete="off" />
              <button data-testid="torbox-key-visibility-toggle" onClick={() => setShow(!show)} aria-label="Toggle key visibility" className="n-icon-btn !absolute right-1 top-1/2 -translate-y-1/2 !border-transparent">{show ? <EyeOff size={16} /> : <Eye size={16} />}</button>
            </div>
            <button data-testid="torbox-save-key-button" onClick={save} disabled={busy || !input.trim()} className="n-btn-red w-full">
              {busy && <Loader2 size={14} className="animate-spin" />} {t("Save & verify")}
            </button>
            <a data-testid="torbox-get-key-link" href="https://torbox.app/settings" target="_blank" rel="noreferrer" className="mt-4 inline-flex min-h-[44px] items-center gap-1.5 font-nmono text-[11px] uppercase tracking-widest text-neutral-400 hover:text-white">
              Get a TorBox key <ExternalLink size={12} />
            </a>
          </div>
        </SettingsGroup>
        <SettingsGroup id="metadata" title="Metadata & ratings" hint="TMDb · Cinemeta · OMDb"><MetadataSources/></SettingsGroup>
        <SettingsGroup id="sources" title="Search sources" hint="Torrentio · Torznab · indexers">
          <SourcesSettings />
        </SettingsGroup>
        <SettingsGroup id="jellyfin" title="Jellyfin" hint="Server · playback · sync">
          <JellyfinSettings />
        </SettingsGroup>
        <SettingsGroup id="appearance" title={t("Appearance")} hint="Theme · accent · atmosphere · ratings">
          <AppearanceSettings />
        </SettingsGroup>
        <SettingsGroup id="home" title="Home & browsing" hint="Shelves · navigation · genres">
          <NavigationSettings />
          <HomeSettings />
        </SettingsGroup>
        <SettingsGroup id="advanced" title="Data & app" hint="Backup · tools · about STAK">
          <MediaToolkit />
          <BackupSettings />
          <AppCard />
        </SettingsGroup>
      </div>
    </div>
  );
}
