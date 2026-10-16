package com.dopdop.bopdop.data.together

import android.content.Context
import android.util.Log
import com.dopdop.bopdop.data.model.Source
import com.dopdop.bopdop.data.model.Track
import com.dopdop.bopdop.data.net.Http
import com.dopdop.bopdop.playback.PlayerConnection
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom

enum class RoomStatus { IDLE, CONNECTING, WAITING, LIVE, RECONNECTING }

data class Member(val id: String, val name: String, val host: Boolean)

data class RoomState(
    val status: RoomStatus = RoomStatus.IDLE,
    val code: String = "",
    val error: String = "",
    val meId: String = "",
    val hostId: String = "",
    /** "host": only the host drives playback; "all": anyone can. */
    val mode: String = "host",
    val members: List<Member> = emptyList(),
    val pending: List<Member> = emptyList(),
    val reaction: Pair<String, String>? = null,
) {
    val live get() = status == RoomStatus.LIVE
    val isHost get() = meId.isNotEmpty() && meId == hostId
    val canControl get() = live && (isHost || mode == "all")
}

/**
 * Listen Together, on the same relay and wire format as the BopDop web app
 * (workers/party): nobody streams audio to anyone — every phone plays its own copy and the
 * room only carries the queue and the playback position, stamped with server time.
 */
object Together {
    private const val TAG = "Together"
    const val RELAY = "wss://dopdop-party.harshkandoo.workers.dev"
    private const val WINDOW_BEFORE = 4
    private const val WINDOW_AFTER = 70
    private const val HEARTBEAT_MS = 4000L
    private const val ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"

    private val scope = MainScope()
    private val _state = MutableStateFlow(RoomState())
    val state: StateFlow<RoomState> = _state.asStateFlow()

    private var prefs: android.content.SharedPreferences? = null
    private var ws: WebSocket? = null
    private var intent: Intent? = null
    private var retry = 0
    private var offset = 0L
    private var bestRtt = Long.MAX_VALUE
    private var jobs = listOf<Job>()
    /** Ignore our own player changes until then — they came from the room. */
    private var quietUntil = 0L
    private var sentQueue = ""
    private var pendingSync: JSONObject? = null

    /** No read timeout on an idle room; OkHttp keeps the socket alive with its own pings. */
    private val socketClient by lazy { Http.client.newBuilder().readTimeout(0, java.util.concurrent.TimeUnit.MILLISECONDS).pingInterval(20, java.util.concurrent.TimeUnit.SECONDS).build() }

    private data class Intent(val code: String, val name: String, val create: Boolean)

    fun init(context: Context) {
        if (prefs == null) prefs = context.getSharedPreferences("together", Context.MODE_PRIVATE)
    }

    var name: String
        get() = prefs?.getString("name", null).orEmpty()
        set(v) { prefs?.edit()?.putString("name", v.trim().take(24))?.apply() }

    private val key: String get() {
        val p = prefs ?: return randomHex(24)
        return p.getString("key", null)?.takeIf { it.length >= 24 } ?: randomHex(24).also { p.edit().putString("key", it).apply() }
    }

    fun normalize(code: String) = code.uppercase().filter { it.isLetterOrDigit() }.take(8)

    fun create(name: String) = start(Intent(newCode(), name.ifBlank { "Guest" }, create = true))
    fun join(code: String, name: String) = start(Intent(normalize(code), name.ifBlank { "Guest" }, create = false))

    fun leave() {
        intent = null
        jobs.forEach { it.cancel() }; jobs = emptyList()
        ws?.close(1000, "bye"); ws = null
        _state.value = RoomState()
    }

    fun end() { send(JSONObject().put("t", "end")); leave() }
    fun approve(id: String, ok: Boolean) = send(JSONObject().put("t", "approve").put("id", id).put("ok", ok))
    fun setMode(all: Boolean) = send(JSONObject().put("t", "mode").put("mode", if (all) "all" else "host"))
    fun react(emoji: String) = send(JSONObject().put("t", "react").put("emoji", emoji))

    private fun start(i: Intent) {
        leave()
        name = i.name
        intent = i
        retry = 0
        _state.value = RoomState(status = RoomStatus.CONNECTING, code = i.code)
        connect()
        jobs = listOf(watchPlayer(), heartbeat(), watchSeeks())
    }

