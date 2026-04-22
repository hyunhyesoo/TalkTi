package kr.ac.kopo.talkti.data.remote

import android.util.Log
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

import org.slf4j.Logger
import org.slf4j.LoggerFactory

class NetworkRepository {
    private val apiService = NetworkModule.apiService
    private val logger = LoggerFactory.getLogger(this.javaClass)

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
            logger.info(e.message)
            Result.failure(e)
        }
    }

    /**
     * 이미지를 서버로 전송합니다.
     * 
     * @param image 전송할 이미지 파이트 배열
     * @return 전송 성공 여부
     */
    /**
     * 화면 이미지와 노드 트리를 서버로 함께 전송합니다.
     */
    suspend fun sendScreenAnalysisToServer(image: ByteArray, treeJson: String?): Boolean {
        return try {
            println("1번")
            val requestBody = image.toRequestBody("image/jpeg".toMediaTypeOrNull())
            println("2번")
            val multipartBody = MultipartBody.Part.createFormData(
                "image", "capture.jpg", requestBody
            )

            println("3번")
            
            val treeRequestBody = (treeJson ?: "{}").toRequestBody("application/json".toMediaTypeOrNull())

            println("4번")
            
            val response = apiService.postScreenAnalyze(multipartBody, treeRequestBody)
            Log.v("response: ", response.toString())

            println("5번")
            response.isSuccessful && response.body()?.success == true
        } catch (e: Exception) {
            Log.v("************", e.toString())
            false
        }
    }
}
