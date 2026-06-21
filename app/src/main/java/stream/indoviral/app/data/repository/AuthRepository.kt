package stream.indoviral.app.data.repository

import kotlinx.coroutines.flow.first
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import stream.indoviral.app.data.local.TokenManager
import stream.indoviral.app.data.remote.ApiService
import stream.indoviral.app.domain.model.AuthResponse
import stream.indoviral.app.domain.model.User
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val apiService: ApiService,
    private val tokenManager: TokenManager
) {
    suspend fun getToken(): String? = tokenManager.token.first()

    suspend fun getCurrentUser(): User? = tokenManager.user.first()

    suspend fun register(username: String, password: String, avatarBytes: ByteArray?): Result<AuthResponse> {
        val usernameBody = username.toRequestBody("text/plain".toMediaTypeOrNull())
        val passwordBody = password.toRequestBody("text/plain".toMediaTypeOrNull())
        val avatarPart = avatarBytes?.let {
            MultipartBody.Part.createFormData(
                "avatar", "avatar.jpg",
                it.toRequestBody("image/jpeg".toMediaTypeOrNull())
            )
        }

        return try {
            val response = apiService.register(usernameBody, passwordBody, avatarPart)
            val body = response.body()
            if (response.isSuccessful && body != null) {
                val token = body.token
                val user = body.user
                if (token != null && user != null) {
                    tokenManager.saveAuth(token, user)
                    Result.success(body)
                } else {
                    Result.failure(Exception(body.error ?: "Gagal mendaftar"))
                }
            } else {
                val errorBody = response.errorBody()?.string()
                val msg = parseError(errorBody)
                Result.failure(Exception(msg))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Gagal terhubung ke server"))
        }
    }

    suspend fun login(username: String, password: String): Result<AuthResponse> {
        return try {
            val response = apiService.login(mapOf("username" to username, "password" to password))
            val body = response.body()
            if (response.isSuccessful && body != null) {
                val token = body.token
                val user = body.user
                if (token != null && user != null) {
                    tokenManager.saveAuth(token, user)
                    Result.success(body)
                } else {
                    Result.failure(Exception(body.error ?: "Username atau password salah"))
                }
            } else {
                val errorBody = response.errorBody()?.string()
                val msg = parseError(errorBody)
                Result.failure(Exception(msg))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Gagal terhubung ke server"))
        }
    }

    suspend fun logout() {
        tokenManager.clear()
    }

    suspend fun refreshUser(): Result<User> {
        return try {
            val response = apiService.getMe()
            val user = response.body()
            if (response.isSuccessful && user != null) {
                val token = tokenManager.token.first() ?: return Result.failure(Exception("No token"))
                tokenManager.saveAuth(token, user)
                Result.success(user)
            } else {
                Result.failure(Exception("Gagal memuat user"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Gagal terhubung ke server"))
        }
    }

    private fun parseError(json: String?): String {
        if (json == null) return "Terjadi kesalahan"
        return try {
            val obj = org.json.JSONObject(json)
            obj.optString("error", "Terjadi kesalahan")
        } catch (e: Exception) {
            "Terjadi kesalahan"
        }
    }
}
