# Permanent Android signing

The public Native 0.2 alpha used an ephemeral CI debug certificate. Its private key is unavailable. A new permanent certificate **cannot update that installation in place**. Export data, save the backup password and keep the old installation until the backup has been checked. Re-enter the same TorBox key and restore after a fresh Native installation. Original STAK has a separate application ID and is unaffected.

## Owner setup (one time)

Generate the permanent key **on your own trusted computer**, not in a disposable CI runner:

```sh
keytool -genkeypair -v -keystore stak-native-release.jks -alias stak-native \
  -keyalg RSA -keysize 4096 -validity 10000
```

Use interactive password prompts. Keep two secure offline copies of the keystore and its passwords. Losing the key prevents updates for this package. Do not commit it, attach it to a release, or put it in a public Actions artifact.

In GitHub repository Settings → Environments, create `android-release`, limit permitted branches and add required reviewers where available. Add these **environment secrets**:

- `STAK_SIGNING_KEYSTORE_BASE64`: base64 encoding of the keystore (a transfer encoding, not encryption).
- `STAK_SIGNING_STORE_PASSWORD`: keystore password.
- `STAK_SIGNING_KEY_ALIAS`: `stak-native`.
- `STAK_SIGNING_KEY_PASSWORD`: private-key password.

The `Native signed candidate` Actions workflow builds a signed candidate and its certificate/hash manifest. It does not publish automatically. Retain the SHA-256 signing certificate fingerprint from the first permanent build; compare it before every later release. Download and safely check the encrypted backup before replacing an existing installation. Full provider coverage and real-phone performance still need validation before stable release.

The current workspace integration cannot create repository secrets; the repository owner must perform that setup. No permanent key has been generated or uploaded on the owner's behalf.

## Local release build

Set `STAK_SIGNING_STORE_FILE` to the absolute keystore path and the other three `STAK_SIGNING_*` values in a private shell/session, then run `./gradlew assembleRelease`. Do not paste secrets into terminal history, chat or command arguments. Release packaging fails if signing is absent. Debug builds remain available for development and are not distribution builds.

## Update continuity check

After two permanent-key candidates exist, verify both with `apksigner verify --print-certs`. Their certificate SHA-256 digests must match. On a test device install the older candidate, add a test watchlist/episode, then install the newer one with `adb install -r`; verify the data survives. This cannot be proven using the ephemeral 0.2 signature.
