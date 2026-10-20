import { LocalPreferences as Preferences } from './secureStorage';
import { accountScope } from './accountScope';
const prefix = 'dmm.local.v1.';
const parse = value => { try { const cfg = JSON.parse(value); return cfg?.url && cfg?.api_key && cfg?.user_id ? cfg : null; } catch { return null; } };
// One device-level Jellyfin connection is independent of TorBox key rotation.
// Legacy records remain intact; migrate only an already selected connection.
export async function readJellyfinConnection(account = 'guest') {
  const saved = await Preferences.get({ key: `${prefix}jellyfin.connection` });
  const current = parse(saved.value);
  if (current) return current;
  const scope = await accountScope(account);
  const active = await Preferences.get({ key: `${prefix}jellyfin.active` });
  for (const candidate of [...new Set([scope, active.value].filter(Boolean))]) {
    const legacy = parse((await Preferences.get({ key: `${prefix}${candidate}.jellyfin` })).value);
    if (legacy) { await saveJellyfinConnection(legacy); return legacy; }
  }
  return null;
}
export const saveJellyfinConnection = cfg => Preferences.set({ key: `${prefix}jellyfin.connection`, value: JSON.stringify(cfg) });
export async function removeJellyfinConnection(account = 'guest') {
  const scope = await accountScope(account);
  const active = await Preferences.get({ key: `${prefix}jellyfin.active` });
  const selected = parse((await Preferences.get({ key: `${prefix}jellyfin.connection` })).value);
  await Preferences.remove({ key: `${prefix}jellyfin.connection` });
  // Remove the explicitly disconnected credential, so it cannot auto-migrate back.
  for (const candidate of [...new Set([scope, active.value].filter(Boolean))]) {
    const legacyKey = `${prefix}${candidate}.jellyfin`;
    const legacy = parse((await Preferences.get({ key: legacyKey })).value);
    if (!selected || (legacy?.url === selected.url && legacy?.user_id === selected.user_id && legacy?.api_key === selected.api_key)) await Preferences.remove({ key: legacyKey });
  }
  await Preferences.remove({ key: `${prefix}jellyfin.active` });
}
