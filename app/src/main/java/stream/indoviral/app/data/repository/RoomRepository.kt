package stream.indoviral.app.data.repository

import stream.indoviral.app.data.remote.ApiService
import stream.indoviral.app.domain.model.RoomInfo
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomRepository @Inject constructor(
    private val apiService: ApiService
) {
    suspend fun getActiveRooms(): Result<List<RoomInfo>> {
        return try {
            val response = apiService.getRooms()
            if (response.isSuccessful) {
                Result.success(response.body() ?: emptyList())
            } else {
                Result.failure(Exception("Gagal memuat room"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Gagal terhubung ke server"))
        }
    }
}
