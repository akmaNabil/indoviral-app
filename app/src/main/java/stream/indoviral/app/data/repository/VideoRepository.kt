package stream.indoviral.app.data.repository

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
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

    suspend fun uploadVideo(title: String, fileBytes: ByteArray, mimeType: String, fileName: String): Result<Video> {
        return try {
            val titleBody = title.toRequestBody("text/plain".toMediaTypeOrNull())
            val videoPart = MultipartBody.Part.createFormData(
                "video", fileName,
                fileBytes.toRequestBody(mimeType.toMediaTypeOrNull())
            )
            val response = apiService.uploadVideo(videoPart, titleBody)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                val msg = response.errorBody()?.string() ?: "Gagal upload video"
                Result.failure(Exception(msg))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Gagal terhubung ke server"))
        }
    }
}
