# WR-002 builder evidence

Tested source: ac5d9376358859575b71cbbf884f82f3627918bb. APK 0.5/versionCode 5.
APK SHA256: b8589946d0aa47bb4f1a5535530c9faa71e8856907afb4c107681ee67af450a6.

Language policy is applied in both drafting and final review. Compose follows incoming language, using up to 16 recent incoming messages to disambiguate short/mixed text. Revise preserves current draft language, explicit translation overrides. Language context contains incoming text only and is separate from revision's pruned history. This is model-based inference; offline mocks cannot certify actual model language output.

Chronological bubble history now includes latest incoming. Chat opening scrolls to bottom; updates follow only when already at bottom, preserving upward reading position. Unchanged updates do not rebuild history. Latest incoming preview and draft remain matching 112dp containers, with independent incoming TTS. Original local schema, Notification/RemoteInput source and male/Lighthouse profile preserved.

Validation: assembleDebug/lintDebug passed, 26 Android cases across API28/35, 15 backend cases. New tests cover chronological insertion, latest incoming visibility, open-at-bottom and reading-position retention; backend covers bounded incoming-only language evidence and revision isolation. Published APK downloaded and hash matched. Physical-device and independent acceptance pending.

Render remains pending manual latest-commit deployment; native Chrome automation returns a blank page after navigation. Live acceptance must verify English/Thai/Russian incoming with Russian instruction, revision preserving English despite Russian incoming, explicit Russian override of English, and short/mixed incoming using substantive recent thread language. Never label old-server outputs as new-version acceptance.

Direct owner authorization used an isolated detached worktree at /tmp/whatsreply-wr002. Original checkout unchanged. The Blackboard execution queue was concurrently enabled by Reasoning during this turn; its Execution field was preserved. Codex handoff states direct implementation to avoid duplicate build. Builder fields published using fresh read/compare and exact readback; no self-approval. Confirmed projection and original imported specification are retained under .au.
