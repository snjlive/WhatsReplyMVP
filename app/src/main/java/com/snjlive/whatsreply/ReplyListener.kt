package com.snjlive.whatsreply

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

object ReplyState {
    const val TEST_REPLY = "TEST from Whats Reply MVP"
    var sender = ""
    var text = ""
    var status = "Ожидание уведомления WhatsApp Business"
    var key: String? = null
    var action: Notification.Action? = null
    var connected = false
    var changed: (() -> Unit)? = null
    fun update() { changed?.invoke() }
}

class ReplyListener : NotificationListenerService() {
    override fun onListenerConnected() {
        ReplyState.connected = true
        activeNotifications?.sortedBy { it.postTime }?.forEach { onNotificationPosted(it) }
        ReplyState.update()
    }
    override fun onListenerDisconnected() {
        ReplyState.connected = false
        ReplyState.action = null
        ReplyState.update()
    }
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName != "com.whatsapp.w4b") return
        val n = sbn.notification
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        if (n.category != Notification.CATEGORY_MESSAGE && n.extras.getCharSequence(Notification.EXTRA_TEXT).isNullOrBlank()) return
        val messages = if (android.os.Build.VERSION.SDK_INT >= 30) Notification.MessagingStyle.Message.getMessagesFromBundleArray(n.extras.getParcelableArray(Notification.EXTRA_MESSAGES)) else emptyList()
        val last = messages.lastOrNull()
        ReplyState.sender = (if (android.os.Build.VERSION.SDK_INT >= 28) last?.senderPerson?.name?.toString() else last?.sender?.toString())
            ?: n.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        ReplyState.text = last?.text?.toString()
            ?: n.extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: n.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        ReplyState.key = sbn.key
        ReplyState.action = n.actions?.firstOrNull { a ->
            a.remoteInputs?.any { it.allowFreeFormInput } == true && a.actionIntent != null
        }
        ReplyState.status = if (ReplyState.action != null) "Ответ доступен" else "В уведомлении нет действия для ответа"
        ReplyState.update()
    }
    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.key == ReplyState.key) {
            ReplyState.action = null
            ReplyState.status = "Уведомление удалено. Ожидание нового сообщения"
            ReplyState.update()
        }
    }
}

fun sendTestReply(activity: MainActivity) {
    val action = ReplyState.action ?: return
    val inputs = action.remoteInputs?.filter { it.allowFreeFormInput }?.toTypedArray() ?: return
    val results = Bundle()
    inputs.forEach { results.putCharSequence(it.resultKey, ReplyState.TEST_REPLY) }
    val intent = Intent()
    RemoteInput.addResultsToIntent(inputs, intent, results)
    if (android.os.Build.VERSION.SDK_INT >= 28) RemoteInput.setResultsSource(intent, RemoteInput.SOURCE_FREE_FORM_INPUT)
    try {
        action.actionIntent.send(activity, 0, intent)
        ReplyState.status = "Тестовый ответ передан WhatsApp Business"
        ReplyState.action = null
    } catch (_: PendingIntent.CanceledException) {
        ReplyState.status = "Действие устарело. Дождитесь нового сообщения"
        ReplyState.action = null
    } catch (_: SecurityException) {
        ReplyState.status = "WhatsApp Business не разрешил отправить ответ"
        ReplyState.action = null
    }
    ReplyState.update()
}
