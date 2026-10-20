import {APP_VERSION} from './appVersion';
// Jellyfin supports MediaBrowser authorization and the legacy token header.
// Use both for server/reverse-proxy compatibility, never query-string tokens.
export function jellyfinHeaders(token, deviceId = 'dmm-studio-android') {
  if (typeof token !== 'string' || !token.trim() || token.length > 4096 || /[\r\n]/.test(token)) throw new Error('Reconnect Jellyfin in Settings: the saved credential is invalid.');
  const quote = value => String(value).replace(/\\/g, '\\\\').replace(/"/g, '\\"');
  const authorization = `MediaBrowser Client="DMM Studio", Device="Android", DeviceId="${quote(deviceId)}", Version="${APP_VERSION}", Token="${quote(token)}"`;
  return { Authorization: authorization, 'X-Emby-Authorization': authorization, 'X-Emby-Token': token };
}

export function jellyfinError(error) {
  const status = error?.response?.status;
  if (status === 401) return 'Jellyfin rejected the saved session. Reconnect in Settings using your username and password, or a valid server API key. Your lists and history are preserved.';
  if (status === 403) return 'This Jellyfin user does not have permission for this action. Check server permissions or choose another user in Settings.';
  return error?.message || 'Jellyfin could not be reached. Check the server address and connection.';
}
