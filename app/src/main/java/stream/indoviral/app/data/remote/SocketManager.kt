package stream.indoviral.app.data.remote

import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import org.json.JSONArray
import org.json.JSONObject
import stream.indoviral.app.domain.model.ChatMessage
import stream.indoviral.app.domain.model.RoomUser
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

sealed class SocketEvent {
    data class Sync(val currentTime: Double, val isPlaying: Boolean?, val serverTs: Long) : SocketEvent()
    data class Play(val currentTime: Double) : SocketEvent()
    data class Pause(val currentTime: Double) : SocketEvent()
    data class Seek(val currentTime: Double) : SocketEvent()
    data class ChatReceived(val message: ChatMessage) : SocketEvent()
    data class UserJoined(val users: List<RoomUser>, val joinedUsername: String?) : SocketEvent()
    data class UserRejoined(val users: List<RoomUser>, val rejoinedUsername: String?) : SocketEvent()
    data class UserLeft(val users: List<RoomUser>, val leftUsername: String?) : SocketEvent()
    data class UserDisconnected(val users: List<RoomUser>, val username: String?) : SocketEvent()
    data class HostPromoted(
        val newHostId: Int,
        val newHostName: String,
        val currentTime: Double,
        val isPlaying: Boolean,
        val users: List<RoomUser>
    ) : SocketEvent()
    data object RequestSync : SocketEvent()
    data class ConnectionError(val message: String) : SocketEvent()
}

@Singleton
class SocketManager @Inject constructor() {

    var socket: Socket? = null
        private set

    private val eventChannel = Channel<SocketEvent>(Channel.BUFFERED)

    val events: Flow<SocketEvent> = eventChannel.receiveAsFlow()

    fun connect(url: String, token: String) {
        disconnect()

        val options = IO.Options().apply {
            auth = mutableMapOf("token" to token)
            reconnection = true
            reconnectionAttempts = Int.MAX_VALUE
            reconnectionDelay = 1000
            reconnectionDelayMax = 5000
            timeout = 15000
            forceNew = true
        }

        socket = IO.socket(url, options).apply {
            on(Socket.EVENT_CONNECT) {
                Timber.d("Socket connected")
            }
            on(Socket.EVENT_DISCONNECT) {
                Timber.d("Socket disconnected")
                eventChannel.trySend(SocketEvent.ConnectionError("Koneksi terputus"))
            }
            on(Socket.EVENT_CONNECT_ERROR) {
                Timber.d("Socket connect error: ${it[0]}")
            }

            on("sync") { args ->
                if (args.isNotEmpty()) parseSyncPayload(args[0])?.let {
                    eventChannel.trySend(it)
                }
            }
            on("play") { args ->
                if (args.isNotEmpty()) parsePlayPauseSeekPayload(args[0])?.let { ct ->
                    eventChannel.trySend(SocketEvent.Play(ct))
                }
            }
            on("pause") { args ->
                if (args.isNotEmpty()) parsePlayPauseSeekPayload(args[0])?.let { ct ->
                    eventChannel.trySend(SocketEvent.Pause(ct))
                }
            }
            on("seek") { args ->
                if (args.isNotEmpty()) parsePlayPauseSeekPayload(args[0])?.let { ct ->
                    eventChannel.trySend(SocketEvent.Seek(ct))
                }
            }
            on("chat-message") { args ->
                if (args.isNotEmpty()) parseChatMessage(args[0])?.let {
                    eventChannel.trySend(SocketEvent.ChatReceived(it))
                }
            }
            on("user-joined") { args ->
                if (args.isNotEmpty()) {
                    val data = args[0] as? JSONObject ?: return@on
                    val users = parseUsers(data.optJSONArray("users"))
                    val joined = data.optJSONObject("joined")
                    val name = joined?.optString("username")
                    eventChannel.trySend(SocketEvent.UserJoined(users, name))
                }
            }
            on("user-rejoined") { args ->
                if (args.isNotEmpty()) {
                    val data = args[0] as? JSONObject ?: return@on
                    val users = parseUsers(data.optJSONArray("users"))
                    val rejoined = data.optJSONObject("rejoined")
                    val name = rejoined?.optString("username")
                    eventChannel.trySend(SocketEvent.UserRejoined(users, name))
                }
            }
            on("user-left") { args ->
                if (args.isNotEmpty()) {
                    val data = args[0] as? JSONObject ?: return@on
                    val users = parseUsers(data.optJSONArray("users"))
                    val left = data.optJSONObject("left")
                    val name = left?.optString("username")
                    eventChannel.trySend(SocketEvent.UserLeft(users, name))
                }
            }
            on("user-disconnected") { args ->
                if (args.isNotEmpty()) {
                    val data = args[0] as? JSONObject ?: return@on
                    val users = parseUsers(data.optJSONArray("users"))
                    val user = data.optJSONObject("user")
                    val name = user?.optString("username")
                    eventChannel.trySend(SocketEvent.UserDisconnected(users, name))
                }
            }
            on("host-promoted") { args ->
                if (args.isNotEmpty()) {
                    val data = args[0] as? JSONObject ?: return@on
                    eventChannel.trySend(
                        SocketEvent.HostPromoted(
                            newHostId = data.optInt("newHostId"),
                            newHostName = data.optString("newHostName", ""),
                            currentTime = data.optDouble("currentTime", 0.0),
                            isPlaying = data.optBoolean("isPlaying", false),
                            users = parseUsers(data.optJSONArray("users"))
                        )
                    )
                }
            }
            on("request-sync") {
                eventChannel.trySend(SocketEvent.RequestSync)
            }

            connect()
        }
    }

