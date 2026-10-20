# Security and privacy

## Android networking

Android network-security XML does not support CIDR ranges for arbitrary private servers. The base policy therefore permits cleartext HTTP so users can configure any LAN address rather than only a hardcoded 192.168.0.5. Explicit public provider domains disable cleartext. Trust anchors use system certificates only; there is no trust-all certificate handler or SSL-error bypass.

The application's provider URL validator additionally restricts HTTP to RFC1918 IPv4, link-local IPv4, local IPv6 and local names/suffixes. Public addresses require HTTPS. Local-name recognition is a heuristic, not DNS-based proof that a host resolves privately. A fully qualified LAN hostname with another suffix should use HTTPS or its private IP. The Android base policy is necessarily broader than this JavaScript validator; review any future native network features so they do not bypass application URL validation.

HTTP exposes credentials, metadata and traffic to devices that can observe the local network. Use it only on a trusted LAN and prefer HTTPS. HTTPS servers need certificates accepted by Android; self-signed or untrusted certificates are not silently accepted. Mixed-content WebView loading is disabled. LAN artwork is fetched natively with an auth header and displayed as data/blob URLs, rather than exposing tokens in img URLs.

Native requests disable automatic redirects. The transport follows a bounded, validated redirect chain and refuses cross-origin forwarding of authorization/query credentials, HTTPS downgrades and unsafe mutation redirects. TorBox torrent-file upload uses one fixed HTTPS provider endpoint and also disables redirects. Native/browser transport capabilities differ; physical device and reverse-proxy checks are still required.

## Credential persistence

On Android, DMMVault uses Android Keystore AES-GCM with a fresh IV and storage-key associated data. TorBox/TMDb/OMDb keys and local provider records, including Jellyfin credentials and indexer settings, use the native vault. JS credential mirrors exist only in memory. Legacy plaintext copies are removed only after an encrypted write succeeds. Account namespaces remain compatible with V13's SHA-256 prefix and do not use raw account keys as persistence identifiers. The insecure shared 'device' fallback is no longer used when a hashing primitive is unavailable.

Android backup and device-transfer copying of app preferences/databases is disabled to avoid moving encrypted data without the original Keystore key. Users can export a credential-free local backup explicitly. Do not uninstall/clear app storage when diagnosing a connection failure: that deletes local data and Keystore entries. The repair itself does not perform those actions.

Native encryption and migration code was inspected but not executed on an Android device. Native compilation status is recorded in TEST_RESULTS.md and the actual CI run. Read TEST_RESULTS.md. The old exceptional 'device' namespace is preserved in legacy storage but is not automatically assigned to a new authenticated profile because ownership cannot be established safely.

Browser previews use browser local storage/Capacitor Preferences, which are not a secrets vault. Encryption at rest cannot protect decrypted credentials from compromised app code or a compromised device. CSP disallows unrelated scripts and embedded objects, remote analytics scripts were removed, native logging is disabled, and render errors log only error names and a bounded component stack. No real credentials were supplied or added to this project.

## Provider/API limitations

Direct access requires user-owned keys and provider permissions/limits. No proxy backend hides these keys from the application runtime. TorBox requestdl, TMDb v3 and Torznab contracts can require query credentials; these are sent only to the chosen/provider HTTPS or trusted LAN service, not placed in artwork URLs or logged. Browser opening of generated download links exposes that link to the destination/browser; signed links are access capabilities and should not be published.

Jellyfin username/password authentication uses the official AuthenticateByName endpoint; passwords are not saved. Administrative API keys can have broad privileges and should be avoided when ordinary user authentication suffices. Use minimal privileges for indexers and provider accounts.

Dependency vulnerability auditing could not run without installation/network access. CRA/react-scripts is an older toolchain, so install, audit and resolve supported dependency advisories before a production release; no claim of a clean npm security audit is made.


## Android 14.3 trailer policy

The current user request enables in-app YouTube trailers. CSP permits frames only from https://www.youtube-nocookie.com and https://www.youtube.com, while app scripts remain self-only. Only validated eleven-character YouTube video IDs form embed URLs. Player messages must match the current iframe window and an official HTTPS YouTube origin. Loading timeouts, provider restrictions (including WebView identity/referrer error 153), retry, fullscreen where supported and a validated external fallback avoid pretending playback succeeded. This does not restore a general media player. No provider restrictions are bypassed, and live Android playback has not been tested.
