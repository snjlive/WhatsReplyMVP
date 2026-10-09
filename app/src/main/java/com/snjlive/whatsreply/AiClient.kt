package com.snjlive.whatsreply

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.net.URL
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.net.ssl.HttpsURLConnection

class AiSettings(context: Context) {
    private val prefs = context.getSharedPreferences("ai",Context.MODE_PRIVATE)
    var endpoint: String
        get() = prefs.getString("endpoint","").orEmpty()
        set(value) { prefs.edit().putString("endpoint",value).apply() }
    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey("whatsreply-access",null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("whatsreply-access",KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    var token: String
        get() = try {
            val raw = prefs.getString("token",null)
            if(raw == null) "" else {
                val obj = JSONObject(raw)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,Base64.decode(obj.getString("iv"),Base64.NO_WRAP)))
                String(cipher.doFinal(Base64.decode(obj.getString("data"),Base64.NO_WRAP)),Charsets.UTF_8)
            }
        } catch(_: Exception) { "" }
        set(value) {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE,key())
            val obj = JSONObject().put("iv",Base64.encodeToString(cipher.iv,Base64.NO_WRAP))
                .put("data",Base64.encodeToString(cipher.doFinal(value.toByteArray()),Base64.NO_WRAP))
            prefs.edit().putString("token",obj.toString()).apply()
        }
}
object AiClient {
    fun generate(endpoint: String, token: String, payload: JSONObject): String {
        val url = URL(endpoint)
        require(url.protocol == "https" && url.userInfo == null && url.query == null && url.ref == null) { "Нужен HTTPS-адрес AI-сервера" }
        require(token.isNotBlank() && !token.contains('\n') && !token.contains('\r')) { "Добавьте код доступа к AI" }
        val conn = url.openConnection() as HttpsURLConnection
        try {
            conn.requestMethod = "POST"; conn.instanceFollowRedirects = false
            conn.connectTimeout = 15000; conn.readTimeout = 65000; conn.doOutput = true
            conn.setRequestProperty("Content-Type","application/json; charset=utf-8")
            conn.setRequestProperty("Authorization","Bearer $token")
            conn.outputStream.use { it.write(payload.toString().toByteArray()) }
            val status = conn.responseCode
            if(status != 200) throw IllegalStateException(when(status) {
                401,403 -> "Проверьте код доступа к AI"
                429 -> "Лимит AI исчерпан. Попробуйте позже"
                503 -> "AI-сервер ещё не настроен"
                else -> "AI временно недоступен ($status). Черновик сохранён"
            })
            val bytes = conn.inputStream.use { stream ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(4096)
                while(output.size() <= 65536) {
                    val count = stream.read(buffer)
                    if(count < 0) break
                    output.write(buffer,0,count)
                }
                output.toByteArray()
            }
            require(bytes.size <= 65536) { "Ответ AI слишком большой" }
            val obj = JSONObject(String(bytes,Charsets.UTF_8))
            val draft = obj.getString("draft").trim()
            require(draft.isNotBlank() && draft.length <= 12000) { "AI не вернул готовый ответ" }
            return draft
        } finally { conn.disconnect() }
    }
}
