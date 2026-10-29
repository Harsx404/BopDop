package com.dopdop.bopdop.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

/** The feed consumes this contract; a future sync repository can keep local reads and add remote sync. */
interface ShortsHistoryRepository {
    val state: StateFlow<ShortsHistoryState>
    fun record(watch: ShortWatch)
    fun toggleLike(item: FeedShort)
}

/** Private, durable SQLite tables. All reads/writes are serialized off the UI thread. */
object ShortsStore : ShortsHistoryRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pending = Channel<(SQLiteDatabase) -> Unit>(Channel.UNLIMITED)
    private val mutable = MutableStateFlow(ShortsHistoryState())
    override val state = mutable.asStateFlow()
    private var started = false

    @Synchronized fun init(context: Context) {
        if (started) return
        started = true
        val helper = Database(context.applicationContext)
        scope.launch {
            try {
                val db = helper.writableDatabase
                publish(db)
                for (action in pending) try {
                    action(db)
                } catch (e: Exception) {
                    Log.e("ShortsStore", "Couldn't save Shorts history", e)
                    mutable.value = mutable.value.copy(error = "Couldn't save your Shorts preferences on this device.")
                }
            } catch (e: Exception) {
                Log.e("ShortsStore", "Couldn't open Shorts history", e)
                mutable.value = mutable.value.copy(error = "Your saved Shorts are temporarily unavailable.")
            }
        }
    }

    override fun record(watch: ShortWatch) {
        // Failed loads and accidental passes teach nothing; "Not interested" or using the song always does.
        if (watch.watchedMs < 250 && !watch.dismissed && !watch.acted) return
        pending.trySend { db ->
            db.beginTransaction()
            try {
                check(db.insertWithOnConflict("watches", null, ContentValues().apply {
                    put("event_id", watch.eventId); put("clip_id", watch.item.clip.id); put("at", watch.at); put("payload", watch.toJson().toString())
                }, SQLiteDatabase.CONFLICT_REPLACE) != -1L)
                db.execSQL("DELETE FROM watches WHERE id NOT IN (SELECT id FROM watches ORDER BY id DESC LIMIT ${ShortsTaste.MAX_WATCHES})")
                db.setTransactionSuccessful()
            } finally { db.endTransaction() }
            // Avoid rereading/parsing thousands of rows on every swipe.
            mutable.value = mutable.value.copy(watches = (mutable.value.watches.filterNot { it.eventId == watch.eventId } + watch).takeLast(ShortsTaste.MAX_WATCHES),
                revision = mutable.value.revision + 1, error = null)
        }
    }

    override fun toggleLike(item: FeedShort) {
        pending.trySend { db ->
            val previous = mutable.value
            val liked = item.clip.id !in previous.likedIds
            val entry = LikedShort(item, System.currentTimeMillis())
            if (liked) db.insertWithOnConflict("likes", null, ContentValues().apply {
                put("clip_id", item.clip.id); put("at", entry.at); put("payload", entry.toJson().toString())
            }, SQLiteDatabase.CONFLICT_REPLACE).also { check(it != -1L) }
            else db.delete("likes", "clip_id = ?", arrayOf(item.clip.id))
            mutable.value = previous.copy(liked = if (liked) listOf(entry) + previous.liked else previous.liked.filterNot { it.item.clip.id == item.clip.id },
                revision = previous.revision + 1, error = null)
        }
    }

    fun toJson() = state.value.toJson()

    fun restore(json: JSONObject) {
        val restored = ShortsHistoryState.fromJson(json)
        pending.trySend { db ->
            db.beginTransaction()
            try {
                db.delete("watches", null, null); db.delete("likes", null, null)
                restored.watches.forEach { w -> db.insertOrThrow("watches", null, ContentValues().apply {
                    put("event_id", w.eventId); put("clip_id", w.item.clip.id); put("at", w.at); put("payload", w.toJson().toString())
                }) }
                restored.liked.forEach { l -> db.insertOrThrow("likes", null, ContentValues().apply {
                    put("clip_id", l.item.clip.id); put("at", l.at); put("payload", l.toJson().toString())
                }) }
                db.setTransactionSuccessful()
            } finally { db.endTransaction() }
            publish(db)
        }
    }

    private fun publish(db: SQLiteDatabase) {
        fun <T> read(sql: String, parse: (JSONObject) -> T): List<T> = db.rawQuery(sql, null).use { c ->
            buildList { while (c.moveToNext()) runCatching { parse(JSONObject(c.getString(0))) }.getOrNull()?.let(::add) }
        }
        mutable.value = ShortsHistoryState(
            watches = read("SELECT payload FROM (SELECT id, payload FROM watches ORDER BY id DESC LIMIT ${ShortsTaste.MAX_WATCHES}) ORDER BY id", ShortWatch::fromJson),
            liked = read("SELECT payload FROM likes ORDER BY at DESC", LikedShort::fromJson),
            revision = mutable.value.revision + 1,
        )
    }

    private class Database(context: Context) : SQLiteOpenHelper(context, "shorts.db", null, 1) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE watches (id INTEGER PRIMARY KEY AUTOINCREMENT, event_id TEXT UNIQUE NOT NULL, clip_id TEXT NOT NULL, at INTEGER NOT NULL, payload TEXT NOT NULL)")
            db.execSQL("CREATE INDEX watches_clip_at ON watches(clip_id, at)")
            db.execSQL("CREATE TABLE likes (clip_id TEXT PRIMARY KEY NOT NULL, at INTEGER NOT NULL, payload TEXT NOT NULL)")
        }
        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }
}
