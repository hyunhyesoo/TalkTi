package kr.ac.kopo.talkti.data.remote

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

class NetworkRepository {
    private val apiService = NetworkModule.apiService

    /**
     * STT로 인식된 텍스트를 서버로 전송할 때 사용하는 함수입니다.
     * 
     * [사용 예시]
     * CoroutineScope(Dispatchers.IO).launch {
     *     val repository = NetworkRepository()
     *     val isSuccess = repository.sendTextToServer("이거 전송해줘")
     *     if (isSuccess) {
     *         Log.d("STT", "전송 완료!")
     *     } else {
     *         Log.e("STT", "전송 실패 ㅠㅠ")
     *     }
     * }
     */
    suspend fun sendTextToServer(text: String): Result<String> {
        return try {
            val response = apiService.postText(SttRequest(text))
            val body = response.body()

            if (response.isSuccessful && body != null) {
                // 서버가 보내준 메시지(예: 대답 텍스트)를 성공 결과로 반환
                Result.success(body.message ?: "전송 성공")
            } else {
                Result.failure(Exception(body?.message ?: "서버 응답 처리 실패"))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * 이미지를 서버로 전송합니다.
     * 
     * @param image 전송할 이미지 파이트 배열
     * @return 전송 성공 여부
     */
    suspend fun sendImageToServer(image: ByteArray): Boolean {
        return try {
            val requestBody = image.toRequestBody("image/jpeg".toMediaTypeOrNull())
            val multipartBody = MultipartBody.Part.createFormData(
                "image", "capture.jpg", requestBody
            )
            
            val response = apiService.postImage(multipartBody)
            response.isSuccessful && response.body()?.success == true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
