package stream.indoviral.app.domain.model

data class AuthResponse(
    val user: User?,
    val token: String?,
    val error: String?
)
