package com.snjlive.whatsreply

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject

data class Chat(val id: String, val name: String)
data class Entry(val id: Long, val chat: String, val kind: String, val text: String, val time: Long, val source: Long)

class HistoryStore(context: Context) : SQLiteOpenHelper(context.applicationContext, "history.db", null, 1), java.io.Closeable {
    override fun close() { super.close() }
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE chats(id TEXT PRIMARY KEY, name TEXT NOT NULL, updated INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE entries(id INTEGER PRIMARY KEY AUTOINCREMENT, chat TEXT NOT NULL, kind TEXT NOT NULL, body TEXT NOT NULL, time INTEGER NOT NULL, source INTEGER NOT NULL DEFAULT 0, fingerprint TEXT UNIQUE)")
        db.execSQL("CREATE INDEX chat_entries ON entries(chat,id)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    fun chats(): List<Chat> = readableDatabase.rawQuery("SELECT id,name FROM chats ORDER BY updated DESC", null).use { c ->
        buildList { while(c.moveToNext()) add(Chat(c.getString(0),c.getString(1))) }
    }
    fun add(chat: Chat, kind: String, text: String, time: Long = System.currentTimeMillis(), source: Long = 0, fingerprint: String? = null): Long {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val values = ContentValues().apply {
                put("chat",chat.id); put("kind",kind); put("body",text); put("time",time); put("source",source)
                if(fingerprint != null) put("fingerprint",fingerprint)
            }
            val id = db.insertWithOnConflict("entries",null,values,SQLiteDatabase.CONFLICT_IGNORE)
            if(id != -1L) db.insertWithOnConflict("chats",null,ContentValues().apply {
                put("id",chat.id); put("name",chat.name); put("updated",System.currentTimeMillis())
            },SQLiteDatabase.CONFLICT_REPLACE)
            db.setTransactionSuccessful()
            return id
        } finally { db.endTransaction() }
    }
    fun entries(chat: String, limit: Int = 80): List<Entry> = readableDatabase.rawQuery(
        "SELECT id,chat,kind,body,time,source FROM entries WHERE chat=? ORDER BY id DESC LIMIT ?",arrayOf(chat,limit.toString())
    ).use { c -> buildList { while(c.moveToNext()) add(Entry(c.getLong(0),c.getString(1),c.getString(2),c.getString(3),c.getLong(4),c.getLong(5))) }.reversed() }
    fun latest(chat: String, kind: String): Entry? = readableDatabase.rawQuery(
        "SELECT id,chat,kind,body,time,source FROM entries WHERE chat=? AND kind=? ORDER BY time DESC,id DESC LIMIT 1",arrayOf(chat,kind)
    ).use { c -> if(c.moveToFirst()) Entry(c.getLong(0),c.getString(1),c.getString(2),c.getString(3),c.getLong(4),c.getLong(5)) else null }
    fun context(chat: String): JSONArray = JSONArray().apply {
        readableDatabase.rawQuery("SELECT kind,body,time FROM entries WHERE chat=? AND kind IN ('incoming','sent') ORDER BY time DESC,id DESC LIMIT 16",arrayOf(chat)).use { c ->
            val items = mutableListOf<JSONObject>()
            while(c.moveToNext()) items.add(JSONObject().put("kind",c.getString(0)).put("text",c.getString(1).take(4000)).put("time",c.getLong(2)))
            items.reversed().forEach { put(it) }
        }
    }
}
