# OpenRouter verification — 2026-10-06

## Result

142 automated tests passed across provider, domain, settings, and chat modules; the debug APK compiled successfully. Eight targeted tests were added. Six failed against the previous implementation and passed after the fixes.

A subsequent live test with a user-supplied key passed through Gecko's actual `OpenRouterProvider` on the desktop JVM:

- Authentication: `/api/v1/key` returned HTTP 200.
- Catalog: the provider parsed 464 models and found `openrouter/free`.
- Streaming: a nonempty completed reply arrived in 1,910 ms.
- Non-streaming: a nonempty completed reply arrived in 3,886 ms.

Both chat requests used `openrouter/free` and the synthetic prompt “Reply with just OK.” The key was supplied through the test process environment, not saved in source or test output. The temporary live harness is under ignored `build/` and is only included through an explicit Gradle init script. These checks verify this key and the current local provider implementation at test time; they do not verify an installed Android APK, every model, or future availability.

## Changes and coverage

- Handle choice-level errors and `finish_reason: "error"` in streaming and non-streaming responses. A partial failed reply cannot become a successful completion or be replayed after text was received.
- Prefer `openrouter/free` when present in the catalog, ahead of individual free models. Keep the existing maximum of five setup candidates.
- Allow OpenRouter setup to try another candidate after a model rate limit or upstream outage. Invalid credentials still stop immediately; existing 20-second candidate and 90-second overall timeouts remain in force.
- Read structured input/output modalities, with the legacy combined modality as fallback. Exclude models without text input/output and distinguish image input from image generation.
- Verify SSE processing comments, final usage chunks, and invalid-key error handling.

## Validation

```powershell
./gradlew.bat :core:provider:test :domain:test :feature:settings:testDebugUnitTest :feature:chat:testDebugUnitTest :app:assembleDebug --console=plain
```

| Suite | Passed |
| --- | ---: |
| Provider | 47 |
| Domain | 57 |
| Settings | 19 |
| Chat | 19 |

The 142-test suite had no failures, errors, or skipped tests. The subsequent dedicated live test also passed. No version bump or release upload was performed.

API contract references: [OpenRouter response format](https://openrouter.ai/docs/api_reference/overview), [free router](https://openrouter.ai/openrouter/free/providers).
