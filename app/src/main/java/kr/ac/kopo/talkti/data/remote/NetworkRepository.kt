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
    suspend fun sendTextToServer(text: String): Boolean {
        return try {
            val response = apiService.postText(SttRequest(text))
            response.isSuccessful && response.body()?.success == true
        } catch (e: Exception) {
            e.printStackTrace()
            false
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

    /**
     * 에이전트 초기화 정보를 서버로 전송합니다.
     * 
     * @param hasAccessibility 접근성 권한 여부
     * @param hasOverlay 오버레이 권한 여부
     * @param features 제공 가능한 기능 리스트
     * @return 성공 여부 boolean 반환
     */
    suspend fun sendAgentInitToServer(
        hasAccessibility: Boolean,
        hasOverlay: Boolean,
        features: List<String> = listOf("길찾기", "사진보내기", "택시호출")
    ): Boolean {
        return try {
            val request = AgentInitRequest(
                hasAccessibilityPermission = hasAccessibility,
                hasOverlayPermission = hasOverlay,
                availableFeatures = features
            )
            val response = apiService.postAgentInit(request)
            response.isSuccessful && response.body()?.success == true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
