package com.snjlive.whatsreply

import android.app.Activity
import android.app.AlertDialog
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.view.View
import android.widget.*
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private lateinit var store: HistoryStore
    private lateinit var settings: AiSettings
    private lateinit var contact: TextView
    private lateinit var incoming: TextView
    private lateinit var status: TextView
    private lateinit var draft: EditText
    private lateinit var voice: Button
    private lateinit var revise: Button
    private lateinit var send: Button
    private lateinit var listen: Button
    private lateinit var history: LinearLayout
    private lateinit var permissions: Button
    private var selected: Chat? = null
    private var source = 0L
    private var busy = false
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var speechChat: Chat? = null
    private var speechSource = 0L
    private var speechRevision = false
    private var speechDraft = ""
    private val executor = Executors.newSingleThreadExecutor()
    private val teal = Color.rgb(0,105,92)
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    private fun label(value: String, size: Float = 16f) = TextView(this).apply {
        text = value; textSize = size; setTextColor(Color.rgb(24,44,43)); setPadding(0,dp(6),0,dp(6))
    }
    private fun button(value: String, action: () -> Unit) = Button(this).apply {
        text = value; isAllCaps = false; minHeight = dp(54); textSize = 16f; setOnClickListener { action() }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = HistoryStore(this); settings = AiSettings(this)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.rgb(244,248,247)) }
        root.setOnApplyWindowInsetsListener { v, i -> v.setPadding(dp(16),i.systemWindowInsetTop,dp(16),i.systemWindowInsetBottom); i }
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL }
        header.addView(label("WhatsReply 0.2",24f).apply { setTypeface(null,Typeface.BOLD) },LinearLayout.LayoutParams(0,-2,1f))
        header.addView(button("⚙") { configureAi() }.apply { contentDescription = "Настроить AI" },LinearLayout.LayoutParams(dp(56),-2))
        root.addView(header)
        permissions = button("Разрешить уведомления WhatsApp") { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        root.addView(permissions)
        contact = label("Ожидание сообщения",22f).apply { setTypeface(null,Typeface.BOLD); setOnClickListener { chooseContact() } }
        root.addView(contact)
        val scroll = ScrollView(this)
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        incoming = label("Получите новое сообщение в WhatsApp Business",18f)
        body.addView(incoming)
        body.addView(label("Готовый ответ • можно изменить текст"))
        draft = EditText(this).apply {
            hint = "Нажмите «Что ответить?» и скажите по-русски"; textSize = 18f; minLines = 3; maxLines = 8
            gravity = android.view.Gravity.TOP; inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }
        body.addView(draft)
        val audio = LinearLayout(this)
        listen = button("▶ Прослушать") { speak() }
        revise = button("🎙 Исправить") { startSpeech(true) }
        audio.addView(listen,LinearLayout.LayoutParams(0,-2,1f)); audio.addView(revise,LinearLayout.LayoutParams(0,-2,1f)); body.addView(audio)
        body.addView(button("Написать инструкцию") { textInstruction(false) })
        body.addView(label("История контакта",18f).apply { setTypeface(null,Typeface.BOLD) })
        history = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }; body.addView(history)
        scroll.addView(body); root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        status = label("История хранится на телефоне",14f); root.addView(status)
        voice = button("🎙 Что ответить?") { startSpeech(false) }.apply {
            setBackgroundTintList(android.content.res.ColorStateList.valueOf(teal)); setTextColor(Color.WHITE); minHeight = dp(64)
        }
        root.addView(voice)
        send = button("Отправить") { transmit() }.apply { minHeight = dp(58) }
        root.addView(send)
        setContentView(root)
        tts = TextToSpeech(this) { result -> runOnUiThread { ttsReady = result == TextToSpeech.SUCCESS; renderButtons() } }
        val savedId = savedInstanceState?.getString("chat") ?: getPreferences(MODE_PRIVATE).getString("selected",null)
        selected = store.chats().firstOrNull { it.id == savedId } ?: store.chats().firstOrNull()
        restoreDraft()
        if(savedInstanceState != null && selected?.id == savedId) {
            draft.setText(savedInstanceState.getString("draft",draft.text.toString())); source = savedInstanceState.getLong("source",source)
            speechChat = store.chats().firstOrNull { it.id == savedInstanceState.getString("speechChat") }
            speechSource = savedInstanceState.getLong("speechSource"); speechRevision = savedInstanceState.getBoolean("speechRevision")
            speechDraft = savedInstanceState.getString("speechDraft","")
        }
        draft.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { renderButtons() }
            override fun afterTextChanged(s: android.text.Editable?) = Unit
        })
    }
    override fun onResume() {
        super.onResume(); ReplyState.changed = { runOnUiThread { render() } }; render()
    }
    override fun onPause() { persistDraft(); ReplyState.changed = null; tts?.stop(); super.onPause() }
    override fun onDestroy() { tts?.shutdown(); executor.shutdown(); store.close(); super.onDestroy() }
    override fun onSaveInstanceState(out: Bundle) {
        out.putString("chat",selected?.id); out.putString("draft",draft.text.toString()); out.putLong("source",source)
        out.putString("speechChat",speechChat?.id); out.putLong("speechSource",speechSource)
        out.putBoolean("speechRevision",speechRevision); out.putString("speechDraft",speechDraft)
        super.onSaveInstanceState(out)
    }
    private fun hasAccess(): Boolean {
        val component = ComponentName(this,ReplyListener::class.java)
        return if(android.os.Build.VERSION.SDK_INT >= 27) getSystemService(NotificationManager::class.java).isNotificationListenerAccessGranted(component)
            else Settings.Secure.getString(contentResolver,"enabled_notification_listeners")?.split(':')?.any { ComponentName.unflattenFromString(it) == component } == true
    }
    private fun persistDraft() {
        if(busy) return
        val chat = selected ?: return
        val text = draft.text.toString().trim()
        if(text.isNotBlank() && source != 0L && store.latest(chat.id,"draft")?.let { it.text == text && it.source == source } != true)
            store.add(chat,"draft",text,source=source)
        getPreferences(MODE_PRIVATE).edit().putString("selected",chat.id).apply()
    }
    private fun restoreDraft() {
        val chat = selected
        val last = chat?.let { store.latest(it.id,"draft") }
        val sent = chat?.let { store.latest(it.id,"sent") }
        val usable = last?.takeIf { sent == null || it.id > sent.id }
        source = usable?.source ?: chat?.let { store.latest(it.id,"incoming")?.id } ?: 0L
        draft.setText(usable?.text.orEmpty())
    }
    private fun render() {
        if(selected == null) { selected = store.chats().firstOrNull(); restoreDraft() }
        permissions.visibility = if(hasAccess()) View.GONE else View.VISIBLE
        val chat = selected
        contact.text = (chat?.name ?: "Ожидание сообщения") + if(chat != null) " ▾" else ""
        val last = chat?.let { store.latest(it.id,"incoming") }
        incoming.text = last?.let { "Входящее • ${time(it.time)}\n${it.text}" } ?: "Получите новое сообщение в WhatsApp Business"
        if(draft.text.isNullOrBlank()) source = last?.id ?: 0L
        if(last != null && source != last.id && draft.text.isNotBlank()) status.text = "Пришло новое сообщение — подготовьте ответ заново"
        history.removeAllViews()
        chat?.let { store.entries(it.id,30).forEach { entry ->
            val kind = when(entry.kind) { "incoming" -> "Входящее"; "draft" -> "Черновик"; else -> "Передано в WhatsApp" }
            history.addView(label("$kind • ${time(entry.time)}\n${entry.text}",14f).apply {
                setTextColor(if(entry.kind == "incoming") Color.DKGRAY else teal)
            })
        } }
        renderButtons()
    }
    private fun renderButtons() {
        if(!::voice.isInitialized) return
        val chat = selected; val text = draft.text.toString().trim()
        voice.isEnabled = !busy && chat != null
        revise.isEnabled = !busy && text.isNotBlank()
        listen.isEnabled = ttsReady && text.isNotBlank()
        val target = chat?.let { ReplyState.targets[it.id] }
        send.isEnabled = !busy && text.isNotBlank() && text.length <= 12000 && hasAccess() && ReplyState.connected && target?.incoming == source && chat?.let { store.latest(it.id,"incoming")?.id } == source
        send.text = if(chat != null) "Отправить → ${chat.name}" else "Отправить"
        voice.text = if(busy) "Готовлю ответ…" else "🎙 Что ответить?"
    }
    private fun chooseContact() {
        if(busy) return
        val chats = store.chats()
        AlertDialog.Builder(this).setTitle("Контакты").setItems(chats.map { it.name }.toTypedArray()) { _, index ->
            persistDraft(); selected = chats[index]; restoreDraft(); render()
        }.setNegativeButton("Закрыть",null).show()
    }
    private fun startSpeech(revision: Boolean) {
        if(busy) return
        val chat = selected ?: return
        val last = store.latest(chat.id,"incoming") ?: return
        if(revision && source != last.id) { status.text = "Сначала подготовьте ответ на новое сообщение"; return }
        persistDraft(); tts?.stop()
        speechChat = chat; speechSource = last.id; speechRevision = revision; speechDraft = if(revision) draft.text.toString() else ""
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE,"ru-RU")
            putExtra(RecognizerIntent.EXTRA_PROMPT,if(revision) "Что изменить в ответе?" else "Что ответить ${chat.name}?")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,1)
        }
        try { startActivityForResult(intent,101) }
        catch(_: ActivityNotFoundException) { status.text = "Распознавание речи недоступно. Введите инструкцию текстом"; textInstruction(revision) }
    }
    @Deprecated("Platform activity result API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode == 101 && resultCode == RESULT_OK) {
            val instruction = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull().orEmpty()
            val chat = speechChat ?: return
            if(instruction.isNotBlank()) generate(chat,speechSource,instruction,speechDraft)
        }
    }
    private fun textInstruction(revision: Boolean) {
        val chat = selected ?: return
        val last = store.latest(chat.id,"incoming") ?: return
        if(busy) return
        if(revision && source != last.id) { status.text = "Сначала подготовьте ответ на новое сообщение"; return }
        val existing = if(revision) draft.text.toString() else ""
        val input = EditText(this).apply { hint = "Например: поблагодари и скажи, что оплатим в понедельник"; minLines = 3; maxLength(4000) }
        AlertDialog.Builder(this).setTitle(if(revision) "Что изменить?" else "Что ответить?").setView(input)
            .setPositiveButton("Подготовить") { _, _ -> if(input.text.isNotBlank()) generate(chat,last.id,input.text.toString(),existing) }
            .setNegativeButton("Отмена",null).show()
    }
    private fun EditText.maxLength(length: Int) { filters = arrayOf(android.text.InputFilter.LengthFilter(length)) }
    private fun generate(chat: Chat, incomingId: Long, instruction: String, existing: String) {
        if(busy) return
        if(store.latest(chat.id,"incoming")?.id != incomingId) { status.text = "Пришло новое сообщение. Повторите инструкцию"; return }
        val endpoint = settings.endpoint; val token = settings.token
        if(endpoint.isBlank() || token.isBlank()) { status.text = "AI ещё не подключён. Инструкция сохранена для повторного запуска"; pendingInstruction = Triple(chat,incomingId,instruction to existing); configureAi(); return }
        val payload = JSONObject().put("contact",chat.name.take(200)).put("history",store.context(chat.id))
            .put("instruction",instruction.take(4000)).put("draft",existing.take(12000))
        busy = true; status.text = "Инструкция: $instruction"; renderButtons()
        val appContext = applicationContext
        executor.execute {
            val result = runCatching { AiClient.generate(endpoint,token,payload) }
            result.getOrNull()?.let { text -> HistoryStore(appContext).use { it.add(chat,"draft",text,source=incomingId) } }
            runOnUiThread {
                if(isDestroyed) return@runOnUiThread
                busy = false
                result.fold(onSuccess = { text ->
                    if(selected?.id == chat.id) { source = incomingId; draft.setText(text) }
                    status.text = "Ответ готов. Прослушайте или отправьте"
                },onFailure = { error -> status.text = when(error) {
                    is java.net.SocketTimeoutException -> "AI не успел ответить. Повторите попытку"
                    is java.io.IOException -> "Нет связи с AI. Проверьте интернет и повторите попытку"
                    else -> error.message ?: "Не удалось подготовить ответ"
                } })
                render()
            }
        }
    }
    private var pendingInstruction: Triple<Chat,Long,Pair<String,String>>? = null
    private fun configureAi() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20),dp(8),dp(20),0) }
        box.addView(label("AI получает инструкцию и последние сообщения только выбранного контакта. Ключ AI хранится на сервере. Голос распознаёт сервис телефона.",14f))
        val endpoint = EditText(this).apply { hint = "HTTPS-адрес сервера /draft"; setText(settings.endpoint); inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI; isSingleLine = true }
        val token = EditText(this).apply { hint = "Код доступа (не API-ключ)"; setText(settings.token); inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD; isSingleLine = true }
        box.addView(endpoint); box.addView(token)
        val dialog = AlertDialog.Builder(this).setTitle("Подключение AI").setView(box).setPositiveButton("Сохранить",null).setNegativeButton("Закрыть",null).create()
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val address = endpoint.text.toString().trim(); val access = token.text.toString().trim()
            val valid = runCatching { java.net.URL(address).let { it.protocol == "https" && it.host.isNotBlank() && it.userInfo == null && it.query == null && it.ref == null } }.getOrDefault(false)
            if(!valid || access.length < 32 || access.startsWith("sk-") || access.contains('\n') || access.contains('\r')) {
                token.error = "Нужны HTTPS-адрес и код сервера от 32 символов. Не API-ключ"; return@setOnClickListener
            }
            try { settings.token = access; settings.endpoint = address } catch(_: Exception) { status.text = "Не удалось сохранить подключение"; return@setOnClickListener }
            dialog.dismiss(); status.text = "Подключение сохранено"
            pendingInstruction?.let { pendingInstruction = null; generate(it.first,it.second,it.third.first,it.third.second) }
        } }; dialog.show()
    }
    private fun speak() {
        val text = draft.text.toString().trim(); if(text.isBlank() || !ttsReady) return
        val language = when {
            text.any { it in '\u0E00'..'\u0E7F' } -> Locale.forLanguageTag("th-TH")
            text.any { it in '\u0400'..'\u04FF' } -> Locale.forLanguageTag("ru-RU")
            else -> Locale.ENGLISH
        }
        if((tts?.setLanguage(language) ?: TextToSpeech.LANG_NOT_SUPPORTED) < 0) {
            status.text = "Установите голос для ${language.displayLanguage} в настройках телефона"; return
        }
        text.chunked(TextToSpeech.getMaxSpeechInputLength() - 100).forEachIndexed { index, part ->
            val queue = if(index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            if(tts?.speak(part,queue,null,"draft-$index") == TextToSpeech.ERROR) {
                status.text = "Не удалось воспроизвести ответ"; return
            }
        }
    }
    private fun transmit() {
        val chat = selected ?: return; val text = draft.text.toString().trim()
        if(text.isBlank() || text.length > 12000) return
        persistDraft(); tts?.stop()
        status.text = sendReply(this,store,chat,source,text)
        if(store.latest(chat.id,"sent")?.let { it.text == text && it.source == source } == true) draft.setText("")
        render()
    }
    private fun time(ms: Long) = SimpleDateFormat("dd.MM HH:mm",Locale.getDefault()).format(java.util.Date(ms))
}
