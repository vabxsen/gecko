# Chat additions

## Implemented

- Saved drafts: text and the attached image or extracted document are stored in Room for each conversation, including the new-chat composer. Drafts restore on returning to a chat and from a reopened database. Sending clears the originating draft; rejected sends keep it. Deleting a conversation removes its draft, and clearing all conversations removes all drafts.
- Document chat: the attachment menu offers photos or PDF/plain-text/Markdown documents. Extraction happens locally; document text is sent only when the user sends the message. PDF sources retain physical page numbers, and the chat provides a selectable source preview. Follow-up questions retain the latest source through history trimming. The provider receives instructions to cite supplied page labels; citations are model-generated and are not independently fact-checked.
- Read aloud: assistant replies have a speaker/stop control. One Android text-to-speech engine serves the chat; playback stops when leaving the screen or backgrounding it. Long answers are chunked without splitting emoji. Fenced code is skipped and Markdown link syntax is removed. Missing engines or language voices produce feedback.
- Connection diagnostics: connection details show the latest known result, an actionable explanation, and a check button. Checks verify model access and a real reply without enabling a disabled connection or switching the active connection. The time and duration describe checks made in the current settings session; no account balance is inferred.

## Supported document limits

- PDF, UTF-8 plain text, and Markdown; one attachment per message.
- Maximum 10 MiB, 100 PDF pages, and 40,000 extracted characters. Files beyond these limits are rejected with guidance, not silently truncated.
- Password-protected PDFs, PDFs that disallow extraction, and scanned PDFs without selectable text are rejected. For scans, the message recommends attaching an image. Pages without extractable text inside a mixed PDF are explicitly marked.
- Documents too large for a known selected-model context window receive a context-length error before a request is made.

## Verification

- Debug and optimized release builds pass. All 128 unit tests pass across chat (24), settings (20), database (8), data (17), and domain (59), including provider-boundary HTTP tests using a local mock server. The final debug APK installs and launches successfully on the isolated emulator.
- Unit coverage includes draft switching/recreation, closing/reopening the database, document persistence and page-labelled request content, follow-up context retention, speech chunking, and diagnostics preserving the active connection.
- Android emulator: 24 chat tests, 7 settings tests, and 4 database migration tests pass. The migration suite checks that version 5 messages survive the additive version 6 migration.
- The full chat-screen regression first reproduced an old draft being saved again during first-message composer disposal, then passed after disposal was changed to read live state.
- An existing clipboard test initially timed out while an Android System UI ANR dialog held focus. It passed after dismissing that system dialog and reducing emulator rendering load.
- Visual captures: `build/feature-additions/document-draft.png` and `document-source.png`.
- No real user API key was used. Generated answers were stubbed in the full-screen test; PDF extraction and request construction were exercised independently. Speaker controls and text preparation were tested; audible playback on a physical device remains to be checked.

PDFBox-Android 2.0.27.0 performs extraction. Its license and notices are bundled under `app/src/main/assets/licenses/pdfbox-android/`. Its optional JPEG-2000 image decoder is not included; this feature extracts text rather than rendering embedded PDF images.

These changes do not bump the app version, create a commit, or publish a release.
