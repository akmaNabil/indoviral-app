package stream.indoviral.app.domain.model

data class Video(
    val id: Int,
    val title: String,
    val filename: String,
    val duration: Float,
    val hls: Int,
    val size: Long,
    val createdAt: String? = null
)
