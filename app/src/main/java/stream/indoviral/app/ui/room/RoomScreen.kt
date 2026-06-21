package stream.indoviral.app.ui.room

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import stream.indoviral.app.domain.model.ChatMessage
import stream.indoviral.app.domain.model.RoomUser

@Composable
fun RoomScreen(
    createRoomVideoId: Int?,
    joinRoomCode: String?,
    onExit: () -> Unit,
    viewModel: RoomViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.init(createRoomVideoId, joinRoomCode)
    }

    LaunchedEffect(state.shouldExit) {
        if (state.shouldExit) onExit()
    }

    // Toast
    LaunchedEffect(state.toastMessage) {
        if (state.toastMessage != null) {
            kotlinx.coroutines.delay(3000)
            viewModel.clearToast()
        }
    }

    if (state.isLoading) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Memuat...", color = Color.White)
            }
        }
        return
    }

    val roomState = state.roomState

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar
            TopBar(
                roomCode = roomState?.code,
                users = roomState?.users ?: emptyList(),
                onCopyCode = { /* TODO clipboard */ },
                onLeaveRoom = { viewModel.leaveRoom() }
            )

            // Player
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Color.Black)
            ) {
                if (state.videoUrl != null) {
                    VideoPlayer(
                        url = state.videoUrl!!,
                        isHost = state.isHost,
                        onPlayerReady = { player ->
                            viewModel.playerCurrentTime = { player.currentPosition / 1000.0 }
                            viewModel.playerIsPlaying = { player.isPlaying }
                            viewModel.playerSetCurrentTime = { time ->
                                player.seekTo((time * 1000).toLong())
                            }
                            viewModel.playerPlay = { player.play() }
                            viewModel.playerPause = { player.pause() }
                            viewModel.playerSetPlaybackRate = { rate ->
                                player.setPlaybackParameters(
                                    androidx.media3.common.PlaybackParameters(rate)
                                )
                            }
                            viewModel.playerGetPlaybackRate = { player.playbackParameters.speed }
                            viewModel.playerDuration = { player.duration }

                            // Set initial position if not host
                            if (!state.isHost) {
                                val initialTime = roomState?.currentTime ?: 0.0
                                player.seekTo((initialTime * 1000).toLong())
                                if (roomState?.isPlaying == true) {
                                    player.playWhenReady = true
                                }
                            }
                        },
                        onPlay = { viewModel.emitPlay() },
                        onPause = { viewModel.emitPause() },
                        onSeek = { viewModel.emitSeek() }
                    )
                }
            }

            // User list (mobile)
            if (roomState != null) {
                UserChipRow(
                    users = roomState.users,
                    resolveAvatar = { viewModel.resolveAvatarUrl(it) },
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            // Chat area
            ChatSection(
                modifier = Modifier.weight(1f),
                messages = state.chatMessages,
                currentUserId = state.currentUserId,
                resolveAvatar = { viewModel.resolveAvatarUrl(it) },
                onSend = { viewModel.sendChat(it) }
            )
        }

        // Info overlay
        if (state.showInfoOverlay && roomState != null) {
            InfoOverlay(
                roomCode = roomState.code,
                videoTitle = roomState.videoTitle,
                onDismiss = { viewModel.hideInfoOverlay() }
            )
        }

        // Disconnected banner
        if (!state.isConnected && state.roomState != null) {
            Surface(
                modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
                color = Color(0xFFB91C1C)
            ) {
                Text(
                    "Koneksi terputus — mencoba ulang...",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(8.dp).fillMaxWidth()
                )
            }
        }

        // Toast
        if (state.toastMessage != null) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 80.dp),
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    state.toastMessage!!,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                )
            }
        }
    }
}

