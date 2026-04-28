package kr.ac.kopo.talkti.features.voice

import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kr.ac.kopo.talkti.data.model.AgentCommandResponse
import kr.ac.kopo.talkti.data.model.SttRequest
import com.google.gson.Gson
import kr.ac.kopo.talkti.data.remote.NetworkRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 음성 인식(STT) 결과를 담고 있는 상태 클래스입니다.
 */
data class VoiceToTextParserState(
    val spokenText: String = "",
    val isSpeaking: Boolean = false,
    val error: String? = null
)

/**
 * 안드로이드 내장 SpeechRecognizer를 설정하는 헬퍼 클래스입니다.
 * 사용자의 음성을 텍스트로 변환하여 StateFlow를 통해 전달합니다.
 */
class VoiceToTextParser(
    private val application: Application
) : RecognitionListener {

    private val _state = MutableStateFlow(VoiceToTextParserState())
    val state: StateFlow<VoiceToTextParserState> = _state.asStateFlow()
    private val networkRepository = NetworkRepository()
    private var recognizer: SpeechRecognizer? = null
    private val ttsManager = TtsManager(application)

    // 연속 듣기를 위한 상태 및 타이머
    private var isContinuousListening = false
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var silenceTimerJob: Job? = null
    private var fullText = ""
    private var currentLanguageCode = "ko-KR"

    /**
     * 사용자의 음성 인식을 시작합니다.
     * 기본 언어는 한국어("ko-KR")로 설정되어 있습니다.
     */
    fun startListening(languageCode: String = "ko-KR") {
        currentLanguageCode = languageCode
        isContinuousListening = true
        fullText = ""
        _state.update { VoiceToTextParserState() }

        resetSilenceTimer()
        startSpeechRecognizerInternal()
    }

    /**
     * 내부적으로 SpeechRecognizer를 시작 (연속 듣기를 위해 분리)
     */
    private fun startSpeechRecognizerInternal() {
        if (!SpeechRecognizer.isRecognitionAvailable(application)) {
            _state.update { it.copy(error = "기기에서 음성 인식을 지원하지 않습니다.") }
            return
        }

        // 기존 인스턴스 초기화
        recognizer?.destroy()

        recognizer = SpeechRecognizer.createSpeechRecognizer(application).apply {
            setRecognitionListener(this@VoiceToTextParser)

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, currentLanguageCode)
                // 부분 인식 결과 활성화 (실시간 타이핑 효과)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }
            startListening(intent)
        }
    }

    /**
     * 10초 대기 타이머를 초기화합니다.
     */
    private fun resetSilenceTimer() {
        silenceTimerJob?.cancel()
        silenceTimerJob = scope.launch {
            delay(10000L) // 10초 대기
            // 10초 대기 완료 시(타이머가 취소되지 않음) 종료
            stopListening()
        }
    }

    /**
     * 음성 인식을 수동으로 중지합니다.
     */
    fun stopListening() {
        if (!isContinuousListening) return
        isContinuousListening = false
        silenceTimerJob?.cancel()

        _state.update { it.copy(isSpeaking = false) }
        recognizer?.stopListening()
        recognizer?.destroy()
        recognizer = null
    }

    fun destroy() {
        stopListening()         // 1. 음성 인식 중단
        ttsManager.release()     // 2. TTS 엔진 종료 (메모리 해제)
        silenceTimerJob?.cancel() // 3. 10초 타이머 취소
    }

    override fun onReadyForSpeech(params: Bundle?) {
        _state.update { it.copy(isSpeaking = true, error = null) }
    }

    override fun onBeginningOfSpeech() {
        _state.update { it.copy(isSpeaking = true) }
        resetSilenceTimer()
    }

    override fun onRmsChanged(rmsdB: Float) = Unit
    override fun onBufferReceived(buffer: ByteArray?) = Unit
    override fun onEndOfSpeech() = Unit

    override fun onError(error: Int) {
        // 시간에 의한 타임아웃이거나 말을 안했을 경우 => 연속 듣기 상태라면 다시 시작
        if (error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT ||
            error == SpeechRecognizer.ERROR_NO_MATCH ||
            error == SpeechRecognizer.ERROR_CLIENT
        ) {

            if (isContinuousListening) {
                startSpeechRecognizerInternal()
                return
            }
        }

        val errorMessage = when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "오디오 에러가 발생했습니다."
            SpeechRecognizer.ERROR_CLIENT -> "클라이언트 에러가 발생했습니다."
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "오디오 녹음 권한이 없습니다."
            SpeechRecognizer.ERROR_NETWORK -> "네트워크 에러가 발생했습니다."
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "네트워크 타임아웃 오류."
            SpeechRecognizer.ERROR_NO_MATCH -> "일치하는 음성을 찾을 수 없습니다."
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "음성인식 서비스가 바쁩니다."
            SpeechRecognizer.ERROR_SERVER -> "서버 에러가 발생했습니다."
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "음성 입력이 없습니다."
            else -> "알 수 없는 오류가 발생했습니다."
        }

        _state.update { it.copy(error = errorMessage, isSpeaking = false) }
        isContinuousListening = false
        silenceTimerJob?.cancel()
        recognizer?.destroy()
        recognizer = null
    }

    override fun onResults(results: Bundle?) {
        resetSilenceTimer()
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        if (!matches.isNullOrEmpty()) {
            val resultText = matches[0]
            fullText = if (fullText.isEmpty()) resultText else "$fullText $resultText"
            _state.update { it.copy(spokenText = fullText) }

            // 서버로 전송할 STT Request 객체 생성 및 임시 로그 전송
            val sttRequest = SttRequest(
                command = resultText,
                timestamp = getCurrentTimestamp(),
                language = currentLanguageCode
            )
            tempSendData(sttRequest)
            
            // 서버 전송 후 듣기 즉시 종료
            isContinuousListening = false
            silenceTimerJob?.cancel()
        }

        if (isContinuousListening) {
            // 결과를 받은 후 연속적으로 계속 듣기
            startSpeechRecognizerInternal()
        } else {
            _state.update { it.copy(isSpeaking = false) }
            recognizer?.destroy()
            recognizer = null
        }
    }

    override fun onPartialResults(partialResults: Bundle?) {
        resetSilenceTimer()
        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        if (!matches.isNullOrEmpty()) {
            val partialText = matches[0]
            val displayText = if (fullText.isEmpty()) partialText else "$fullText $partialText"
            _state.update { it.copy(spokenText = displayText) }
        }
    }

    override fun onEvent(eventType: Int, params: Bundle?) = Unit

    private fun getCurrentTimestamp(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return sdf.format(Date())
    }

    private fun tempSendData(request: SttRequest) {
        Log.d("VoiceToTextParser", "서버 전송 시도 중: ${request.command}")

        scope.launch(Dispatchers.IO) {
            val result = networkRepository.sendTextToServer(request.command)

            result.onSuccess { serverMessage ->
                // serverMessage가 "{"ttsMessage": "안녕"}" 같은 JSON으로 올 경우를 대비
                Log.d("STT_SERVER", "성공! 서버 답변 원본: $serverMessage")

                launch(Dispatchers.Main) {
                    try {
                        val response = Gson().fromJson(serverMessage, AgentCommandResponse::class.java)
                        ttsManager.speak(response.tts_message)
                        _state.update { it.copy(spokenText = response.target_text) }
                    } catch (e: Exception) {
                        Log.e("STT_SERVER", "JSON 파싱 에러", e)
                        ttsManager.speak("서버 응답을 처리하는데 문제가 발생했습니다.")
                    }
                }
            }.onFailure { error ->
                Log.e("STT_SERVER", "네트워크 통신 실패", error)
                launch(Dispatchers.Main) {
                    ttsManager.speak("서버와 연결이 원활하지 않습니다. 다시 시도해 주세요.")
                }
            }
        }
    }
}

