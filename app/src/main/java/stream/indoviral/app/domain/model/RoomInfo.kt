package stream.indoviral.app.domain.model

data class RoomInfo(
    val code: String,
    val videoTitle: String,
    val videoId: Int,
    val userCount: Int,
    val hostName: String,
    val isPlaying: Boolean
)
