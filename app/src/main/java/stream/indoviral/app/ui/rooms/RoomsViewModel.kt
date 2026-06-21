package stream.indoviral.app.ui.rooms

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
import stream.indoviral.app.data.repository.RoomRepository
import stream.indoviral.app.domain.model.RoomInfo
import javax.inject.Inject

data class RoomsUiState(
    val rooms: List<RoomInfo> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val joinRoomCode: String? = null
)

@HiltViewModel
class RoomsViewModel @Inject constructor(
    private val roomRepository: RoomRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RoomsUiState())
    val uiState: StateFlow<RoomsUiState> = _uiState.asStateFlow()

    private var refreshJob: Job? = null

    init {
        loadRooms()
        startAutoRefresh()
    }

    private fun loadRooms() {
        viewModelScope.launch {
            val result = roomRepository.getActiveRooms()
            result.fold(
                onSuccess = { rooms ->
                    _uiState.update { it.copy(rooms = rooms, isLoading = false, error = null) }
                },
                onFailure = { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
            )
        }
    }

    private fun startAutoRefresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            while (true) {
                delay(5000)
                val result = roomRepository.getActiveRooms()
                result.onSuccess { rooms ->
                    _uiState.update { it.copy(rooms = rooms, error = null) }
                }
            }
        }
    }

    fun refresh() {
        loadRooms()
    }

    fun joinRoom(code: String) {
        _uiState.update { it.copy(joinRoomCode = code) }
    }

    fun clearNavigation() {
        _uiState.update { it.copy(joinRoomCode = null) }
    }

    override fun onCleared() {
        super.onCleared()
        refreshJob?.cancel()
    }
}
