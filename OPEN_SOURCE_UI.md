# Open-source projects used in the STAK visual refresh

Baseline: the verified 2.0.2-derived `stak-discover-202` branch, including the fixes saved in `ddb3c5d884c0374fd11cd6d30040347312971937`. No 3.x/4.x source is imported. This is a record of source integration, not a claim that build/device validation has completed.

| Project | Actual role | STAK source | Integration |
|---|---|---|---|
| [shadcn/ui](https://github.com/shadcn-ui/ui), [Radix Primitives](https://github.com/radix-ui/primitives) | Existing accessible controls, focus-trapped dialogs, menus, switches and sheets. Shared sizes, surfaces, borders and typography are redesigned. | `components/ui/`, `StakUI.jsx`, `styles/design.css` | Existing foundation retained; no second component runtime installed. |
| [Intent UI](https://github.com/irsyadadl/intentui) | Layout reference for compact collection toolbars, grouped field rows and a settings destination grid. | `CollectionControls.jsx`, `SettingsGroup.jsx`, `styles/design.css` | Design reference and local adaptation; Intent UI/React Aria is not installed or claimed as a runtime dependency. |
| [Motion Primitives](https://github.com/ibelick/motion-primitives) | AnimatedBackground/TransitionPanel patterns adapted to controlled selection and route/panel transitions. Caller semantics, keyboard handling and event handlers are preserved. | `components/design/MotionSystem.jsx`, `lib/motionPolicy.js`, `AppShell.jsx`, `TrackingPage.jsx`, `StakUI.jsx`, `LibrarySwitch.jsx`, `EpisodePicker.jsx`, `DiscoverPage.jsx` | Adapted source using the already installed `framer-motion` 11.18.0, with added app/system reduced-motion handling. No Next.js or Tailwind 4 migration. |
| [Magic UI](https://github.com/magicuidesign/magicui) | NumberTicker pattern adapted for actual Activity counts and estimated runtime updates. Exact values are available immediately; no invented zero-to-success statistics. | `components/design/AnimatedNumber.jsx`, `pages/HistoryPage.jsx` | Adapted source; animates updates only while visible, with an exact accessible label and reduced-motion fallback. |
| [Embla](https://github.com/davidjerleke/embla-carousel) | Gesture-driven featured titles and media/artwork rails. Only the selected hero mounts metadata queries; autoplay pauses for focused controls, reduced motion and hidden documents. | `MediaHero.jsx`, `components/design/GestureRail.jsx`, `HomeRow.jsx`, `DetailExtras.jsx` | Existing pinned `embla-carousel-react` 8.6.0 reused. |
| [Rive React](https://github.com/rive-app/rive-react), [Rive runtime](https://github.com/rive-app/rive-wasm) | A small day/night illustration attached to a real theme-switch button in Appearance. | `ThemeMotionPreview.jsx`, `RiveThemeSwitch.jsx`, `public/animations/theme-switch.riv`, `scripts/prepare-ui-assets.cjs` | `@rive-app/react-canvas` pinned to 4.37.0 (React 18 supported). Lazy loaded only on the Appearance page with motion enabled. Primary and fallback WASM bundled locally. Canvas pauses after 1.4 seconds and unmounts when the app is inactive or reduced motion is enabled. |

All listed code projects use MIT licenses at the reviewed sources. Full retained notices are in `THIRD_PARTY_NOTICES.md` and copied into the standalone web/APK assets during build. Rive's editor is separate; no editor account or paid asset is required for this bundled example.

## Rive asset provenance

- Repository: `rive-app/rive-wasm`.
- Reviewed source commit: `8e3484b8d90a73bf51c8ff505d6977e547703177`.
- Original path: `js/examples/_frameworks/parcel_example_canvas/switch_event_example.riv`.
- Original Git blob: `3e3422bbf1249a4bb8bc5368e9012b13b70465d8`.
- Bundled path: `frontend/public/animations/theme-switch.riv`.
- Size: 9,218 bytes.
- SHA-256: `9b9c68b6319e5a186f99bb94ba5b3001f8a272cc3a4fd6a688e67b8bdf7310b4`.
- Source license: repository MIT notice, retained in `THIRD_PARTY_NOTICES.md`.
- State machine: `Main State Machine`, trigger `Click`. The example is rendered in monochrome and mirrors the actual selected app theme; it is not a STAK-authored brand animation.

## Preserved user fixes

Continue Watching heading; episode metadata limited to season/episode numbers; tight title/year spacing; matching tracking-count/percentage styling; larger three-across episode artwork; labels on every taskbar item; no poster overflow-dot buttons; long-press and keyboard actions retained; stronger media-derived Details colors. Existing watched-state reconciliation, explicit unwatch/pending edits, data formats, credentials, providers and Android package identity are retained.

## Validation and delivery

React regressions and production-browser checks must verify the integrations. New source checks are not a substitute for those runs. CI builds Android versionCode **38**, versionName **1.0.0**, application ID `app.dmmstudio.cinema`, using the existing signing configuration. The historical published release is preserved; development validation produces a separate APK artifact. Signing compatibility remains subject to a matching installed certificate; no uninstall or data-clearing workaround is proposed.
