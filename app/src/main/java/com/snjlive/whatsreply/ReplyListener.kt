package com.snjlive.whatsreply

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.security.MessageDigest

data class ReplyTarget(val key: String, val action: Notification.Action, val incoming: Long)
object ReplyState {
    val targets = mutableMapOf<String,ReplyTarget>()
    var connected = false
    var changed: (() -> Unit)? = null
    fun update() { changed?.invoke() }
}
class ReplyListener : NotificationListenerService() {
    private data class IncomingMessage(val text: String, val timestamp: Long, val sender: String?)
    private lateinit var store: HistoryStore
    override fun onCreate() { super.onCreate(); store = HistoryStore(this) }
    override fun onDestroy() { store.close(); super.onDestroy() }
    override fun onListenerConnected() {
        ReplyState.connected = true
        ReplyState.targets.clear()
        activeNotifications?.sortedBy { it.postTime }?.forEach { onNotificationPosted(it) }
        ReplyState.update()
    }
    override fun onListenerDisconnected() {
        ReplyState.connected = false; ReplyState.targets.clear(); ReplyState.update()
    }
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if(sbn.packageName != "com.whatsapp.w4b") return
        val n = sbn.notification
        if(n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val title = n.extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()
            ?: n.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        if(title.isBlank()) return
        // Shortcut is WhatsApp's conversation identity. Never merge contacts by display name.
        val identity = n.shortcutId?.takeIf { it.isNotBlank() } ?: sbn.key
        val chat = Chat("${sbn.packageName}:$identity",title)
        val messages = if(android.os.Build.VERSION.SDK_INT >= 30) {
            Notification.MessagingStyle.Message.getMessagesFromBundleArray(n.extras.getParcelableArray(Notification.EXTRA_MESSAGES))
                .map { IncomingMessage(it.text?.toString().orEmpty(),it.timestamp,it.sender?.toString()) }
        } else {
            n.extras.getParcelableArray(Notification.EXTRA_MESSAGES)?.mapNotNull { parcel ->
                val b = parcel as? Bundle ?: return@mapNotNull null
                val sender = if(android.os.Build.VERSION.SDK_INT >= 28)
                    (b.getParcelable<android.app.Person>("sender_person")?.name ?: b.getCharSequence("sender"))?.toString()
                    else b.getCharSequence("sender")?.toString()
                IncomingMessage(b.getCharSequence("text")?.toString().orEmpty(),b.getLong("time"),sender)
            }.orEmpty()
        }
        val group = if(android.os.Build.VERSION.SDK_INT >= 28) n.extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION) else n.extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE) != null
        if(messages.isNotEmpty()) {
            messages.forEach { m ->
                // MessagingStyle uses a null sender for the user's own messages.
                val sender = m.sender?.toString()
                if(sender != null && !m.text.isNullOrBlank()) {
                    val body = if(group) "$sender: ${m.text}" else m.text.toString()
                    store.add(chat,"incoming",body,m.timestamp,fingerprint = fingerprint(chat.id,m.timestamp,body))
                }
            }
        } else {
            val body = n.extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
                ?: n.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
            if(body.isBlank() || n.category != Notification.CATEGORY_MESSAGE) return
            store.add(chat,"incoming",body,sbn.postTime,fingerprint = fingerprint(chat.id,sbn.postTime,body))
        }
        val latest = store.latest(chat.id,"incoming") ?: return
        val action = n.actions?.firstOrNull { it.remoteInputs?.any { input -> input.allowFreeFormInput } == true }
        if(action != null) ReplyState.targets[chat.id] = ReplyTarget(sbn.key,action,latest.id)
        else ReplyState.targets.remove(chat.id)
        ReplyState.update()
    }
    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        ReplyState.targets.entries.removeAll { it.value.key == sbn.key }; ReplyState.update()
    }
    private fun fingerprint(chat: String, time: Long, text: String): String = MessageDigest.getInstance("SHA-256")
        .digest("$chat\u0000$time\u0000$text".toByteArray()).joinToString("") { "%02x".format(it) }
}
fun sendReply(context: Context, store: HistoryStore, chat: Chat, source: Long, text: String): String {
    val target = ReplyState.targets[chat.id] ?: return "Уведомление устарело. Дождитесь нового сообщения."
    if(!ReplyState.connected || target.incoming != source || store.latest(chat.id,"incoming")?.id != source)
        return "Пришло новое сообщение. Подготовьте ответ заново."
    val inputs = target.action.remoteInputs?.filter { it.allowFreeFormInput }?.toTypedArray() ?: return "Ответ недоступен"
    val results = Bundle().apply { inputs.forEach { putCharSequence(it.resultKey,text) } }
    val intent = Intent()
    RemoteInput.addResultsToIntent(inputs,intent,results)
    if(android.os.Build.VERSION.SDK_INT >= 28) RemoteInput.setResultsSource(intent,RemoteInput.SOURCE_FREE_FORM_INPUT)
    return try {
        target.action.actionIntent.send(context,0,intent)
        ReplyState.targets.remove(chat.id)
        store.add(chat,"sent",text,source=source)
        "Передано в WhatsApp • доставка не подтверждается приложением"
    } catch(_: PendingIntent.CanceledException) {
        ReplyState.targets.remove(chat.id); "Уведомление устарело. Дождитесь нового сообщения."
    } catch(_: SecurityException) {
        ReplyState.targets.remove(chat.id); "WhatsApp не разрешил отправку"
    }
}
