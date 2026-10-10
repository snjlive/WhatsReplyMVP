# WR-001 builder handoff

Source commit: 3381190. APK 0.4, versionCode 4.

Implemented explicit compose/revise client payloads and server prompts. Revision discards prior sent/context and receives original incoming, current draft and latest instruction only. Persistent male/Lighthouse author profile; server enforces canonical author profile. Separate model final review uses a strict JSON schema; unsafe/malformed results and obvious process leakage fail closed. Incoming and answer containers both 112dp; independent incoming TTS button; Text retained. Notification/RemoteInput source files unchanged.

Validation: Android assembleDebug, lintDebug, 22 test cases on APIs 28/35 passed. Backend 12 tests passed (local fake provider), including author/mode isolation, revision context pruning and final-check rejection. Real endpoint returned male revision and concise replies, but a contract probe returned HTTP 200 for an invalid mode: this proves OLD backend remains active. Those real outputs are NOT acceptance of new WR-001 server logic.

Deployment blocker: public Git repository push did not redeploy Render. Render page is blank through available native automation. Manual Deploy -> Deploy latest commit must activate 3381190 before new live checks. No server key changes needed.

Physical device UX/TTS/RemoteInput verification and independent/user acceptance pending; no self-approval. Two model calls per draft now; final semantic correctness is not guaranteed by a finite model check. AU CLI currently rejects WR prefixes, so preserve the imported task and use compact connector preflight/readback without changing central protocol scope.

APK SHA256: 43e13f4a7e4ea1d90fb0ff3e6c0f2c43c37c55fd6264bd5969f6b79ca45de176
