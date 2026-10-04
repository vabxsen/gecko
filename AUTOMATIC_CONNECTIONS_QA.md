# Automatic connections repair

The normal chat and key screens now expose one connection per saved key. The full model picker,
search and catalog expansion were removed. Setup and replacement keys share the same real reply
probe. Only a model that completes a nonempty reply is saved as that key's automatic model.

Connection selection preserves the previous working connection on failure. Candidate checks have
a 20-second limit inside a 90-second overall limit; an individual timeout permits the next
candidate. Invalid keys fail immediately. Cancellation clears the testing indicator. Models that
declare no streaming support use regular completions. Retrying a connection error checks the key
again instead of regenerating an unrelated conversation.

Database v5 adds a nullable verified model field. Existing keys and conversations survive; old
catalog selections are not presented as verified. An existing key can be checked with Connect
automatically, without pasting it again. Replacing its key or endpoint invalidates verification.

The extra typewriter timer was removed: chat now renders persisted stream updates directly.
Keyboard spacing no longer runs a competing animation. Title changes no longer slide, screen
transitions use fades, and settings headings and body letter spacing are more restrained.

## Verification

Final local run: 109 automated tests passed; 19 Android device tests passed (11 chat,
5 settings, 3 database migrations). Debug and signed release builds passed. The local preview
APK is `installer/gecko-automatic-connections-preview.apk`; its release version remains 1.0.9.

- Domain and view-model regressions cover candidate fallback, invalid keys, empty replies,
  timeout fallback, cancellation, key replacement, selection preservation and connection retry.
- MockWebServer exercises the HTTP client: a 403 from the first model falls through to a model
  that returns a real SSE response. A model without streaming support sends a regular request.
- Android UI tests verify a 50-model catalog still produces one connection row, no catalog
  search or expansion, and disabled repeated taps while connecting.
- Android migration tests validate v1/v3 history and the new v4-to-v5 upgrade, including retained
  connection IDs and chat data and cleared legacy verification claims.
- Manual emulator checks use synthetic data only: connection sheet, connection details, and
  narrow-screen / enlarged-text layout. Debug and signed release builds are checked locally.

Live paid/free provider accounts, billing balances and current provider availability were not
tested in this repair. Passing mocked requests does not establish that every real key will work.
No release version or published GitHub asset is changed by this repair.
