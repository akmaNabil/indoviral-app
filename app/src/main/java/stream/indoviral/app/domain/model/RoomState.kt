package stream.indoviral.app.domain.model

data class RoomState(
    val code: String,
    val videoId: Int,
    val videoTitle: String,
    val videoFilename: String,
    val videoHls: Int,
    val currentTime: Double,
    val isPlaying: Boolean,
    val users: List<RoomUser>,
    val chat: List<ChatMessage>,
    val isHost: Boolean
)
