# Liquid glass and scrolling

Continues the glass preview with an app-wide glass treatment and a lighter scroll path.

- The dock samples the ambient background at quarter resolution. Scrolling lists are outside its capture layer, so every fling no longer records the whole navigation tree into an offscreen buffer.
- Poster grids and rails use ordinary lazy layout instead of global shared-element lookahead. Route fade/slide transitions and layer-only press feedback remain. Poster image crossfades are removed, and repeating row data is remembered with explicit recycling content types.
- Buttons, icon buttons, tabs, source badges, fields, switches, menus, dialogs and panels share cached translucent fills, specular highlights and glass rims. Red selection/accent states remain. Controls do not each run a backdrop blur.
- Details uses a small artwork texture blurred once off the UI thread and retained by Coil. It stays fixed behind the scrolling content, with contrast gradients and a restrained red glow. The title header uses a gradient instead of an offscreen alpha mask. Modal sheets use translucent glass rather than requesting another fullscreen compositor blur.
- Credential reads reuse a bounded, process-local decrypted snapshot while the exact scoped storage key and encrypted value remain unchanged. Writes clear snapshots; missing/changed ciphertext invalidates them, and decryption errors remain retryable. This removes repeated Android Keystore work while binding Jellyfin artwork and settings rows. Encrypted persistent storage and account isolation remain required.

Regression tests cover blur color/alpha/edge handling and credential snapshot invalidation, account separation and retry behavior. Existing account, Jellyfin, source, navigation and reduced-motion checks still apply. CI retains real Window frame diagnostics for debug and optimized builds; these are not a physical-device 90 Hz certification. Live providers, physical-device performance and full feature/accessibility/edge-state parity still need verification.
