# STAK native motion

The current direction keeps STAK’s content and familiar structure while allowing a more polished Nothing-inspired native interface. Doto headings, Space Mono labels, restrained monochrome surfaces, the selected accent and original Rive artwork anchor the visual style.

| Interaction | Native behavior |
| --- | --- |
| Poster → title → Back | The selected poster changes bounds over 360 ms into the title hero; Back reverses it. Keys include the shelf so repeated titles do not share the wrong element. |
| Poster press | 3% compression with a damped spring; long press opens actions with optional haptic feedback. |
| Navigation | Selection glides on a spring; icon/label ink changes over 180 ms. The pressed icon compresses and releases with a small spring. |
| Screen change | Incoming content fades and lifts by 1.2% over 280 ms; outgoing content fades over 110 ms. |
| Buttons and icon controls | Buttons compress 2% over 120 ms. Circular icon controls compress 8% with a short spring. Optional Android haptics accompany taps. |
| Switches | The thumb moves on a short spring, with coordinated track and thumb colour changes. |
| Segmented controls | Selection slides with the same navigation spring and labels change ink together. |
| Hero pagination | The active dot grows into a short capsule over 240 ms, with a coordinated opacity change. |
| Text fields | The border changes to the selected accent over 180 ms on focus. |
| Artwork loading | Loaded artwork fades in over 180 ms. It does not add an artificial loading delay. |
| Sheets | Native draggable sheets with a dot grip; a static dialog equivalent under reduced motion. |
| Status / progress | Request-driven loading dots, segmented progress fill and a drawn watched check. Progress reflects real state. |
| Theme illustration | Original bundled Rive state machine rendered natively; it settles and pauses rather than looping continuously. |

App or Android system reduced motion disables moving transitions, presses and artwork fades, snaps control state changes, and uses the static theme icon. Lifecycle-aware ambient/status animation and remote polling pause outside the resumed app state.

`NativeMotionTest` retains a fixture-only emulator MP4 covering poster navigation, switches, tabs and reduced motion. Its frame clock advances at a visible cadence for review. It is not a device performance or frame-rate benchmark. Original/native screenshots at matching viewport sizes provide layout references; they do not certify pixel equality or live-provider behavior.
