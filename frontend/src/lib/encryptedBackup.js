const ITERATIONS = 210000;
const encode = bytes => {
  let value = '';
  for (let i = 0; i < bytes.length; i += 32768) value += String.fromCharCode(...bytes.subarray(i, i + 32768));
  return btoa(value);
};
const decode = value => {
  if (typeof value !== 'string' || value.length > 4 * 1024 * 1024 || !/^[A-Za-z0-9+/]*={0,2}$/.test(value)) throw new Error('Invalid encrypted backup.');
  return Uint8Array.from(atob(value), c => c.charCodeAt(0));
};
async function keyFor(passphrase, salt) {
  if (!globalThis.crypto?.subtle) throw new Error('Encrypted backups require WebCrypto in a secure context.');
  if (typeof passphrase !== 'string' || !passphrase.length) throw new Error('Enter a backup passphrase.');
  const material = await crypto.subtle.importKey('raw', new TextEncoder().encode(passphrase), 'PBKDF2', false, ['deriveKey']);
  return crypto.subtle.deriveKey({ name: 'PBKDF2', hash: 'SHA-256', iterations: ITERATIONS, salt }, material, { name: 'AES-GCM', length: 256 }, false, ['encrypt', 'decrypt']);
}
export async function encryptBackup(value, passphrase) {
  const salt = crypto.getRandomValues(new Uint8Array(16)), iv = crypto.getRandomValues(new Uint8Array(12));
  const key = await keyFor(passphrase, salt);
  const data = await crypto.subtle.encrypt({ name: 'AES-GCM', iv }, key, new TextEncoder().encode(JSON.stringify(value)));
  return { v: 1, salt: encode(salt), iv: encode(iv), data: encode(new Uint8Array(data)) };
}
export async function decryptBackup(value, passphrase) {
  if (value?.v !== 1) throw new Error('Unsupported encrypted backup.');
  const salt = decode(value.salt), iv = decode(value.iv), data = decode(value.data);
  if (salt.length !== 16 || iv.length !== 12 || data.length < 16) throw new Error('Invalid encrypted backup.');
  const key = await keyFor(passphrase, salt);
  try { return JSON.parse(new TextDecoder().decode(await crypto.subtle.decrypt({ name: 'AES-GCM', iv }, key, data))); }
  catch { throw new Error('Could not decrypt backup. Check the passphrase and file.'); }
}
