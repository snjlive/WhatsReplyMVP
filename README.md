# Whats Reply MVP

Native Kotlin Android MVP, Android 8.0 or later. Only WhatsApp Business (`com.whatsapp.w4b`) notifications are handled. No AI and no INTERNET permission.

Install the debug APK, open the app, enable notification access using the app button, receive an incoming WhatsApp Business message, and press the test reply button. The fixed reply is `TEST from Whats Reply MVP`. The app uses the notification's RemoteInput PendingIntent without launching WhatsApp. Success means the intent was handed to WhatsApp, not confirmed delivery.

Messages stay in memory only. Removed notifications and disconnected listeners disable replies. A notification with no free-form reply action cannot be replied to. Replies are manual, never automatic.

Build: JDK 17, Android SDK 36, `./gradlew assembleDebug`. Output: `app/build/outputs/apk/debug/app-debug.apk`.

Physical-device verification with WhatsApp Business is still required.
