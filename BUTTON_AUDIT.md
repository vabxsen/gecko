# Gecko button audit — 2026-10-04

Result: local interactions passed, with live-service limits listed below. This is not a claim that every provider or device has been verified.

## Environment and evidence

- Native Android emulator `emulator-5580`; no physical device or user API keys used.
- Disposable conversations, synthetic keys, cached models, and an app screenshot used as the attachment fixture.
- 390 dp-wide phone; 360 x 640 dp phone at 150% text size also inspected.
- 118 recorded UI taps across 69 distinct labels, including repeated actions and Android picker/permission controls. This count is not a count of independently verified features.
- Manual action log: `build/button-audit/actions.jsonl`; screenshots and latest UI hierarchy: `build/button-audit/`.
- Unit results: 39 passed (17 chat, 22 settings), including delayed model-save/navigation regression.
- Device results: 15 passed (10 chat, 5 settings); see `build/button-audit-chat-final-tests.log` and `build/button-audit-settings-tests.log`.
- Final debug/release build: `build/button-audit-final-controls.log`; release lint passed.

## Fixes made

1. New chat clears the previous draft; composer state is reset when changing conversations.
2. New-chat and conversation selection controls are disabled while generating, matching the ViewModel's existing restriction. Edit is also disabled during generation; copy stays available.
3. Settings model selection waits for persistence before navigating back, preventing navigation from cancelling the save.
4. Export writes off the UI thread, uses UTF-8, and reports success or a save failure instead of swallowing errors.
5. Added Android's speech-recognition service visibility declaration. Afterward, the microphone opened the permission dialog and entered listening mode. [Android documentation](https://developer.android.com/reference/android/speech/SpeechRecognizer.html).
6. Removing a saved key or starting another model refresh clears stale key error text.
7. Drawer search's keyboard action dismisses the keyboard; blank conversation rename cannot be saved.
8. Fixed source encoding that corrupted the update-progress ellipsis.

## Coverage

| Screen / controls | Verification and result |
| --- | --- |
| Welcome: Connect your AI | Opened setup from the cleared-data welcome state. Passed. |
| Header: drawer, new chat, model selector | Opened/closed navigation and selector. New chat cleared a populated draft after the fix. Passed. |
| Starter chips: Explain something, Shape an idea | Filled the draft without sending; native UI and device tests. Passed. |
| Composer: Send, keyboard Send, Stop | Device tests cover accepted/rejected sends, draft preservation and stop callback. Native send with an unreadable test key showed the recovery dialog. Live generation remains unverified. |
| Attachments: Add, select, Remove | Android picker opened; selected only the audit screenshot; preview appeared and removal worked. Passed locally. Provider image processing unverified. |
| Microphone | Permission dialog, permission grant and listening state verified after manifest fix. Real transcription and the stop-listening action remain limited by the emulator's silent audio/service timeout. |
| Drawer: select chat, search, clear/close search | Selected seeded chats, filtered by title, closed search and restored list. Passed. |
| Conversation menu: Pin, Unpin, Rename, Save, Delete | Labels/state changed, rename persisted and deletion removed the disposable chat. Passed. |
| Messages: Copy, Edit, Cancel, edited Send | Native copy/edit/cancel plus device tests for edit submission and blank-send disablement. Passed locally; provider response to an edited message unverified. |
| Regenerate | Device test verified action callback and transition to stop control. Actual regenerated provider response unverified. |
| Scroll to bottom | Dedicated device test scrolled away and returned to the newest message. Passed. |
| Model sheet: choose, close, search, clear, show all/fewer, Manage API keys | Native controls exercised with 13 cached models; search/selection also device-tested. Passed. |
| Model list's conditional Load models | Callback/source reviewed; no successful live catalog load with a real key in this audit. Limited coverage. |
| Settings model selection | Opened from connection details and selected model; delayed-save regression test ensures navigation follows persistence. Passed. |
| Settings destinations and Back | Appearance, Chat preferences, AI Providers, Data & Privacy, About, and return navigation exercised. Passed. |
| Appearance: System, Light, Dark, dynamic color | Choices/toggles exercised; dark preview captured. Passed on this emulator; older Android dynamic-color fallback not tested here. |
| Chat preferences: Send on Enter, Stream responses | Toggled both directions and revisited. Device/unit coverage of preference behavior passed. |
| Provider list: enable/disable, details, Add API key | Toggle reflected Disabled/active states; destinations opened. Passed. |
| Add key: paste, show/hide, choose/change provider, advanced show/hide, inputs, connect | Clipboard content reached field; provider menu opened; OpenAI selected; advanced endpoint edited. Reserved invalid endpoint produced a readable failure and re-enabled retry. Recognized/ambiguous-key device tests passed. Real successful connection remains unverified. |
| Saved key: show/hide, Save key, Test connection, Refresh models | Synthetic key saved, survived reopening and visibility toggle. Unreadable-key and unreachable-endpoint failures surfaced. No real provider success verified. |
| Advanced connection: Save name, Save URL, Remove saved key | Updated values persisted; removal reset connection state. Stale error cleanup fixed and unit build checked. Passed local path. |
| Delete connection: open, Cancel, Delete | Cancel retained connection; confirm returned to provider list without it. Passed. |
| Export: choose location and Save | Markdown file read back from Downloads contained the seeded user/assistant messages. Success feedback displayed. Passed. Failure feedback code-reviewed; storage-provider failure not injected. |
| Delete all conversations: Cancel, Delete | Cancellation returned normally; confirm showed deletion feedback. Repository behavior also unit-tested. Passed. |
| Clear local data: Cancel, Clear everything | Confirm reset app to unconnected welcome. Repository/key cleanup unit-tested. Passed. |
| Errors: Details, Hide details, Dismiss, Retry/fix | Device callback tests passed; native unreadable-key recovery opened settings. Actual provider retry success unverified. |
| Updates: Check for updates | Native action invoked and returned to idle. No newer-version install was performed; successful remote lookup was not captured. Limited live coverage. |
| Update dialog: Download, Not now, Open settings, Cancel | Device tests verify correct callbacks for available-update and install-permission states. Downloading and replacing the APK through Android installer not exercised. |

## Remaining limits

- A valid key for each provider is required to verify real connection, generation, regeneration, streaming, and image processing. No real credentials were requested or exposed.
- Spoken transcription needs audio input and an operational recognition service; only permission/listening was observed here.
- A newer release is needed to test the real update-download/install journey. Its button dispatches passed device tests.
- This is one emulator configuration, not a full Android device/OS matrix. Tablet controls were reviewed in source; not all tablet interactions were repeated in this audit.

## Deliverable

Signed local preview: `installer/gecko-ui-preview.apk` (version 1.0.8). No release publication or push performed during this audit. No fixture data is included in the APK.
