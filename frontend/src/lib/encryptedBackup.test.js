/** @jest-environment node */
import { webcrypto } from 'crypto';
import { encryptBackup, decryptBackup } from './encryptedBackup';

test('encrypted backup round-trip preserves credentials and wrong passphrase fails cleanly', async () => {
  const previous = Object.getOwnPropertyDescriptor(global, 'crypto');
  Object.defineProperty(global, 'crypto', { configurable: true, value: webcrypto });
  try {
    const value = { watchlist: [{ imdb_id: 'tt0133093' }], credentials: { torbox: 'test-only-secret' } };
    const encrypted = await encryptBackup(value, 'test-only-passphrase');
    expect(Object.keys(encrypted)).toEqual(['v', 'salt', 'iv', 'data']);
    expect(atob(encrypted.salt)).toHaveLength(16);
    expect(atob(encrypted.iv)).toHaveLength(12);
    expect(JSON.stringify(encrypted)).not.toContain('test-only-secret');
    expect(await decryptBackup(JSON.parse(JSON.stringify(encrypted)), 'test-only-passphrase')).toEqual(value);
    await expect(decryptBackup(encrypted, 'wrong-passphrase')).rejects.toThrow('Could not decrypt backup');
  } finally {
    if (previous) Object.defineProperty(global, 'crypto', previous);
    else delete global.crypto;
  }
});
