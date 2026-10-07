package com.snjlive.whatsreply

import android.app.Activity
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var details: TextView
    private lateinit var reply: Button
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (24 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
        }
        layout.addView(TextView(this).apply { text = "Whats Reply MVP"; textSize = 26f })
        layout.addView(TextView(this).apply { text = "1. Разрешите доступ к уведомлениям.\n2. Получите сообщение в WhatsApp Business.\n3. Нажмите кнопку тестового ответа.\n\nОтвет: ${ReplyState.TEST_REPLY}\n"; textSize = 16f })
        layout.addView(Button(this).apply {
            text = "Разрешить доступ к уведомлениям"
            setOnClickListener { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        })
        details = TextView(this).apply { textSize = 18f }
        layout.addView(details)
        reply = Button(this).apply {
            text = "Отправить тестовый ответ"
            setOnClickListener { sendTestReply(this@MainActivity) }
        }
        layout.addView(reply)
        setContentView(layout)
    }
    override fun onResume() {
        super.onResume()
        ReplyState.changed = { runOnUiThread { render() } }
        render()
    }
    override fun onPause() { ReplyState.changed = null; super.onPause() }
    private fun render() {
        val granted = getSystemService(NotificationManager::class.java).isNotificationListenerAccessGranted(ComponentName(this, ReplyListener::class.java))
        details.text = "Доступ: ${if (granted) "разрешён" else "не разрешён"}\nСлужба: ${if (ReplyState.connected) "подключена" else "ожидание подключения"}\n\nОт: ${ReplyState.sender}\n${ReplyState.text}\n\n${ReplyState.status}\n"
        reply.isEnabled = granted && ReplyState.connected && ReplyState.action != null
    }
}
