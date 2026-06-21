package stream.indoviral.app.ui.lobby

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import stream.indoviral.app.domain.model.Video

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LobbyScreen(
    onLogout: () -> Unit,
    onNavigateToRoom: (createVideoId: Int?, joinCode: String?) -> Unit,
    onNavigateToRooms: () -> Unit,
    viewModel: LobbyViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(state.createRoomVideoId) {
        state.createRoomVideoId?.let { videoId ->
            onNavigateToRoom(videoId, null)
            viewModel.clearNavigation()
        }
    }
    LaunchedEffect(state.joinRoomCode) {
        state.joinRoomCode?.let { code ->
            onNavigateToRoom(null, code)
            viewModel.clearNavigation()
        }
    }
    LaunchedEffect(state.error) {
        if (state.error == "__logout__") onLogout()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("IndoViral", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                },
                actions = {
                    TextButton(onClick = onNavigateToRooms) {
                        Text("Room Online", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    state.user?.let { user ->
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(user.avatar)
                                .crossfade(true)
                                .build(),
                            contentDescription = "Avatar",
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    TextButton(onClick = { viewModel.logout() }) {
                        Text(
                            "Keluar",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 12.sp
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            // Action buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { /* Upload handled by web */ },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("+ Upload Video")
                }
                OutlinedButton(
                    onClick = { viewModel.showJoinDialog() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Join Room")
                }
            }

            // Section title
            Text(
                "Video Tersedia",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                "Pilih video untuk ditonton bareng",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            if (state.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else if (state.videos.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Belum ada video",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 160.dp),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.videos, key = { it.id }) { video ->
                        VideoCard(
                            video = video,
                            onClick = { viewModel.createRoom(video.id) },
                            onInfoClick = { viewModel.showInfoDialog(video.id) }
                        )
                    }
                }
            }
        }
    }

    // Join Room Dialog
    if (state.showJoinDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissJoinDialog() },
            title = { Text("Join Room") },
            text = {
                OutlinedTextField(
                    value = state.joinCode,
                    onValueChange = { viewModel.onJoinCodeChanged(it.uppercase()) },
                    label = { Text("Kode room (4 karakter)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.joinRoom() },
                    enabled = state.joinCode.length == 4,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Gabung")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissJoinDialog() }) {
                    Text("Batal")
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    // Info Dialog
    if (state.showInfoDialog) {
        val video = state.videos.find { it.id == state.selectedVideoId }
        if (video != null) {
            AlertDialog(
                onDismissRequest = { viewModel.dismissInfoDialog() },
                title = { Text(video.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        InfoRow("Judul", video.title)
                        InfoRow("Durasi", formatDuration(video.duration))
                        InfoRow("Format", if (video.hls == 1) "HLS Streaming" else "MP4 Direct")
                        InfoRow("Status", if (video.hls == 1) "✓ Siap ditonton" else "⏳ Sedang diproses")
                        InfoRow("Ukuran", formatSize(video.size))
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.dismissInfoDialog()
                            viewModel.createRoom(video.id)
                        },
                        enabled = video.hls == 1,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text(if (video.hls == 1) "Tonton Bareng" else "Menunggu proses...")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.dismissInfoDialog() }) {
                        Text("Tutup")
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface
            )
        }
    }
}

@Composable
private fun VideoCard(
    video: Video,
    onClick: () -> Unit,
    onInfoClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = video.hls == 1) { onClick() },
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column {
            // Thumbnail
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text("🎬", fontSize = 40.sp)

                // HLS processing badge
                if (video.hls == 0) {
                    Surface(
                        modifier = Modifier.align(Alignment.TopStart).padding(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            "⏳ Processing",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                // Info button
                IconButton(
                    onClick = onInfoClick,
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)
                ) {
                    Icon(
                        Icons.Outlined.Info,
                        contentDescription = "Info",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = video.title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = formatDuration(video.duration),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        Text(value, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
    }
}

private fun formatDuration(seconds: Float): String {
    if (seconds <= 0f) return "Durasi tidak diketahui"
    val h = (seconds / 3600).toInt()
    val m = ((seconds % 3600) / 60).toInt()
    val s = (seconds % 60).toInt()
    return when {
        h > 0 -> "${h}j ${m}m ${s}d"
        m > 0 -> "${m}m ${s}d"
        else -> "${s}d"
    }
}

private fun formatSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1048576 -> "${"%.1f".format(bytes / 1024.0)} KB"
        else -> "${"%.1f".format(bytes / 1048576.0)} MB"
    }
}
