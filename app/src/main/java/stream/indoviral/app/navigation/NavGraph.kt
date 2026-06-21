package stream.indoviral.app.navigation

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import stream.indoviral.app.ui.auth.AuthScreen
import stream.indoviral.app.ui.lobby.LobbyScreen
import stream.indoviral.app.ui.lobby.LobbyViewModel
import stream.indoviral.app.ui.room.RoomScreen
import stream.indoviral.app.ui.rooms.RoomsScreen
import stream.indoviral.app.ui.rooms.RoomsViewModel

@Composable
fun IndoViralNavGraph(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Screen.Auth.route
    ) {
        composable(Screen.Auth.route) {
            AuthScreen(
                onLoggedIn = {
                    navController.navigate(Screen.Lobby.route) {
                        popUpTo(Screen.Auth.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Lobby.route) {
            LobbyScreen(
                onLogout = {
                    navController.navigate(Screen.Auth.route) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onNavigateToRoom = { videoId, joinCode ->
                    val route = if (videoId != null) {
                        "room?createRoomVideoId=$videoId"
                    } else if (joinCode != null) {
                        "room?joinRoomCode=$joinCode"
                    } else {
                        "room"
                    }
                    navController.navigate(route)
                },
                onNavigateToRooms = {
                    navController.navigate(Screen.Rooms.route)
                }
            )
        }

        composable(Screen.Rooms.route) {
            RoomsScreen(
                onBack = { navController.popBackStack() },
                onNavigateToRoom = { joinCode ->
                    navController.navigate("room?joinRoomCode=$joinCode")
                }
            )
        }

        composable(
            route = "room?createRoomVideoId={createRoomVideoId}&joinRoomCode={joinRoomCode}",
            arguments = listOf(
                navArgument("createRoomVideoId") {
                    type = NavType.IntType
                    defaultValue = -1
                },
                navArgument("joinRoomCode") {
                    type = NavType.StringType
                    defaultValue = ""
                }
            )
        ) { backStackEntry ->
            val videoIdArg = backStackEntry.arguments?.getInt("createRoomVideoId") ?: -1
            val codeArg = backStackEntry.arguments?.getString("joinRoomCode") ?: ""

            RoomScreen(
                createRoomVideoId = if (videoIdArg > 0) videoIdArg else null,
                joinRoomCode = if (codeArg.isNotEmpty() && codeArg.length == 4) codeArg else null,
                onExit = {
                    navController.navigate(Screen.Lobby.route) {
                        popUpTo(Screen.Lobby.route) { inclusive = true }
                    }
                }
            )
        }
    }
}
