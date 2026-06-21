package stream.indoviral.app.domain.model

data class RoomUser(
    val id: Int,
    val username: String,
    val avatar: String?,
    val isHost: Boolean
)
