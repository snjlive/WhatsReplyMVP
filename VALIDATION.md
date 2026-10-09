# MVP 0.2 verification — 2026-10-09

- Existing repository main HEAD verified: `50f17c8d7a5b57b5c23fb1144a03adc880f87643`.
- Final debug APK assembled with SDK 36 / JDK 17. Version code 2, version 0.2, application ID unchanged.
- Final local check: `./gradlew assembleDebug lintDebug testDebugUnitTest -PtestSdks=28` passed; 9 Android tests, zero failures. Lint: zero errors. Proxy: 9 tests passed with fake provider, no real API calls.
- Tests are configured for API 28 and 35. Earlier transport checks passed both. The final full run was blocked by insufficient disk space while extracting Robolectric's API 35 fonts; final local verification selected API 28. CI workflow now includes both API levels plus proxy tests, but could not be triggered because public GitHub publication was rejected pending explicit user authorization.
- APK certificate matches the previous locally supplied 0.1 APK: SHA-256 `06978715ed77d3d22063dd8c9cf82056ecd4569431b267627ea856ea174548c9`.
- No connected Android device/emulator: physical-device STT/TTS/UI testing of 0.2 was not performed. Original RemoteInput transport was confirmed on the user's phone.
- No provider key or hosted AI server is available. Production AI generation and deployment are not verified; the APK intentionally ships without fake AI responses or credentials. Proxy tests cover generation/revision using a fake provider, authentication, request validation, limits and safe errors.
- Automatic approval review rejected opening OpenAI API-key settings and publishing source/APK to the public GitHub repo without explicit authorization. No workaround was attempted; no AI key was inspected, no public upload occurred.
