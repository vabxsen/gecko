# Motion and visual polish

This pass preserves Gecko's monochrome style and existing API connection flow.

## Changes

- A one-time animated welcome mark and staggered home content entrance.
- Spring press feedback on starter cards, composer controls, connection selection, and settings rows.
- Focus-responsive composer border and shadow, send-arrow rotation, and accepted-send haptics.
- Short navigation transitions and new-message fades without animating streamed text or message placement.
- Refined starter cards, pill shapes, and subtle depth in both themes.
- Custom motion follows Android's animation setting, including live changes while Gecko is open.
- Shared animated Material buttons cover toolbars, message actions, API key forms, drawer controls, and dialog actions while retaining their click behavior and accessibility.
- Settings and connection pages enter softly; confirmation dialogs use a short spring entrance. Advanced settings expand from the top with a rotating chevron. Theme selection borders animate, and the scroll-to-bottom button springs into view.

## Validation

- Debug and signed release builds passed.
- 38 chat/settings unit tests passed in the initial polish pass; this extension changes only presentation and interaction feedback.
- All 22 emulator tests passed after the app-wide extension: 11 existing chat controls/composer tests, 5 settings tests, and 6 motion tests. The motion tests cover immediate content visibility with motion disabled, live system-setting changes, send press cancellation/single dispatch, streamed text updates with the animation clock paused, and disabled/cancelled/released presses across all three shared button variants.
- Visually inspected light and dark themes, a 320 dp-wide screen with 1.3 font scale, composer focus/keyboard, and disabled animations.
- Rechecked drawer navigation, light/dark theme selection, privacy dialog cancellation, and expanded API-key settings in the running app after the extension.
- No live provider requests were made; UI previews use synthetic emulator data. Physical-device frame performance was not measured.

## Local preview

- APK: `installer/gecko-motion-preview.apk`
- Video: `build/motion-preview/gecko-motion-preview.mp4`
- App-wide video: `build/motion-preview/gecko-overall-motion.mp4`
- Version remains 1.1.0 (10100); this preview has not been published.
- APK SHA-256: `640af7f07716d9ddf12d572e72522b26ca8f5e90910b4038f4138ac1aa4b5c3e`
- Signing certificate SHA-256 matches the current release: `8b0b6a6b72417e1ba28591a87e1304246e606926b4108670f5db7491a92bfd86`

Build and device logs are under `build/motion-*.log` and `build/overall-motion-*.log`. Preview artifacts and logs are ignored by Git.

## Visual refinement follow-up

- Grouped related settings into shared surfaces with inset dividers; retained row-level actions and accessibility.
- Added contrasting starter cards, tighter heading typography, a smaller welcome mark, and circular toolbar controls.
- Reduced composer and card shadows and softened borders in both themes.
- Rechecked light/dark layouts and a 320 dp-wide screen with 1.3 font scale. All 22 emulator tests passed again; debug and signed release builds succeeded. Logs: `build/visual-refinement-*.log`.
- Latest combined UI/motion preview: `installer/gecko-ui-preview.apk`, version 1.1.0 (10100), signed with the same release certificate. SHA-256: `aa9189ec1a7db07f2ac4cde7433c04cfed3d3d4a30173f6d26b28ad3cd9ac557`.
- Latest screenshots: `build/button-audit/refined-chat-light.png`, `refined-chat-dark.png`, `refined-settings-light.png`, and `refined-compact-chat.png`. Earlier videos show the preceding motion pass.
