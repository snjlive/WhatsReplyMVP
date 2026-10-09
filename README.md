# WhatsReply MVP 0.2

Native Kotlin, Android 8+, WhatsApp Business only (`com.whatsapp.w4b`). Continues the proven notification → RemoteInput transport from 0.1.

## Everyday workflow

Receive a message → open WhatsReply → **Что ответить?** → speak Russian → review or listen to the draft → **Исправить** to revise by voice → **Отправить**. Text instructions and manual draft editing are supported. Tap the contact heading to switch contacts. Bottom action buttons stay outside the scrolling history.

Local SQLite history stores incoming messages, draft versions and the final text handed to WhatsApp. History/drafts survive process death. No backup, CRM, Airtable, Outliner, Coexistence, desktop, automatic sends, or provider key in APK. Notifications are deduplicated by conversation/timestamp/body; contacts are identified by shortcut ID or notification key, never by display name. Groups use the conversation title with individual sender text. History starts with captured notifications; this does not import older WhatsApp conversations or messages written in WhatsApp outside the app. If WhatsApp does not expose a stable shortcut, the notification-key fallback can split history across changed notification keys, intentionally avoiding cross-contact merges.

Replies require a live notification action for the selected contact and the incoming message used to prepare the draft. Notification removal/disconnection/new incoming disables stale sending. One successful handoff consumes the action; delivery is not independently verified. A cancelled action never produces a sent entry. Sending is always an explicit button press.

Speech recognition uses Android's installed recognition activity in `ru-RU` (it can transmit audio to that service and needs an installed speech provider). The app itself records no audio and needs no microphone permission. TTS uses installed voices for Thai, Russian, or English; unavailable voices are reported. An instruction can be typed if recognition is unavailable. Other languages remain readable/editable but MVP TTS detection supports these three.

AI uses an authenticated HTTPS proxy, only on explicit generation/revision. It receives up to 16 incoming/sent messages of the selected contact, the instruction and, for revision, the current draft. Endpoint and access code are configured once in ⚙; code is encrypted using Android Keystore. Provider key stays server-side; Android rejects `sk-` keys. No default deployed endpoint or credentials are shipped. **AI remains unavailable until the proxy is hosted and credentials are supplied.** See [backend](backend/README.md).

## Install

1. Install `downloads/WhatsReplyMVP-0.2-debug.apk` on the phone (allow installation from that browser/file app if asked).
2. Open WhatsReply and allow notification access if it is not already enabled.
3. Receive a fresh WhatsApp Business message. For AI, set the supplied server connection once in ⚙. Without a connected server, local history, editable replies and RemoteInput sending work; generated replies do not.

The APK signature was verified against the locally supplied 0.1 APK; the same application ID and debug certificate permit an in-place upgrade of the locally built 0.1 APK. APKs built independently by GitHub Actions can have a different debug certificate; Android will then reject an in-place upgrade. Do not uninstall a version containing needed local history.

## Build and verify

JDK 17, Android SDK 36. `./gradlew assembleDebug lintDebug testDebugUnitTest`. APK: `app/build/outputs/apk/debug/app-debug.apk`. Robolectric tests cover Android 9 and 15 paths: replay deduplication, same-name contact isolation, notification removal, stale-draft rejection, persisted history, successful RemoteInput handoff, no duplicate send, disconnect handling. Proxy tests: `python3 -m unittest discover -s backend -v` (fake provider, no costs).

See [verification status](VALIDATION.md) for checks performed and current blockers.

Physical-device testing of the new UI/STT/TTS and live AI requires the user's phone and provisioned AI backend. The original 0.1 transport was already verified on the user's phone.
