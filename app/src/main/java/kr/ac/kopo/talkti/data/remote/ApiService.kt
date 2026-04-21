package kr.ac.kopo.talkti.data.remote

import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

data class SttRequest(
    val text: String
)

data class ServerResponse(
    val success: Boolean,
    val message: String
)

interface ApiService {
    @Multipart
    @POST("upload/image")
    suspend fun postImage(
        @Part image: MultipartBody.Part
    ): Response<ServerResponse>

    @POST("upload/text")
    suspend fun postText(
        @Body request: SttRequest
    ): Response<ServerResponse>
}
