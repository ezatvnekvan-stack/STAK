# Liquid glass and scrolling

Continues the glass preview with an app-wide glass treatment and a lighter scroll path.

- The dock uses cached frosted paint with translucent highlights and rims. Live backdrop capture and the Haze dependency are removed, so flings need no glass capture or blur render target. Details retains actual blurred artwork through its cached image transformation.
- Ambient glow modulates paint alpha directly, avoiding a fullscreen opacity buffer beneath scrolling content.
- Android 15/16 receives both window and view-level refresh hints, using a supported display rate. Both hints are released when the activity pauses. The user's current device is a Nothing Phone (3a) on Android 16; sustained device performance still needs measurement.
- Poster grids and rails use ordinary lazy layout instead of global shared-element lookahead. Route fade/slide transitions and layer-only press feedback remain. Poster image crossfades are removed, and repeating row data is remembered with explicit recycling content types.
- Buttons, icon buttons, tabs, source badges, fields, switches, menus, dialogs and panels share cached translucent fills, specular highlights and glass rims. Red selection/accent states remain. Controls do not each run a backdrop blur.
- Details uses a small artwork texture blurred once off the UI thread and retained by Coil. It stays fixed behind the scrolling content, with contrast gradients and a restrained red glow. The title header uses a gradient instead of an offscreen alpha mask. Modal sheets use translucent glass rather than requesting another fullscreen compositor blur.
- Credential reads reuse a bounded, process-local decrypted snapshot while the exact scoped storage key and encrypted value remain unchanged. Writes clear snapshots; missing/changed ciphertext invalidates them, and decryption errors remain retryable. This removes repeated Android Keystore work while binding Jellyfin artwork and settings rows. Encrypted persistent storage and account isolation remain required.

Regression tests cover blur color/alpha/edge handling and credential snapshot invalidation, account separation and retry behavior. Existing account, Jellyfin, source, navigation and reduced-motion checks still apply. CI runs both APK variants on Android 16 (API 36), retaining real Window frame diagnostics; these are not a physical-device 90 Hz certification. Project compilation remains on SDK/build-tools 35. Live providers, physical-device performance and full feature/accessibility/edge-state parity still need verification.