@Composable
private fun TopBar(
    roomCode: String?,
    users: List<RoomUser>,
    onCopyCode: () -> Unit,
    onLeaveRoom: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF141414))
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "IndoViral",
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            fontSize = 16.sp
        )
        Spacer(modifier = Modifier.width(8.dp))
        roomCode?.let {
            Text(
                it,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 12.sp,
                letterSpacing = 1.sp
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        TextButton(onClick = onCopyCode) {
            Text("Salin Kode", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
        }
        TextButton(onClick = onLeaveRoom) {
            Text("Keluar", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun UserChipRow(
    users: List<RoomUser>,
    resolveAvatar: (String?) -> String?,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        users.forEach { user ->
            Surface(
                color = Color(0xFF333333),
                shape = RoundedCornerShape(20.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(resolveAvatar(user.avatar))
                            .crossfade(true)
                            .build(),
                        contentDescription = user.username,
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF333333)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${user.username}${if (user.isHost) " 👑" else ""}",
                        fontSize = 11.sp,
                        color = Color(0xFFCCCCCC)
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatSection(
    modifier: Modifier = Modifier,
    messages: List<ChatMessage>,
    currentUserId: Int,
    resolveAvatar: (String?) -> String?,
    onSend: (String) -> Unit
) {
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val context = LocalContext.current

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF1F1F1F))
    ) {
        // Messages
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (messages.isEmpty()) {
                item {
                    Text(
                        "Belum ada pesan",
                        color = Color.Gray,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            items(messages, key = { "${it.userId}_${it.time}" }) { msg ->
                val isSelf = msg.userId == currentUserId
                ChatMessageRow(msg = msg, isSelf = isSelf, resolveAvatar = resolveAvatar, context = context)
            }
        }

        // Input
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1F1F1F))
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = { if (it.length <= 300) inputText = it },
                placeholder = { Text("Ketik pesan...", color = Color.Gray) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF333333),
                    unfocusedContainerColor = Color(0xFF333333),
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color(0xFF404040),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                shape = RoundedCornerShape(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = {
                    if (inputText.isNotBlank()) {
                        onSend(inputText)
                        inputText = ""
                    }
                },
                modifier = Modifier
                    .size(36.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Kirim",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun ChatMessageRow(
    msg: ChatMessage,
    isSelf: Boolean,
    resolveAvatar: (String?) -> String?,
    context: android.content.Context
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isSelf) Arrangement.End else Arrangement.Start
    ) {
        if (!isSelf) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(resolveAvatar(msg.avatar))
                    .crossfade(true)
                    .build(),
                contentDescription = msg.username,
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF333333)),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(
            modifier = Modifier.widthIn(max = 300.dp),
            horizontalAlignment = if (isSelf) Alignment.End else Alignment.Start
        ) {
            Text(
                msg.username,
                fontSize = 11.sp,
                color = Color(0xFF999999),
                modifier = Modifier.padding(bottom = 2.dp)
            )
            Surface(
                color = if (isSelf) MaterialTheme.colorScheme.primary else Color(0xFF333333),
                shape = RoundedCornerShape(
                    topStart = 12.dp,
                    topEnd = 12.dp,
                    bottomStart = if (isSelf) 12.dp else 4.dp,
                    bottomEnd = if (isSelf) 4.dp else 12.dp
                )
            ) {
                Text(
                    msg.message,
                    color = Color.White,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }

        if (isSelf) {
            Spacer(modifier = Modifier.width(8.dp))
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(resolveAvatar(msg.avatar))
                    .crossfade(true)
                    .build(),
                contentDescription = msg.username,
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF333333)),
                contentScale = ContentScale.Crop
            )
        }
    }
}

@Composable
private fun InfoOverlay(
    roomCode: String,
    videoTitle: String,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.9f))
            .clickable { onDismiss() }
    ) {
        Column(
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 80.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                roomCode,
                fontSize = 48.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 8.sp
            )
            Text(
                videoTitle,
                fontSize = 16.sp,
                color = Color(0xFFCCCCCC),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp, start = 16.dp, end = 16.dp)
            )
            Text(
                "Bagikan kode ke teman",
                fontSize = 13.sp,
                color = Color.Gray,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        Button(
            onClick = onDismiss,
            modifier = Modifier
                .align(Alignment.Center)
                .widthIn(min = 200.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Text("Mulai Nonton", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        }
    }
}

@Composable
private fun VideoPlayer(
    url: String,
    isHost: Boolean,
    onPlayerReady: (Player) -> Unit,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onSeek: () -> Unit
) {
    val context = LocalContext.current
    val player = remember {
        androidx.media3.exoplayer.ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_OFF
            playWhenReady = true
        }
    }

    DisposableEffect(Unit) {
        val mediaItem = MediaItem.fromUri(url)
        player.setMediaItem(mediaItem)
        player.prepare()

        val listener = object : Player.Listener {
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (player.playbackState == Player.STATE_READY && isHost) {
                    if (playWhenReady) onPlay()
                    else onPause()
                }
            }
        }
        player.addListener(listener)

        onPlayerReady(player)

        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    AndroidView(
        factory = { ctx: android.content.Context ->
            PlayerView(ctx).apply {
                this@apply.player = player
                useController = isHost
                setShowPreviousButton(false)
                setShowNextButton(false)
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}
