package stream.indoviral.app.data.repository

import stream.indoviral.app.data.remote.ApiService
import stream.indoviral.app.domain.model.Video
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VideoRepository @Inject constructor(
    private val apiService: ApiService
) {
    suspend fun getVideos(): Result<List<Video>> {
        return try {
            val response = apiService.getVideos()
            if (response.isSuccessful) {
                Result.success(response.body() ?: emptyList())
            } else {
                Result.failure(Exception("Gagal memuat video"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Gagal terhubung ke server"))
        }
    }
}