    private fun connect() {
        val i = intent ?: return
        val req = Request.Builder().url("$RELAY/room/${i.code}").build()
        ws = socketClient.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                bestRtt = Long.MAX_VALUE
                ping()
                val hello = JSONObject().put("t", "hello").put("name", i.name).put("key", key).put("code", i.code)
                if (i.create) hello.put("create", true).put("kind", "music").put("mode", "host").put("approval", false)
                webSocket.send(hello.toString())
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                val m = runCatching { JSONObject(text) }.getOrNull() ?: return
                scope.launch { handle(m) }
            }
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { webSocket.close(code, null) }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { scope.launch { dropped(code, reason) } }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { scope.launch { dropped(0, t.message.orEmpty()) } }
        })
    }

    private fun dropped(code: Int, reason: String) {
        val i = intent ?: return
        // Codes the relay uses to say "don't come back": not found, denied, kicked, full, taken.
        if (code in setOf(4001, 4003, 4004, 4008, 4009) || code == 1000) {
            val msg = when (code) { 4001 -> "You were removed from the room"; 4003 -> "The host didn't let you in"; 4004 -> "Room not found"; 4008 -> "This room is full"; 4009 -> "That room code is taken"; else -> "The room ended" }
            leave(); _state.value = RoomState(error = msg); return
        }
        _state.update { it.copy(status = RoomStatus.RECONNECTING) }
        val wait = (1000L shl retry.coerceAtMost(5)).coerceAtMost(30_000L)
        retry++
        Log.w(TAG, "relay dropped ($code $reason), retrying in $wait ms")
        scope.launch { delay(wait); if (intent == i) connect() }
    }

    private fun send(m: JSONObject) { ws?.send(m.toString()) }
    private fun ping() = send(JSONObject().put("t", "ping").put("c", System.currentTimeMillis()))
    /** Server time, estimated from the fastest ping round-trip. */
    private fun now() = System.currentTimeMillis() + offset

    private fun handle(m: JSONObject) {
        when (m.optString("t")) {
            "pong" -> {
                val c = m.optLong("c"); val s = m.optLong("s"); val rtt = System.currentTimeMillis() - c
                if (rtt in 0 until bestRtt) { bestRtt = rtt; offset = s - (c + rtt / 2) }
            }
            "welcome" -> {
                retry = 0
                val you = m.optJSONObject("you"); val room = m.optJSONObject("room")
                _state.update {
                    it.copy(status = RoomStatus.LIVE, error = "", meId = you?.optString("id").orEmpty(),
                        hostId = room?.optString("hostId").orEmpty(), mode = room?.optString("mode") ?: "host", members = members(m.optJSONArray("members")))
                }
                if (!_state.value.canControl) {
                    applyQueue(m.optJSONArray("items"), m.optInt("index"))
                    m.optJSONObject("sync")?.let(::applySync)
                } else { sentQueue = ""; pushQueue(); pushSync() }
                repeat(3) { k -> scope.launch { delay(600L * (k + 1)); ping() } }
            }
            "waiting" -> _state.update { it.copy(status = RoomStatus.WAITING) }
            "members" -> {
                val before = _state.value.members.size
                _state.update { it.copy(members = members(m.optJSONArray("members")), hostId = m.optString("hostId", it.hostId), pending = it.pending.filter { p -> it.members.none { x -> x.id == p.id } }) }
                // Someone new: the controller brings them up to date.
                if (_state.value.canControl && _state.value.members.size > before) { sentQueue = ""; pushQueue(); pushSync() }
            }
            "room" -> m.optJSONObject("room")?.let { r -> _state.update { it.copy(hostId = r.optString("hostId", it.hostId), mode = r.optString("mode", it.mode)) } }
            "pending" -> _state.update { it.copy(pending = it.pending + Member(m.optString("id"), m.optString("name"), false)) }
            "queue" -> if (!_state.value.canControl || m.optString("by") != _state.value.meId) applyQueue(m.optJSONArray("items"), m.optInt("index"))
            "sync" -> if (m.optString("by") != _state.value.meId) applySync(m)
            "react" -> _state.update { it.copy(reaction = m.optString("name") to m.optString("emoji")) }
            "error" -> _state.update { it.copy(error = m.optString("msg")) }
            "closed", "kicked", "denied" -> { leave(); _state.value = RoomState(error = if (m.optString("t") == "closed") "The host ended the room" else "You left the room") }
        }
    }

    private fun members(a: JSONArray?) = (0 until (a?.length() ?: 0)).mapNotNull { a!!.optJSONObject(it) }
        .map { Member(it.optString("id"), it.optString("name"), it.optBoolean("host")) }

    // --- inbound ---------------------------------------------------------------------------

    private fun applyQueue(items: JSONArray?, index: Int) {
        val list = (0 until (items?.length() ?: 0)).mapNotNull { fromWire(items!!.optJSONObject(it)) }
        if (list.isEmpty()) return
        val i = index.coerceIn(0, list.lastIndex)
        sentQueue = list.joinToString { it.id }
        val queue = PlayerConnection.queue.value
        quietUntil = System.currentTimeMillis() + 1500
        if (queue.map { it.id } == list.map { it.id }) PlayerConnection.skipTo(i)
        else PlayerConnection.play(list, i, "Together · ${_state.value.code}")
        pendingSync?.let { scope.launch { delay(400); applySync(it) } }
    }

    private fun applySync(m: JSONObject) {
        val id = m.optString("id")
        val queue = PlayerConnection.queue.value
        val index = queue.indexOfFirst { it.id == id || it.id == "sv:$id" }
        if (index < 0) { pendingSync = m; return }
        pendingSync = null
        val playing = m.optBoolean("playing")
        // Position is in seconds on the wire; a playing track has moved on since the server stamped it.
        val elapsed = if (playing) (now() - m.optLong("at", now())).coerceIn(0, 10_000) else 0
        val ms = (m.optDouble("pos", 0.0) * 1000).toLong() + elapsed
        quietUntil = System.currentTimeMillis() + 1500
        PlayerConnection.syncTo(index, ms, playing)
    }

    // --- outbound --------------------------------------------------------------------------

    private fun watchPlayer() = scope.launch {
        combine(PlayerConnection.current, PlayerConnection.isPlaying, PlayerConnection.queue, PlayerConnection.index) { c, p, q, i -> listOf(c?.id, p, q.size, i) }
            .distinctUntilChanged().collectLatest {
                delay(250)
                if (!_state.value.canControl || System.currentTimeMillis() < quietUntil) return@collectLatest
                pushQueue(); pushSync()
            }
    }

    private fun watchSeeks() = scope.launch {
        PlayerConnection.seeks.collect { if (_state.value.canControl && System.currentTimeMillis() >= quietUntil) { delay(150); pushSync() } }
    }

    private fun heartbeat() = scope.launch {
        while (true) {
            delay(HEARTBEAT_MS)
            if (_state.value.live) ping()
            if (_state.value.canControl && PlayerConnection.isPlaying.value) pushSync(hb = true)
        }
    }

    private fun pushQueue() {
        val queue = PlayerConnection.queue.value.filter { it.source != Source.LOCAL }
        val current = PlayerConnection.current.value ?: return
        val at = queue.indexOfFirst { it.id == current.id }.takeIf { it >= 0 } ?: return
        val from = (at - WINDOW_BEFORE).coerceAtLeast(0)
        val window = queue.subList(from, minOf(queue.size, at + WINDOW_AFTER))
        val sig = window.joinToString { it.id }
        if (sig == sentQueue) return
        sentQueue = sig
        send(JSONObject().put("t", "queue").put("items", JSONArray().also { a -> window.forEach { a.put(toWire(it)) } }).put("index", at - from))
    }

    private fun pushSync(hb: Boolean = false) {
        val current = PlayerConnection.current.value ?: return
        send(JSONObject().put("t", "sync").put("id", current.id).put("pos", PlayerConnection.position / 1000.0)
            .put("playing", PlayerConnection.isPlaying.value).put("rate", 1).put("hb", hb))
    }

    // --- wire format (shared with the web app) ---------------------------------------------

    internal fun toWire(t: Track): JSONObject = JSONObject().put("id", t.id).put("title", t.title).put("artist", t.artist)
        .put("album", t.album).put("duration", t.durationSec).put("thumb", t.artwork).apply {
            if (t.source == Source.SAAVN) { put("source", "saavn"); t.streamUrl?.let { put("url", it) } }
            else { put("source", "ytm"); put("videoId", t.rawId) }
        }

    /** Peers are untrusted: rebuild a track from known-safe fields only. */
    internal fun fromWire(o: JSONObject?): Track? {
        o ?: return null
        val title = o.optString("title").take(200).ifBlank { return null }
        val thumb = o.optString("thumb").takeIf { it.startsWith("https://") }.orEmpty()
        val duration = o.optDouble("duration", 0.0).toInt().coerceAtLeast(0)
        val artist = o.optString("artist").take(200)
        val album = o.optString("album").take(200)
        return if (o.optString("source") == "ytm") {
            val vid = o.optString("videoId")
            if (!Regex("^[\\w-]{11}$").matches(vid) || o.optString("id") != "yt:$vid") null
            else Track("yt:$vid", title, artist, album = album, durationSec = duration, artwork = thumb)
        } else {
            val url = o.optString("url")
            if (!Regex("^https://[^/]*saavncdn\\.com/").containsMatchIn(url)) return null
            val id = o.optString("id").take(100).ifBlank { return null }
            Track(if (id.startsWith("sv:")) id else "sv:$id", title, artist, album = album, durationSec = duration, artwork = thumb, streamUrl = url)
        }
    }

    private fun newCode(): String {
        val r = SecureRandom()
        return (1..6).map { ALPHABET[r.nextInt(ALPHABET.length)] }.joinToString("")
    }

    private fun randomHex(bytes: Int): String {
        val b = ByteArray(bytes).also { SecureRandom().nextBytes(it) }
        return b.joinToString("") { "%02x".format(it) }
    }
}
