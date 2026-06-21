package stream.indoviral.app.ui.room

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import stream.indoviral.app.data.local.TokenManager
import stream.indoviral.app.data.remote.SocketEvent
import stream.indoviral.app.data.remote.SocketManager
import stream.indoviral.app.data.repository.AuthRepository
import stream.indoviral.app.data.repository.VideoRepository
import stream.indoviral.app.domain.model.ChatMessage
import stream.indoviral.app.domain.model.RoomState
import stream.indoviral.app.domain.model.RoomUser
import stream.indoviral.app.domain.model.Video
import stream.indoviral.app.di.BaseUrl
import javax.inject.Inject

data class RoomUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val roomState: RoomState? = null,
    val currentUserId: Int = 0,
    val isHost: Boolean = false,
    val videoUrl: String? = null,
    val showInfoOverlay: Boolean = true,
    val chatMessages: List<ChatMessage> = emptyList(),
    val isConnected: Boolean = false,
    val toastMessage: String? = null,
    val shouldExit: Boolean = false
)

@HiltViewModel
class RoomViewModel @Inject constructor(
    private val tokenManager: TokenManager,
    private val authRepository: AuthRepository,
    private val videoRepository: VideoRepository,
    private val socketManager: SocketManager,
    @BaseUrl private val baseUrl: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(RoomUiState())
    val uiState: StateFlow<RoomUiState> = _uiState.asStateFlow()

    private var syncJob: Job? = null
    private var rateResetJob: Job? = null
    private var reconnectAttempted = false

    // These will be called by the Screen composable
    var playerCurrentTime: () -> Double = { 0.0 }
    var playerIsPlaying: () -> Boolean = { false }
    var playerSetCurrentTime: (Double) -> Unit = {}
    var playerPlay: () -> Unit = {}
    var playerPause: () -> Unit = {}
    var playerSetPlaybackRate: (Float) -> Unit = {}
    var playerGetPlaybackRate: () -> Float = { 1f }
    var playerDuration: () -> Long = { 0 }

    fun init(createRoomVideoId: Int?, joinRoomCode: String?) {
        viewModelScope.launch {
            val token = tokenManager.token.first() ?: run {
                _uiState.update { it.copy(error = "Not authenticated", shouldExit = true) }
                return@launch
            }
            val user = authRepository.refreshUser().getOrNull() ?: run {
                _uiState.update { it.copy(error = "Gagal memuat user", shouldExit = true) }
                return@launch
            }
            _uiState.update { it.copy(currentUserId = user.id) }

            socketManager.connect(baseUrl, token)
            collectSocketEvents()

            if (createRoomVideoId != null) {
                initAsHost(createRoomVideoId, token)
            } else if (joinRoomCode != null) {
                initAsGuest(joinRoomCode)
            }
        }
    }

    private suspend fun initAsHost(videoId: Int, token: String) {
        val result = videoRepository.getVideos()
        result.onFailure { e ->
            _uiState.update { it.copy(error = e.message, shouldExit = true) }
            return
        }
        val videos = result.getOrThrow()
        val video = videos.find { it.id == videoId }
        if (video == null) {
            _uiState.update { it.copy(error = "Video tidak ditemukan", shouldExit = true) }
            return
        }

        socketManager.emitWithAck("create-room", arrayOf(
            JSONObject().apply { put("videoId", videoId) }
        )) { response ->
            viewModelScope.launch {
                if (response == null || response.has("error")) {
                    val msg = response?.optString("error", "Gagal membuat room") ?: "Gagal membuat room"
                    _uiState.update { it.copy(error = msg, shouldExit = true) }
                    return@launch
                }
                val roomObj = response.optJSONObject("room")
                if (roomObj == null) {
                    _uiState.update { it.copy(error = "Gagal membuat room", shouldExit = true) }
                    return@launch
                }
                val room = parseRoomState(roomObj, userId = _uiState.value.currentUserId)
                if (room != null) {
                    setupRoom(room, video, isHost = true)
                } else {
                    _uiState.update { it.copy(error = "Gagal membuat room", shouldExit = true) }
                }
            }
        }
    }

    private fun initAsGuest(code: String) {
        socketManager.emitWithAck("join-room", arrayOf(
            JSONObject().apply { put("code", code) }
        )) { response ->
            viewModelScope.launch {
                if (response == null || response.has("error")) {
                    val msg = response?.optString("error", "Gagal bergabung room") ?: "Gagal bergabung room"
                    _uiState.update { it.copy(error = msg, shouldExit = true) }
                    return@launch
                }
                val roomObj = response.optJSONObject("room")
                if (roomObj == null) {
                    _uiState.update { it.copy(error = "Gagal bergabung room", shouldExit = true) }
                    return@launch
                }
                val room = parseRoomState(roomObj, userId = _uiState.value.currentUserId)
                if (room != null) {
                    val video = Video(
                        id = room.videoId,
                        title = room.videoTitle,
                        filename = room.videoFilename,
                        duration = 0f,
                        hls = room.videoHls,
                        size = 0
                    )
                    setupRoom(room, video, isHost = false)
                } else {
                    _uiState.update { it.copy(error = "Gagal bergabung room", shouldExit = true) }
                }
            }
        }
    }

    private fun setupRoom(room: RoomState, video: Video, isHost: Boolean) {
        val videoUrl = buildVideoUrl(video)
        val showInfo = isHost

        _uiState.update {
            it.copy(
                roomState = room,
                isHost = isHost,
                videoUrl = videoUrl,
                showInfoOverlay = showInfo,
                isLoading = false,
                chatMessages = room.chat
            )
        }

        if (isHost) {
            startSyncInterval()
        }
    }

    private fun buildVideoUrl(video: Video): String {
        return if (video.hls == 1) {
            "${baseUrl}uploads/videos/hls_${video.id}/output.m3u8"
        } else {
            "${baseUrl}uploads/videos/${video.filename}"
        }
    }

    fun hideInfoOverlay() {
        _uiState.update { it.copy(showInfoOverlay = false) }
    }

    fun sendChat(message: String) {
        if (message.isBlank()) return
        socketManager.emitWithAck("chat", arrayOf(
            JSONObject().apply { put("message", message.trim()) }
        )) { response ->
            if (response != null && response.has("error")) {
                val msg = response.optString("error")
                viewModelScope.launch {
                    _uiState.update { it.copy(toastMessage = msg) }
                }
            }
        }
    }

    fun leaveRoom() {
        socketManager.emit("leave-room")
        socketManager.disconnect()
        _uiState.update { it.copy(shouldExit = true) }
    }

    fun clearToast() {
        _uiState.update { it.copy(toastMessage = null) }
    }

    fun resolveAvatarUrl(path: String?): String? {
        if (path.isNullOrBlank()) return null
        return if (path.startsWith("http")) path else baseUrl.trimEnd('/') + "/" + path.trimStart('/')
    }

    // Host sync
    private fun startSyncInterval() {
        syncJob?.cancel()
        syncJob = viewModelScope.launch {
            while (true) {
                delay(3000)
                if (!_uiState.value.isHost) break
                if (!socketManager.isConnected()) continue
                val ct = playerCurrentTime()
                val ip = playerIsPlaying()
                socketManager.emit("sync", JSONObject().apply {
                    put("currentTime", ct)
                    put("isPlaying", ip)
                    put("ts", System.currentTimeMillis())
                })
            }
        }
    }

    fun emitPlay() {
        if (!_uiState.value.isHost) return
        socketManager.emit("play", JSONObject().apply {
            put("currentTime", playerCurrentTime())
            put("ts", System.currentTimeMillis())
        })
    }

    fun emitPause() {
        if (!_uiState.value.isHost) return
        socketManager.emit("pause", JSONObject().apply {
            put("currentTime", playerCurrentTime())
            put("ts", System.currentTimeMillis())
        })
    }

    fun emitSeek() {
        if (!_uiState.value.isHost) return
        socketManager.emit("seek", JSONObject().apply {
            put("currentTime", playerCurrentTime())
            put("ts", System.currentTimeMillis())
        })
    }

    private fun computeTargetTime(currentTime: Double, serverTs: Long): Double {
        return currentTime + maxOf(0.0, (System.currentTimeMillis() - serverTs) / 1000.0)
    }

    // Guest sync handlers
    fun handleSync(currentTime: Double, isPlaying: Boolean?, serverTs: Long) {
        if (_uiState.value.isHost) return
        val target = computeTargetTime(currentTime, serverTs)
        val myTime = playerCurrentTime()
        val diff = target - myTime

        if (isPlaying != null) {
            if (isPlaying && !playerIsPlaying()) playerPlay()
            else if (!isPlaying && playerIsPlaying()) playerPause()
        }

        rateResetJob?.cancel()
        if (kotlin.math.abs(diff) > 1.5) {
            playerSetCurrentTime(target)
            playerSetPlaybackRate(1f)
        } else if (kotlin.math.abs(diff) > 0.3) {
            val rate = if (diff > 0) 1.04f else 0.96f
            playerSetPlaybackRate(rate)
            rateResetJob = viewModelScope.launch {
                delay(2000)
                playerSetPlaybackRate(1f)
            }
        } else {
            playerSetPlaybackRate(1f)
        }
    }

    fun handlePlay(currentTime: Double) {
        if (_uiState.value.isHost) return
        val diff = kotlin.math.abs(currentTime - playerCurrentTime())
        if (diff > 1.5) playerSetCurrentTime(currentTime)
        playerPlay()
    }

    fun handlePause(currentTime: Double) {
        if (_uiState.value.isHost) return
        val diff = kotlin.math.abs(currentTime - playerCurrentTime())
        if (diff > 1.5) playerSetCurrentTime(currentTime)
        playerPause()
    }

    fun handleSeek(currentTime: Double) {
        if (_uiState.value.isHost) return
        playerSetCurrentTime(currentTime)
    }

    // Socket events collection
    private fun collectSocketEvents() {
        viewModelScope.launch {
            socketManager.events.collect { event ->
                when (event) {
                    is SocketEvent.Sync -> handleSync(event.currentTime, event.isPlaying, event.serverTs)
                    is SocketEvent.Play -> handlePlay(event.currentTime)
                    is SocketEvent.Pause -> handlePause(event.currentTime)
                    is SocketEvent.Seek -> handleSeek(event.currentTime)
                    is SocketEvent.ChatReceived -> {
                        _uiState.update { state ->
                            val msgs = state.chatMessages.toMutableList()
                            msgs.add(event.message)
                            state.copy(chatMessages = msgs)
                        }
                    }
                    is SocketEvent.UserJoined -> {
                        _uiState.update { state ->
                            val currentRoom = state.roomState ?: return@update state
                            state.copy(roomState = currentRoom.copy(users = event.users))
                        }
                    }
                    is SocketEvent.UserRejoined -> {
                        _uiState.update { state ->
                            val currentRoom = state.roomState ?: return@update state
                            state.copy(roomState = currentRoom.copy(users = event.users))
                        }
                    }
                    is SocketEvent.UserLeft -> {
                        _uiState.update { state ->
                            val currentRoom = state.roomState ?: return@update state
                            state.copy(roomState = currentRoom.copy(users = event.users))
                        }
                    }
                    is SocketEvent.UserDisconnected -> {
                        _uiState.update { state ->
                            val currentRoom = state.roomState ?: return@update state
                            state.copy(roomState = currentRoom.copy(users = event.users))
                        }
                    }
                    is SocketEvent.HostPromoted -> {
                        val myId = _uiState.value.currentUserId
                        if (event.newHostId == myId) {
                            _uiState.update { state ->
                                state.copy(
                                    isHost = true,
                                    toastMessage = "Kamu sekarang jadi host!",
                                    roomState = state.roomState?.copy(users = event.users)
                                )
                            }
                            startSyncInterval()
                        } else {
                            _uiState.update { state ->
                                state.copy(
                                    roomState = state.roomState?.copy(users = event.users)
                                )
                            }
                        }
                    }
                    is SocketEvent.RequestSync -> {
                        if (_uiState.value.isHost && socketManager.isConnected()) {
                            socketManager.emit("sync", JSONObject().apply {
                                put("currentTime", playerCurrentTime())
                                put("isPlaying", playerIsPlaying())
                                put("ts", System.currentTimeMillis())
                            })
                        }
                    }
                    is SocketEvent.ConnectionError -> {
                        _uiState.update { it.copy(isConnected = false) }
                    }
                }
            }
        }
    }

    private fun parseRoomState(json: JSONObject, userId: Int): RoomState? {
        return try {
            val usersArr = json.optJSONArray("users") ?: JSONArray()
            val users = mutableListOf<RoomUser>()
            for (i in 0 until usersArr.length()) {
                val u = usersArr.getJSONObject(i)
                users.add(
                    RoomUser(
                        id = u.getInt("id"),
                        username = u.getString("username"),
                        avatar = u.optString("avatar", null),
                        isHost = u.optBoolean("isHost", false)
                    )
                )
            }
            val chatArr = json.optJSONArray("chat") ?: JSONArray()
            val chat = mutableListOf<ChatMessage>()
            for (i in 0 until chatArr.length()) {
                val c = chatArr.getJSONObject(i)
                chat.add(
                    ChatMessage(
                        userId = c.getInt("userId"),
                        username = c.getString("username"),
                        avatar = c.optString("avatar", null),
                        message = c.getString("message"),
                        time = c.optLong("time", System.currentTimeMillis())
                    )
                )
            }

            RoomState(
                code = json.getString("code"),
                videoId = json.getInt("videoId"),
                videoTitle = json.getString("videoTitle"),
                videoFilename = json.getString("videoFilename"),
                videoHls = json.optInt("videoHls", 0),
                currentTime = json.optDouble("currentTime", 0.0),
                isPlaying = json.optBoolean("isPlaying", false),
                users = users,
                chat = chat,
                isHost = json.optBoolean("isHost", false)
            )
        } catch (e: Exception) {
            null
        }
    }

    override fun onCleared() {
        super.onCleared()
        syncJob?.cancel()
        rateResetJob?.cancel()
        socketManager.disconnect()
    }
}
