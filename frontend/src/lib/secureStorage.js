import { Capacitor, registerPlugin } from '@capacitor/core';
import { Preferences } from '@capacitor/preferences';
const Vault = registerPlugin('DMMVault');
const native = () => Capacitor.isNativePlatform();
export const SECRET_KEYS = ['tb_api_key', 'tmdb_api_key', 'omdb_api_key'];
const secrets = new Map();
let pending = Promise.resolve();
export function readSecret(key, fallback) { return secrets.get(key) ?? fallback; }
export function writeSecret(key, value) {
  const save = pending.then(async () => {
    if (value == null) await Vault.remove({ key: `credential.${key}` });
    else await Vault.set({ key: `credential.${key}`, value: String(value) });
    secrets.set(key, value == null ? '' : String(value));
  });
  pending = save.catch(() => { window.dispatchEvent(new Event('dmm-storage-error')); });
  return save;
}
export async function hydrateSecrets() {
  if (!native()) return;
  for (const key of SECRET_KEYS) {
    const { value } = await Vault.get({ key: `credential.${key}` });
    let legacy = null;
    try { legacy = window.localStorage.getItem(key); } catch { /* denied storage */ }
    if (value != null) secrets.set(key, value);
    else if (legacy != null) { await Vault.set({ key: `credential.${key}`, value: legacy }); secrets.set(key, legacy); }
    // Only remove a plaintext copy after the encrypted write has succeeded.
    if (legacy != null) { try { window.localStorage.removeItem(key); } catch { /* retain on failure */ } }
  }
}
export async function saveCredential(key, value) {
  if (native()) return writeSecret(key, value);
  try { if (value == null) window.localStorage.removeItem(key); else window.localStorage.setItem(key, String(value)); }
  catch { throw new Error('Storage is unavailable. Your connection was not saved.'); }
}
// Encrypt provider settings and user state on Android. Browser preview uses Preferences.
export const LocalPreferences = {
  async get({ key }) {
    if (!native()) return Preferences.get({ key });
    const saved = await Vault.get({ key });
    if (saved.value != null) return saved;
    const legacy = await Preferences.get({ key });
    if (legacy.value != null) { await Vault.set({ key, value: legacy.value }); await Preferences.remove({ key }); }
    return legacy;
  },
  async set({ key, value }) {
    if (!native()) return Preferences.set({ key, value });
    return Vault.set({ key, value });
  },
  async remove({ key }) {
    if (!native()) return Preferences.remove({ key });
    await Vault.remove({ key });
    await Preferences.remove({ key });
  }
};

// Scoped provider secrets use Android Keystore. Browser previews keep these only
// in memory, never plaintext localStorage or the public metadata database.
const scopedSecrets=new Map();
export async function getScopedCredential(scope,provider){const key=`metadata.credential.${scope}.${provider}`;if(!native())return scopedSecrets.has(key)?scopedSecrets.get(key):null;return (await Vault.get({key})).value ?? null;}
export async function saveScopedCredential(scope,provider,value){const key=`metadata.credential.${scope}.${provider}`;if(native())await Vault.set({key,value:String(value)});else scopedSecrets.set(key,String(value));}