    fun emit(event: String, vararg args: Any) {
        socket?.emit(event, *args)
    }

    fun emitWithAck(event: String, args: Array<out Any>, callback: (JSONObject?) -> Unit) {
        socket?.emit(event, args) { response ->
            val obj = when (response) {
                is JSONObject -> response
                is JSONArray -> if (response.length() > 0) response.optJSONObject(0) else null
                else -> null
            }
            callback(obj)
        }
    }

    fun disconnect() {
        socket?.disconnect()
        socket?.off()
        socket = null
    }

    fun isConnected(): Boolean = socket?.connected() == true

    private fun parseSyncPayload(data: Any): SocketEvent.Sync? {
        val obj = data as? JSONObject ?: return null
        val currentTime = obj.optDouble("currentTime", -1.0)
        if (currentTime < 0) return null
        val isPlaying = if (obj.has("isPlaying")) obj.optBoolean("isPlaying") else null
        val serverTs = obj.optLong("serverTs", System.currentTimeMillis())
        return SocketEvent.Sync(currentTime, isPlaying, serverTs)
    }

    private fun parsePlayPauseSeekPayload(data: Any): Double? {
        val obj = data as? JSONObject ?: return null
        val currentTime = obj.optDouble("currentTime", -1.0)
        return if (currentTime >= 0) currentTime else null
    }

    private fun parseChatMessage(data: Any): ChatMessage? {
        val obj = data as? JSONObject ?: return null
        return ChatMessage(
            userId = obj.optInt("userId"),
            username = obj.optString("username", ""),
            avatar = obj.optString("avatar", null),
            message = obj.optString("message", ""),
            time = obj.optLong("time", System.currentTimeMillis())
        )
    }

    private fun parseUsers(arr: JSONArray?): List<RoomUser> {
        if (arr == null) return emptyList()
        val users = mutableListOf<RoomUser>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            users.add(
                RoomUser(
                    id = obj.optInt("id"),
                    username = obj.optString("username", ""),
                    avatar = obj.optString("avatar", null),
                    isHost = obj.optBoolean("isHost", false)
                )
            )
        }
        return users
    }
}
