package stream.indoviral.app.domain.model

data class ChatMessage(
    val userId: Int,
    val username: String,
    val avatar: String?,
    val message: String,
    val time: Long = System.currentTimeMillis()
)
