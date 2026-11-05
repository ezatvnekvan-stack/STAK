# Optional STAK account service

This is optional managed Supabase storage, not a daemon that must run on a user's phone or media server. The product UI uses only username/password; no Google provider, real email, phone, recovery code or confirmation flow is exposed. Ordinary local STAK use remains available without an account.

## Provisioning

1. Create a dedicated Supabase project in a suitable region. Choose the plan/retention policy appropriate for the deployment; do not promise indefinite availability or storage on a free tier.
2. In Auth settings enable password/email authentication and sign-up, **disable email confirmation**, and set a minimum password length of 8. The app checks `mailer_autoconfirm` before registration so a misconfigured service cannot pretend that a user is signed in. Do not configure OAuth or email recovery for this flow.
3. Run `001_backups.sql` in the SQL editor. The two custom RPCs are `stak_backup_read` and `stak_backup_write`. The private tables are not exposed/granted to anonymous clients; every authenticated call is scoped exclusively by the verified `auth.uid()`, never by a caller-supplied user ID.
4. Provide the project's HTTPS URL as `STAK_CLOUD_URL` and its **publishable** client key as `STAK_CLOUD_PUBLIC_KEY` when building the Android APK. These are public project connection details, not a database password or admin key. The build rejects secret/service keys. No keys are committed here.
5. Build with the existing official signing setup. Verify registration, sign-in, refresh, backup and restore using disposable accounts before enabling a public release. Without configuration the account UI explicitly reports that the service is not ready; it does not simulate authentication or protection.

Username normalization is NFKC + lowercase with 3–32 ASCII letters/digits/dot/underscore/hyphen. Auth internally receives a deterministic SHA-256-based address under the reserved `.invalid` domain. It is an internal identifier, **not a mailbox**, and no email is requested or sent. Supabase Auth continues to handle password hashing, credential checks, JWT issuance, refresh and its rate limiting; STAK does not implement its own password database.

## Data protection

The device exports logical settings, decrypted credential values in memory, watchlist, tracking rows and Room preference documents. Android master keys, device IDs, cloud tokens, restore journals, image caches and database files are never uploaded. The portable snapshot is compressed and encrypted on-device using AES-256-GCM with a PBKDF2-HMAC-SHA256 key derived from the user's password and account-unique salt (600,000 iterations). The local encryption key and Auth session are stored only in Android encrypted storage. TLS protects authentication requests; neither passwords nor raw settings are logged.

Read authenticates and decrypts before restoring. Write uses optimistic revision checking and idempotency IDs; stale devices cannot overwrite a newer backup. Five encrypted versions are retained server-side. A failed/unknown upload is reconciled by reading the current encrypted backup before another save attempt. No local data is marked protected until a real acknowledgement is received. Account switching cannot upload a previous account's dataset into an empty different account.

Restore validates the complete payload, records an encrypted local rollback journal and uses a Room transaction plus checked settings writes. A crash recovers the previous state before normal app models/workers run. A prior local checkpoint is retained encrypted on-device. Restoring cloud state never replays legacy watched writes or requests media additions/deletions.

Changes are coalesced in the foreground and retried through constrained WorkManager jobs. Android may defer background work; only the last successfully saved snapshot is recoverable after uninstall. The UI shows that actual save time. Multi-device concurrent edits stop at a conflict rather than guessing a destructive merge.

There is deliberately no password recovery/change UI. Losing the original password prevents decryption; an administrator resetting the Auth password cannot decrypt older snapshots. Do not externally change Auth passwords while claiming old backups will remain recoverable.

## References

- https://supabase.com/docs/guides/auth/passwords
- https://github.com/supabase/auth/blob/master/openapi.yaml
- https://supabase.com/docs/guides/getting-started/api-keys
- https://supabase.com/docs/guides/database/functions
- https://supabase.com/docs/guides/database/postgres/row-level-security

`tests/` contains isolated PostgreSQL contract tests. Local fixtures are not a deployed cloud service or evidence that any user's data is already backed up.
