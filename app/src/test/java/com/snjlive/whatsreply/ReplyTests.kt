package com.snjlive.whatsreply

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.UserHandle
import android.service.notification.StatusBarNotification
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35])
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.LEGACY)
class ReplyTests {
    private lateinit var context: Context
    private lateinit var store: HistoryStore
    @Before fun before() {
        context = RuntimeEnvironment.getApplication()
        context.deleteDatabase("history.db")
        store = HistoryStore(context)
        ReplyState.targets.clear(); ReplyState.connected = true
    }
    @After fun after() { store.close(); ReplyState.targets.clear(); ReplyState.changed = null }
    private fun notification(chatId: String, name: String = "Same name", body: String = "Hello", time: Long = 100): StatusBarNotification {
        val pending = PendingIntent.getBroadcast(context,chatId.hashCode(),Intent("reply.$chatId").setPackage(context.packageName),PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val action = Notification.Action.Builder(null,"Reply",pending)
            .addRemoteInput(RemoteInput.Builder("reply_text").build()).build()
        val n = Notification.Builder(context,"messages").setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle(name).setCategory(Notification.CATEGORY_MESSAGE).setShortcutId(chatId)
            .setStyle(Notification.MessagingStyle("Me").addMessage(body,time,name))
            .addAction(action).build()
        return StatusBarNotification("com.whatsapp.w4b","com.whatsapp.w4b",chatId.hashCode(),chatId,1000,0,0,n,android.os.Process.myUserHandle(),time)
    }
    @Test fun notificationReplayDeduplicatesAndContactsStaySeparate() {
        val listener = Robolectric.buildService(ReplyListener::class.java).create().get()
        val a = notification("a"); val b = notification("b")
        listener.onNotificationPosted(a); listener.onNotificationPosted(a); listener.onNotificationPosted(b)
        assertEquals(2,store.chats().size)
        store.chats().forEach { assertEquals(1,store.entries(it.id).size) }
        assertEquals(2,ReplyState.targets.size)
        listener.onDestroy()
    }
    @Test fun removedNotificationDisablesOnlyItsOwnContact() {
        val listener = Robolectric.buildService(ReplyListener::class.java).create().get()
        val a = notification("a"); val b = notification("b")
        listener.onNotificationPosted(a); listener.onNotificationPosted(b); listener.onNotificationRemoved(a)
        assertFalse(ReplyState.targets.containsKey("com.whatsapp.w4b:a"))
        assertTrue(ReplyState.targets.containsKey("com.whatsapp.w4b:b"))
        assertEquals(2,store.chats().size)
        listener.onDestroy()
    }
    @Test fun staleDraftCannotBeSentAfterNewIncoming() {
        val listener = Robolectric.buildService(ReplyListener::class.java).create().get()
        listener.onNotificationPosted(notification("a"))
        val chat = store.chats().single(); val first = store.latest(chat.id,"incoming")!!
        listener.onNotificationPosted(notification("a",body="New question",time=200))
        assertTrue(sendReply(context,store,chat,first.id,"Old answer").contains("новое"))
        assertNull(store.latest(chat.id,"sent"))
        listener.onDestroy()
    }
    @Test fun successfulHandoffSavesFinalTextAndPreventsSecondSend() {
        val listener = Robolectric.buildService(ReplyListener::class.java).create().get()
        listener.onNotificationPosted(notification("a"))
        val chat = store.chats().single(); val incoming = store.latest(chat.id,"incoming")!!
        val result = sendReply(context,store,chat,incoming.id,"Final answer")
        assertTrue(result.startsWith("Передано"))
        assertEquals("Final answer",store.latest(chat.id,"sent")?.text)
        val broadcast = org.robolectric.Shadows.shadowOf(RuntimeEnvironment.getApplication()).broadcastIntents.last()
        assertEquals("reply.a",broadcast.action)
        assertEquals("Final answer",RemoteInput.getResultsFromIntent(broadcast)?.getCharSequence("reply_text")?.toString())
        sendReply(context,store,chat,incoming.id,"Final answer")
        assertEquals(1,store.entries(chat.id).count { it.kind == "sent" })
        listener.onDestroy()
    }
    @Test fun historyPersistsAcrossReopenAndExcludesUnsentDraftsFromAi() {
        val chat = Chat("a","Jira")
        val incoming = store.add(chat,"incoming","Payment?",time=100)
        store.add(chat,"draft","Abandoned draft",source=incoming)
        store.add(chat,"sent","Monday",source=incoming)
        store.close(); store = HistoryStore(context)
        assertEquals(3,store.entries(chat.id).size)
        val payload = store.context(chat.id).toString()
        assertTrue(payload.contains("Monday")); assertFalse(payload.contains("Abandoned"))
    }
    @Test fun latestIncomingRemainsInContextAfterManyDraftRevisions() {
        val chat = Chat("a","Jira")
        val id = store.add(chat,"incoming","Latest message")
        repeat(40) { store.add(chat,"draft","Revision $it",source=id) }
        assertTrue(store.context(chat.id).toString().contains("Latest message"))
    }
    @Test fun disconnectedListenerCannotSend() {
        val listener = Robolectric.buildService(ReplyListener::class.java).create().get()
        listener.onNotificationPosted(notification("a")); listener.onListenerDisconnected()
        val chat = store.chats().single()
        sendReply(context,store,chat,store.latest(chat.id,"incoming")!!.id,"answer")
        assertNull(store.latest(chat.id,"sent"))
        listener.onDestroy()
    }
}
