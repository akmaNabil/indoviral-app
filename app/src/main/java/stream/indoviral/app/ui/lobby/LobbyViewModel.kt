package stream.indoviral.app.ui.lobby

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import stream.indoviral.app.data.repository.AuthRepository
import stream.indoviral.app.data.repository.VideoRepository
import stream.indoviral.app.di.BaseUrl
import stream.indoviral.app.domain.model.User
import stream.indoviral.app.domain.model.Video
import javax.inject.Inject

data class LobbyUiState(
    val user: User? = null,
    val videos: List<Video> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val selectedVideoId: Int? = null,
    val showJoinDialog: Boolean = false,
    val showInfoDialog: Boolean = false,
    val joinCode: String = "",
    val createRoomVideoId: Int? = null,
    val joinRoomCode: String? = null,
    val isUploading: Boolean = false,
    val showUploadDialog: Boolean = false,
    val uploadTitle: String = "",
    val uploadPickedName: String? = null,
    val uploadBytes: ByteArray? = null,
    val uploadMimeType: String? = null
)

@HiltViewModel
class LobbyViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val videoRepository: VideoRepository,
    @BaseUrl private val baseUrl: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(LobbyUiState())
    val uiState: StateFlow<LobbyUiState> = _uiState.asStateFlow()

    private var pollJob: Job? = null

    init {
        loadUserAndVideos()
    }

    private fun loadUserAndVideos() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val userResult = authRepository.refreshUser()
            userResult.onSuccess { user ->
                _uiState.update { it.copy(user = user) }
            }

            val videosResult = videoRepository.getVideos()
            videosResult.fold(
                onSuccess = { videos ->
                    _uiState.update { it.copy(videos = videos, isLoading = false) }
                    startHlsPolling(videos)
                },
                onFailure = { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
            )
        }
    }

    private fun startHlsPolling(videos: List<Video>) {
        pollJob?.cancel()
        if (videos.none { it.hls == 0 }) return

        pollJob = viewModelScope.launch {
            delay(5000)
            while (true) {
                val result = videoRepository.getVideos()
                result.onSuccess { updated ->
                    _uiState.update { it.copy(videos = updated) }
                    if (updated.none { it.hls == 0 }) {
                        pollJob?.cancel()
                        return@launch
                    }
                }
                delay(5000)
            }
        }
    }

    fun refresh() {
        pollJob?.cancel()
        loadUserAndVideos()
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
            _uiState.update { it.copy(error = "__logout__") }
        }
    }

    fun selectVideo(videoId: Int) {
        _uiState.update { it.copy(selectedVideoId = videoId) }
    }

    fun createRoom(videoId: Int) {
        _uiState.update { it.copy(createRoomVideoId = videoId) }
    }

    fun showJoinDialog() {
        _uiState.update { it.copy(showJoinDialog = true) }
    }

    fun dismissJoinDialog() {
        _uiState.update { it.copy(showJoinDialog = false, joinCode = "") }
    }

    fun showInfoDialog(videoId: Int) {
        _uiState.update { it.copy(showInfoDialog = true, selectedVideoId = videoId) }
    }

    fun dismissInfoDialog() {
        _uiState.update { it.copy(showInfoDialog = false, selectedVideoId = null) }
    }

    fun onJoinCodeChanged(code: String) {
        _uiState.update { it.copy(joinCode = code.take(4)) }
    }

    fun joinRoom() {
        val code = _uiState.value.joinCode
        if (code.length == 4) {
            _uiState.update { it.copy(joinRoomCode = code, showJoinDialog = false) }
        }
    }

    fun clearNavigation() {
        _uiState.update { it.copy(createRoomVideoId = null, joinRoomCode = null) }
    }

    fun resolveAvatarUrl(path: String?): String? {
        if (path.isNullOrBlank()) return null
        return if (path.startsWith("http")) path else baseUrl.trimEnd('/') + "/" + path.trimStart('/')
    }

    fun showUploadDialog() {
        _uiState.update { it.copy(showUploadDialog = true, uploadTitle = "", uploadPickedName = null, uploadBytes = null, uploadMimeType = null) }
    }

    fun dismissUploadDialog() {
        _uiState.update { it.copy(showUploadDialog = false, uploadTitle = "", uploadPickedName = null, uploadBytes = null, uploadMimeType = null) }
    }

    fun onUploadTitleChanged(value: String) {
        _uiState.update { it.copy(uploadTitle = value.take(100)) }
    }

    fun onVideoFilePicked(bytes: ByteArray, mimeType: String, fileName: String) {
        _uiState.update { it.copy(uploadBytes = bytes, uploadMimeType = mimeType, uploadPickedName = fileName) }
    }

    fun uploadVideo() {
        val state = _uiState.value
        val bytes = state.uploadBytes ?: run {
            _uiState.update { it.copy(error = "Pilih file video terlebih dahulu") }
            return
        }
        val mimeType = state.uploadMimeType ?: "video/mp4"
        val fileName = state.uploadPickedName ?: "video.mp4"
        val title = state.uploadTitle.ifBlank { fileName }

        viewModelScope.launch {
            _uiState.update { it.copy(isUploading = true, error = null) }
            val result = videoRepository.uploadVideo(title, bytes, mimeType, fileName)
            result.fold(
                onSuccess = {
                    _uiState.update { it.copy(isUploading = false, showUploadDialog = false, uploadTitle = "", uploadPickedName = null, uploadBytes = null, uploadMimeType = null) }
                    refresh()
                },
                onFailure = { e ->
                    _uiState.update { it.copy(isUploading = false, error = e.message) }
                }
            )
        }
    }
}
