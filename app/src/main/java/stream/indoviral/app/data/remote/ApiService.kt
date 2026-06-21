package stream.indoviral.app.data.remote

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*
import stream.indoviral.app.domain.model.*

interface ApiService {

    @POST("api/register")
    @Multipart
    suspend fun register(
        @Part("username") username: RequestBody,
        @Part("password") password: RequestBody,
        @Part avatar: MultipartBody.Part?
    ): Response<AuthResponse>

    @POST("api/login")
    suspend fun login(
        @Body body: Map<String, String>
    ): Response<AuthResponse>

    @GET("api/me")
    suspend fun getMe(): Response<User>

    @POST("api/users/avatar")
    @Multipart
    suspend fun updateAvatar(
        @Part avatar: MultipartBody.Part
    ): Response<Map<String, String>>

    @GET("api/videos")
    suspend fun getVideos(): Response<List<Video>>

    @GET("api/rooms")
    suspend fun getRooms(): Response<List<RoomInfo>>
}
