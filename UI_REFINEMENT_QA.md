# UI refinement — 2026-10-06

Implemented the eight-screen review while preserving Gecko's monochrome palette and curled logo.

## Changes

- Connection setup: smaller header, Paste inside the key field, and one shared Connect action. On a normal screen the action sits directly below the field; with the keyboard open or a viewport shorter than 500 dp it stays in a footer above the keyboard. The decorative header collapses in that compact state; the form remains scrollable.
- Welcome: moved the content higher, tightened spacing, and used shared typography. Connected users see “What's on your mind?” with the existing functional prompt suggestions.
- Drawer: quieter search control and explanatory empty-state text, including a separate no-search-results message.
- Settings: removed redundant introductory copy, tightened section and row spacing, and renamed AI Providers to AI connections.
- Connections: one compact empty-state explanation and a clear Connect AI action; existing provider status and reconnection behavior are retained.
- Appearance: shorter theme rows, smaller previews, and a split light/dark System preview. The off-state switch now uses on-surface-variant for its thumb and outline in both themes.
- Shared typography and shape: 16 sp / 22 sp row titles, 24 sp page introductions, and 20 dp large card corners. Existing 48 dp icon-button targets are preserved.
- Motion: routine reveals shortened from 440 to 220 ms with less travel and shorter welcome staggers. Theme selection and drawer header transitions explicitly honor reduced motion.

## Verification

- Debug app and instrumentation APKs build successfully.
- 38 unit tests pass: 19 settings and 19 chat.
- 23 device tests pass: 6 settings and 17 chat, including copy/paste, provider selection, message controls, reduced motion, and a new large-text/short-viewport regression test for Connect.
- Fresh screenshots are captured under `build/ui-refinement/`, including the keyboard-open state, settings, drawer, connection list, and both theme previews. The original audit evidence remains in `build/ui-review/` and `build/button-audit/`.
- Verification uses an isolated emulator at 780 × 1784 pixels and density 320. Synthetic key text is used for layout checks; it is not submitted to a provider.

No release, version bump, or API behavior change is included. This is focused UI verification, not a claim of exhaustive accessibility certification or performance testing on physical devices.
