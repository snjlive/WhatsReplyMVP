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
    private lateinit var inboxPage: LinearLayout
    private lateinit var chatPage: LinearLayout
    private lateinit var chatRows: LinearLayout
    private lateinit var inboxSummary: TextView
    private lateinit var search: EditText
    private var onlyWaiting = false
    private var showingChat = false
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
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.rgb(245,247,249)) }
        root.setOnApplyWindowInsetsListener { v, i -> v.setPadding(dp(16),i.systemWindowInsetTop,dp(16),i.systemWindowInsetBottom); i }
        val header = LinearLayout(this).apply { gravity = android.view.Gravity.CENTER_VERTICAL }
        header.addView(label("WhatsReply",26f).apply { setTypeface(null,Typeface.BOLD) },LinearLayout.LayoutParams(0,-2,1f))
        header.addView(button("⚙") { configureAi() }.apply { contentDescription = "Настройки подключения AI" },LinearLayout.LayoutParams(dp(56),-2))
        root.addView(header)
        permissions = button("Подключить уведомления WhatsApp") { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        root.addView(permissions)
        inboxPage = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        inboxSummary = label("Чаты",18f); inboxPage.addView(inboxSummary)
        search = EditText(this).apply {
            hint = "Поиск по имени или сообщению"; isSingleLine = true; textSize = 16f
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            setPadding(dp(16),dp(12),dp(16),dp(12)); background = surface(Color.WHITE)
        }
        inboxPage.addView(search)
        val filters = LinearLayout(this)
        filters.addView(button("Все чаты") { onlyWaiting = false; renderInbox() },LinearLayout.LayoutParams(0,-2,1f))
        filters.addView(button("Ждут ответа") { onlyWaiting = true; renderInbox() },LinearLayout.LayoutParams(0,-2,1f))
        inboxPage.addView(filters)
        val inboxScroll = ScrollView(this)
        chatRows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        inboxScroll.addView(chatRows); inboxPage.addView(inboxScroll,LinearLayout.LayoutParams(-1,0,1f))
        inboxPage.addView(label("Здесь сообщения, полученные из уведомлений после подключения приложения.",13f))
        root.addView(inboxPage,LinearLayout.LayoutParams(-1,0,1f))
        chatPage = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        val chatHeader = LinearLayout(this).apply { gravity = android.view.Gravity.CENTER_VERTICAL }
        chatHeader.addView(button("‹ Чаты") { showInbox() },LinearLayout.LayoutParams(dp(100),-2))
        contact = label("",19f).apply { setTypeface(null,Typeface.BOLD) }
        chatHeader.addView(contact,LinearLayout.LayoutParams(0,-2,1f)); chatPage.addView(chatHeader)
        val scroll = ScrollView(this)
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        history = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        incoming = label("",16f).apply { setPadding(dp(14),dp(12),dp(14),dp(12)); background = surface(Color.WHITE) }
        body.addView(label("Последнее входящее",13f)); body.addView(incoming)
        body.addView(label("Переписка · последние сообщения сверху",13f)); body.addView(history)
        scroll.addView(body); chatPage.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        chatPage.addView(label("Ваш ответ · текст можно редактировать",13f))
        draft = EditText(this).apply {
            hint = "Нажмите микрофон и скажите, что ответить"; textSize = 17f; minLines = 2; maxLines = 4
            setPadding(dp(14),dp(10),dp(14),dp(10)); background = surface(Color.WHITE)
            gravity = android.view.Gravity.TOP; inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }
        chatPage.addView(draft)
        status = label("",13f); status.setTextColor(teal); chatPage.addView(status)
        val audio = LinearLayout(this)
        listen = button("▶ Слушать") { speak() }
        revise = button("🎙 Правка") { startSpeech(true) }
        audio.addView(listen,LinearLayout.LayoutParams(0,-2,1f)); audio.addView(revise,LinearLayout.LayoutParams(0,-2,1f))
        audio.addView(button("⌨ Текст") { textInstruction(draft.text.isNotBlank()) }.apply { contentDescription = "Инструкция текстом" },LinearLayout.LayoutParams(0,-2,1f))
        chatPage.addView(audio)
        val actions = LinearLayout(this)
        voice = button("🎙 Что ответить?") { startSpeech(false) }.apply {
            backgroundTintList = android.content.res.ColorStateList.valueOf(teal); setTextColor(Color.WHITE); minHeight = dp(60)
        }
        send = button("Отправить ➤") { transmit() }.apply { minHeight = dp(60) }
        actions.addView(voice,LinearLayout.LayoutParams(0,-2,1.2f)); actions.addView(send,LinearLayout.LayoutParams(0,-2,1f)); chatPage.addView(actions)
        root.addView(chatPage,LinearLayout.LayoutParams(-1,0,1f))
        setContentView(root)
        if(android.os.Build.VERSION.SDK_INT >= 33) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT) {
                if(showingChat) showInbox() else finish()
            }
        }
        search.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { renderInbox() }
            override fun afterTextChanged(s: android.text.Editable?) = Unit
        })
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
        showingChat = savedInstanceState?.getBoolean("showingChat") ?: false
        inboxPage.visibility = if(showingChat) View.GONE else View.VISIBLE
        chatPage.visibility = if(showingChat) View.VISIBLE else View.GONE
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
        out.putBoolean("showingChat",showingChat)
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
        contact.text = (chat?.name ?: "Ожидание сообщения")
        val last = chat?.let { store.latest(it.id,"incoming") }
        incoming.text = last?.let { "${time(it.time)}\n${it.text}" } ?: "Получите новое сообщение в WhatsApp Business"
        if(showingChat && last != null) getPreferences(MODE_PRIVATE).edit().putLong("read:${chat?.id}",last.id).apply()
        if(draft.text.isNullOrBlank()) source = last?.id ?: 0L
        if(last != null && source != last.id && draft.text.isNotBlank()) status.text = "Пришло новое сообщение — подготовьте ответ заново"
        history.removeAllViews()
        chat?.let { store.entries(it.id,80).filter { entry -> entry.id != last?.id }.reversed().forEach { entry ->
            val kind = when(entry.kind) { "incoming" -> "Входящее"; "draft" -> "Черновик"; else -> "Передано в WhatsApp" }
            val bubble = label("$kind • ${time(entry.time)}\n${entry.text}",16f).apply {
                setPadding(dp(12),dp(10),dp(12),dp(10))
                background = surface(if(entry.kind == "incoming") Color.WHITE else Color.rgb(222,242,236))
                setTextIsSelectable(true)
            }
            history.addView(bubble,LinearLayout.LayoutParams(-1,-2).apply {
                setMargins(if(entry.kind == "incoming") 0 else dp(24),dp(4),if(entry.kind == "incoming") dp(24) else 0,dp(4))
            })
        } }
        renderInbox()
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
        send.text = "Отправить ➤"
        send.contentDescription = "Отправить ответ ${chat?.name.orEmpty()}"
        revise.visibility = if(text.isBlank()) View.GONE else View.VISIBLE
        listen.visibility = if(text.isBlank()) View.GONE else View.VISIBLE
        voice.text = if(busy) "Готовлю ответ…" else "🎙 Что ответить?"
    }
    private fun surface(color: Int) = android.graphics.drawable.GradientDrawable().apply {
        setColor(color); cornerRadius = dp(14).toFloat()
    }
    private fun waiting(chat: Chat): Boolean {
        val last = store.latest(chat.id,"incoming") ?: return false
        return store.latest(chat.id,"sent")?.source != last.id
    }
    private fun renderInbox() {
        if(!::chatRows.isInitialized) return
        val all = store.chats().sortedByDescending { store.latest(it.id,"incoming")?.time ?: 0L }
        val query = search.text.toString().trim()
        val chats = all.filter { chat ->
            (!onlyWaiting || waiting(chat)) && (query.isBlank() || chat.name.contains(query,true) || store.matchesMessage(chat.id,query))
        }
        inboxSummary.text = if(onlyWaiting) "Ждут ответа · ${chats.size}" else "Все чаты · ${all.size}"
        chatRows.removeAllViews()
        if(chats.isEmpty()) chatRows.addView(label(if(all.isEmpty()) "Пока нет сообщений\nПолучите сообщение в WhatsApp Business — чат появится здесь." else "Ничего не найдено",17f))
        val prefs = getPreferences(MODE_PRIVATE)
        chats.forEach { chat ->
            val last = store.latest(chat.id,"incoming")
            val savedDraft = store.latest(chat.id,"draft")
            val sent = store.latest(chat.id,"sent")
            val hasDraft = savedDraft != null && savedDraft.source == last?.id && (sent == null || savedDraft.id > sent.id)
            val unread = last != null && last.id > prefs.getLong("read:${chat.id}",0)
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; setPadding(dp(14),dp(12),dp(14),dp(12)); background = surface(Color.WHITE)
                isClickable = true; isFocusable = true
                setOnClickListener {
                    if(busy) return@setOnClickListener
                    persistDraft(); selected = chat; restoreDraft(); showingChat = true
                    last?.let { prefs.edit().putLong("read:${chat.id}",it.id).apply() }
                    inboxPage.visibility = View.GONE; chatPage.visibility = View.VISIBLE
                    status.text = if(hasDraft) "Черновик готов" else "Скажите по-русски, что ответить"
                    render()
                }
            }
            card.addView(label((if(unread) "● " else "") + chat.name,18f).apply { setTypeface(null,Typeface.BOLD) })
            card.addView(label(last?.text.orEmpty(),16f).apply { maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END })
            card.addView(label("${last?.let { time(it.time) }.orEmpty()} · ${if(hasDraft) "Черновик" else if(waiting(chat)) "Ждёт ответа" else "Ответ передан"}",13f).apply { setTextColor(teal) })
            chatRows.addView(card,LinearLayout.LayoutParams(-1,-2).apply { setMargins(0,dp(5),0,dp(5)) })
        }
    }
    private fun showInbox() {
        persistDraft(); tts?.stop(); showingChat = false
        chatPage.visibility = View.GONE; inboxPage.visibility = View.VISIBLE; renderInbox()
    }
    @android.annotation.SuppressLint("GestureBackNavigation")
    @Deprecated("Legacy devices use this; Android 13+ uses OnBackInvokedDispatcher")
    override fun onBackPressed() { if(showingChat) showInbox() else super.onBackPressed() }
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
