package com.snjlive.whatsreply

import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28,35])
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.LEGACY)
class ActivityTests {
    @Before fun before() {
        RuntimeEnvironment.getApplication().deleteDatabase("history.db")
        ReplyState.targets.clear(); ReplyState.connected = false; ReplyState.changed = null
    }
    private fun descendants(v: View): List<View> = listOf(v) + if(v is ViewGroup) (0 until v.childCount).flatMap { descendants(v.getChildAt(it)) } else emptyList()
    @Test fun emptyScreenStartsWithDisabledReplyActions() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val views = descendants(controller.get().window.decorView)
        val buttons = views.filterIsInstance<Button>()
        assertFalse(buttons.first { it.text.toString().contains("Что ответить") }.isEnabled)
        assertFalse(buttons.first { it.text.toString().startsWith("Отправить") }.isEnabled)
        controller.pause().stop().destroy()
    }
    @Test fun inboxSearchFiltersByMessageAndOpensMatchingChat() {
        val context = RuntimeEnvironment.getApplication()
        HistoryStore(context).use {
            it.add(Chat("jira","Jira"),"incoming","Payment tomorrow?")
            it.add(Chat("alex","Alex"),"incoming","Hello")
        }
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val views = descendants(controller.get().window.decorView)
        views.filterIsInstance<EditText>().first { it.hint.toString().startsWith("Поиск") }.setText("Payment")
        val filtered = descendants(controller.get().window.decorView).filterIsInstance<android.widget.TextView>().filter { (it.parent as? View)?.isClickable == true }.map { it.text.toString() }
        assertTrue(filtered.any { it.contains("Jira") })
        assertFalse(filtered.any { it.contains("Alex") })
        val jira = descendants(controller.get().window.decorView).filterIsInstance<android.widget.TextView>().first { it.text.toString().contains("Jira") }
        (jira.parent as View).performClick()
        val draft = descendants(controller.get().window.decorView).filterIsInstance<EditText>().first { it.hint.toString().contains("микрофон") }
        assertEquals(View.VISIBLE,(draft.parent as View).visibility)
        controller.get().onBackPressed()
        assertEquals(View.GONE,(draft.parent as View).visibility)
        controller.pause().stop().destroy()
    }
    @Test fun incomingAudioAndMatchedMessageContainers() {
        val context=RuntimeEnvironment.getApplication()
        HistoryStore(context).use { it.add(Chat("jira","Jira"),"incoming","Hello") }
        val controller=Robolectric.buildActivity(MainActivity::class.java).setup()
        val views=descendants(controller.get().window.decorView)
        val incoming=views.filterIsInstance<android.widget.TextView>().first { it.text.toString().endsWith("\nHello") }
        val answer=views.filterIsInstance<EditText>().first { it.hint.toString().contains("микрофон") }
        assertEquals(answer.layoutParams.height,incoming.layoutParams.height)
        assertTrue(views.filterIsInstance<Button>().any { it.contentDescription == "Прослушать входящее" })
        assertEquals("male",AiSettings(context).authorProfile.getString("gender"))
        controller.pause().stop().destroy()
    }
    @Test fun manuallyEditedDraftSurvivesActivityRecreation() {
        val context = RuntimeEnvironment.getApplication()
        HistoryStore(context).use { it.add(Chat("jira","Jira"),"incoming","Payment?") }
        val first = Robolectric.buildActivity(MainActivity::class.java).setup()
        val edit = descendants(first.get().window.decorView).filterIsInstance<EditText>().first { it.hint.toString().contains("микрофон") }
        edit.setText("We will pay Monday")
        first.pause().stop().destroy()
        val second = Robolectric.buildActivity(MainActivity::class.java).setup()
        assertEquals("We will pay Monday",descendants(second.get().window.decorView).filterIsInstance<EditText>().first { it.hint.toString().contains("микрофон") }.text.toString())
        val voice = descendants(second.get().window.decorView).filterIsInstance<Button>().first { it.text.toString().contains("Что ответить") }
        assertTrue(voice.isEnabled)
        second.pause().stop().destroy()
    }
}
