export async function accountScope(account) {
  if (!account || account === 'guest') return 'guest';
  if (!globalThis.crypto?.subtle) throw new Error('Secure account storage is unavailable. Restart with a current Android System WebView.');
  const digest = await globalThis.crypto.subtle.digest('SHA-256', new TextEncoder().encode(account));
  return Array.from(new Uint8Array(digest)).slice(0, 12).map(x => x.toString(16).padStart(2, '0')).join('');
}
