# MVP 0.2 verification — 2026-10-09

- Existing repository main HEAD verified: `50f17c8d7a5b57b5c23fb1144a03adc880f87643`.
- Final debug APK assembled with SDK 36 / JDK 17. Version code 2, version 0.2, application ID unchanged.
- Final local check: `./gradlew assembleDebug lintDebug testDebugUnitTest -PtestSdks=28` passed; 9 Android tests, zero failures. Lint: zero errors. Proxy: 9 tests passed with fake provider, no real API calls.
- Full GitHub Actions verification passed on commit `07cdcf45901107fb4cb38323e0655009e2efc6a1`: APK build, lint, Android API 28 and 35 tests, 9 proxy tests, artifact upload. Run: https://github.com/snjlive/WhatsReplyMVP/actions/runs/37948532895. Local API 35 verification was limited by disk space; CI completed it.
- APK certificate matches the previous locally supplied 0.1 APK: SHA-256 `06978715ed77d3d22063dd8c9cf82056ecd4569431b267627ea856ea174548c9`.
- No connected Android device/emulator: physical-device STT/TTS/UI testing of 0.2 was not performed. Original RemoteInput transport was confirmed on the user's phone.
- No provider key or hosted AI server is available. Production AI generation and deployment are not verified; the APK intentionally ships without fake AI responses or credentials. Proxy tests cover generation/revision using a fake provider, authentication, request validation, limits and safe errors.
- The user explicitly authorized publication and inspection of OpenAI API settings. Source and the locally signed APK are published on `codex/mvp-0.2`. No provider secret is included. AI provisioning remains pending; no live generation is claimed.

# UX update 0.3 — 2026-10-10

- Native inbox home screen with contact/message search over stored history, waiting-for-reply filter, latest incoming previews and unread markers. Chat view keeps draft, audio/revision/text instruction and send controls together at the bottom. Latest incoming is shown first.
- Debug APK version code 3, version 0.3; same application ID and debug signing key. Database schema and AI settings storage unchanged.
- Local assembleDebug, lintDebug and testDebugUnitTest succeeded: 20 Android test cases across API 28 and 35. Coverage includes inbox search/open/back navigation and draft persistence; existing RemoteInput safeguards remain tested.
- Render service was deployed and real generation returned HTTP 200 on 2026-10-10. User confirmed voice/AI/reply functionality on their phone for 0.2. No provider credentials are included in the repository or APK.
- New 0.3 UI has not yet been visually tested on a physical phone. Stored notifications provide the inbox; this does not import older WhatsApp history.
