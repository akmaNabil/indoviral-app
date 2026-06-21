package stream.indoviral.app.navigation

sealed class Screen(val route: String) {
    data object Auth : Screen("auth")
    data object Lobby : Screen("lobby")
    data object Rooms : Screen("rooms")
    data object Room : Screen("room?createRoomVideoId={createRoomVideoId}&joinRoomCode={joinRoomCode}") {
        fun createRoute(
            createRoomVideoId: Int? = null,
            joinRoomCode: String? = null
        ): String {
            val params = mutableListOf<String>()
            if (createRoomVideoId != null) params.add("createRoomVideoId=$createRoomVideoId")
            if (joinRoomCode != null) params.add("joinRoomCode=$joinRoomCode")
            return if (params.isEmpty()) "room" else "room?${params.joinToString("&")}"
        }
    }
}
