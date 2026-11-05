# Optional services

| Directory | Role |
| --- | --- |
| `stak-server` | Current unified Auto-add and organized-library service; one Docker container |
| `stak-cloud` | Username/password accounts and encrypted backup deployment source |

Jellyfin and Zurg remain external services. The old standalone `stak-watch` and `stak-library-filter` source copies are retired. Existing installations can migrate using `stak-server/migrate.py`; that migration reads their existing containers and data, not retired source directories.

Keep completed `.env`, `torznab.json`, database files, data directories and migration state private. Do not blindly run migration or schema setup against an already configured owner installation. Read each service's README first.
